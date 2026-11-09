"""측정 도구 테스트. 실제 호출을 하지 않는다: python -m unittest discover -s evals/parser_remeasure -v"""
from __future__ import annotations

import argparse
import contextlib
import csv
import io
import json
import os
import re
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import remeasure as rm  # noqa: E402

JAVA_PARSER = rm.ROOT / "src/main/java/com/opinio/passform/llm/OpenAiQueryParser.java"
ANALYSIS = rm.ROOT / "src/main/java/com/opinio/passform/service/analysis/AnalysisService.java"
SPIKE = rm.ROOT / "docs/spikes/2026-11-04_test_demo.md"


def question(identifier="q01", text="경영학과 학생 몇 명", **expected):
    base = {"conditions": ["경영학과"], "operation": "count"}
    base.update(expected)
    return {"id": identifier, "form": "f", "pattern": "p", "question": text, "expected": base}


def result(prompt, number, identifier, context, attempts=1):
    return {"prompt": prompt, "round": number, "question_id": identifier, "context": context, "attempt_count": attempts}


class Markers(unittest.TestCase):
    def test_마커만_있는_줄_사이를_읽어_설명문의_마커에서_잘리지_않는다(self):
        prompt = rm.prompt_between_markers(rm.read_text(rm.V2_PATH))
        self.assertTrue(prompt.startswith(rm.FIRST_SENTENCE))
        self.assertIn("예시 4\n", prompt)
        self.assertNotIn("prompt_version", prompt)
        self.assertTrue(prompt.endswith('"topic":"비용이 부담된다"}'))

    def test_마커가_없으면_중단한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.prompt_between_markers("본문만 있다")


class PromptValidity(unittest.TestCase):
    def setUp(self):
        self.prompts = rm.load_prompts()

    def test_저장소의_v2와_v3는_통과한다(self):
        rm.check_prompts(self.prompts)

    def test_v3_자리에_v2를_넣으면_중단한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.check_prompts({"v2": self.prompts["v2"], "v3": self.prompts["v2"]})

    def test_시작이_첫_문장이_아니면_중단한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.check_prompts({"v2": "`와 `", "v3": self.prompts["v3"]})

    def test_v2에_예시_5가_있으면_중단한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.check_prompts({"v2": self.prompts["v3"], "v3": self.prompts["v3"]})

    def test_v3는_v2_뒤에_시험_데모_예시를_그대로_붙인_것이다(self):
        spike = rm.read_text(SPIKE)
        section = spike[spike.index("### 3-5."):spike.index("### 3-6.")]
        examples = re.search(r"```\n(.*?)\n```", section, re.S).group(1)
        self.assertEqual(self.prompts["v2"] + "\n\n" + examples, self.prompts["v3"])


class RequestBody(unittest.TestCase):
    def setUp(self):
        self.schema = rm.load_schema()
        self.body = rm.request_body("지시", self.schema, "질문", None)

    def test_OpenAiQueryParser와_같은_필드다(self):
        self.assertEqual(["model", "instructions", "input", "reasoning", "temperature", "text", "store"], list(self.body))
        self.assertEqual("gpt-6-luna", self.body["model"])
        self.assertEqual("지시", self.body["instructions"])
        self.assertEqual("질문", self.body["input"])
        self.assertEqual({"effort": "none"}, self.body["reasoning"])
        self.assertEqual(0, self.body["temperature"])
        self.assertIs(False, self.body["store"])
        form = self.body["text"]["format"]
        self.assertEqual("json_schema", form["type"])
        self.assertEqual("request_context", form["name"])
        self.assertIs(False, form["strict"])
        self.assertIn("conditions", form["schema"]["properties"])

    def test_스키마의_달러_schema_키를_뺀다(self):
        self.assertIn("$schema", self.schema)
        self.assertNotIn("$schema", self.body["text"]["format"]["schema"])

    def test_재호출_입력은_제품과_같은_문장이다(self):
        body = rm.request_body("지시", self.schema, "질문", "오류 설명")
        self.assertEqual("질문\n\n이전 출력이 스키마에 맞지 않았다. 오류: 오류 설명\n스키마에 맞는 JSON 객체 하나만 다시 출력한다.", body["input"])

    def test_제품_코드가_측정_도구와_같은_값을_쓴다(self):
        source = rm.read_text(JAVA_PARSER)
        for expected in ('MODEL = "gpt-6-luna"', 'body.put("temperature", 0)', 'Map.of("effort", "none")', '"name", "request_context"', '"strict", false', 'body.put("store", false)', "이전 출력이 스키마에 맞지 않았다. 오류: ", "스키마에 맞는 JSON 객체 하나만 다시 출력한다."):
            self.assertIn(expected, source)
        self.assertIn("attempt < 2", rm.read_text(ANALYSIS))

    def test_출력_문자열은_message의_output_text만_잇는다(self):
        response = {"output": [{"type": "reasoning"}, {"type": "message", "content": [{"type": "output_text", "text": "가"}, {"type": "other"}, {"type": "output_text", "text": "나"}]}]}
        self.assertEqual("가나", rm.output_text(response))


class Validator(unittest.TestCase):
    def setUp(self):
        self.schema = rm.load_schema()

    def test_맞는_값은_오류가_없다(self):
        self.assertEqual([], rm.schema_errors({"conditions": ["가"], "match": "not", "operation": None, "sort": {"by": "나", "order": "asc"}}, self.schema))

    def test_틀린_값을_잡는다(self):
        for value in ({"conditions": []}, {"operation": "count"}, {"conditions": [], "operation": "average"}, {"conditions": [], "operation": "count", "group_by": "학년"}, {"conditions": [1], "operation": "count"}, {"conditions": [], "operation": "count", "match": "and"}, {"conditions": [], "operation": "count", "sort": {"by": "a"}}, []):
            self.assertTrue(rm.schema_errors(value, self.schema), value)


class Retry(unittest.TestCase):
    def setUp(self):
        self.schema = rm.load_schema()

    def run_with(self, *outputs):
        queue = list(outputs)
        self.bodies = []

        def call(body):
            self.bodies.append(body)
            return queue.pop(0)

        return rm.parse_with_retry(call, "지시", self.schema, "질문")

    def test_형식_오류면_한_번만_다시_부른다(self):
        outcome = self.run_with('{"conditions":[],"operation":"count","group_by":"학년"}', '{"conditions":[],"operation":"count"}')
        self.assertEqual("ok", outcome["status"])
        self.assertEqual(2, len(self.bodies))
        self.assertIn("이전 출력이 스키마에 맞지 않았다", self.bodies[1]["input"])

    def test_두_번_실패하면_parse_failed다(self):
        outcome = self.run_with("깨진 값", "")
        self.assertEqual("parse_failed", outcome["status"])
        self.assertIsNone(outcome["context"])
        self.assertEqual(2, len(self.bodies))

    def test_operation_null은_다시_부르지_않는다(self):
        outcome = self.run_with('{"conditions":[],"operation":null}')
        self.assertEqual("ok", outcome["status"])
        self.assertEqual(1, len(self.bodies))


class Judge(unittest.TestCase):
    def verdict(self, expected, actual):
        return rm.judge(expected, actual)

    def test_같으면_일치다(self):
        self.assertTrue(self.verdict({"conditions": ["가"], "operation": "count"}, {"conditions": [" 가 "], "operation": "count"})["matched"])

    def test_조건이_다르면_불일치다(self):
        self.assertFalse(self.verdict({"conditions": ["가"], "operation": "count"}, {"conditions": ["나"], "operation": "count"})["matched"])

    def test_조건을_버리면_불일치다(self):
        self.assertFalse(self.verdict({"conditions": ["가"], "operation": "count"}, {"conditions": [], "operation": "count"})["matched"])

    def test_조건_순서는_무시한다(self):
        self.assertTrue(self.verdict({"conditions": ["가", "나"], "match": "all", "operation": "list"}, {"conditions": ["나", "가"], "match": "all", "operation": "list"})["matched"])

    def test_표현이_다르면_불일치다(self):
        self.assertFalse(self.verdict({"conditions": ["경영학과"], "operation": "count"}, {"conditions": ["경영학과 학생"], "operation": "count"})["matched"])

    def test_조건_하나에_붙은_match_all은_일치로_보고_따로_센다(self):
        verdict = self.verdict({"conditions": ["가"], "operation": "count"}, {"conditions": ["가"], "match": "all", "operation": "count"})
        self.assertEqual({"matched": True, "match_added": True}, verdict)

    def test_조건_둘에_match가_없으면_불일치다(self):
        self.assertFalse(self.verdict({"conditions": ["가", "나"], "match": "all", "operation": "count"}, {"conditions": ["가", "나"], "operation": "count"})["matched"])

    def test_조건_둘의_기대에_없는_match는_불일치다(self):
        self.assertFalse(self.verdict({"conditions": ["가", "나"], "operation": "count"}, {"conditions": ["가", "나"], "match": "all", "operation": "count"})["matched"])

    def test_not은_정확히_비교한다(self):
        self.assertFalse(self.verdict({"conditions": ["가"], "operation": "count"}, {"conditions": ["가"], "match": "not", "operation": "count"})["matched"])
        self.assertFalse(self.verdict({"conditions": ["가"], "match": "not", "operation": "count"}, {"conditions": ["가"], "operation": "count"})["matched"])

    def test_operation_sort_target_topic을_비교한다(self):
        base = {"conditions": [], "operation": "summarize", "target": "가"}
        self.assertTrue(self.verdict(base, dict(base))["matched"])
        self.assertFalse(self.verdict(base, {"conditions": [], "operation": "list", "target": "가"})["matched"])
        self.assertFalse(self.verdict(base, {"conditions": [], "operation": "summarize"})["matched"])
        self.assertFalse(self.verdict(base, {"conditions": [], "operation": "summarize", "target": "가", "topic": "가"})["matched"])
        sort = {"conditions": [], "operation": "list", "sort": {"by": "가", "order": "asc"}}
        self.assertFalse(self.verdict(sort, {"conditions": [], "operation": "list", "sort": {"by": "가", "order": "desc"}})["matched"])

    def test_파싱_실패는_불일치다(self):
        self.assertFalse(self.verdict({"conditions": [], "operation": None}, None)["matched"])


class Tags(unittest.TestCase):
    def test_조건을_모두_버리면_전체_건수_위험이다(self):
        self.assertIn("drop_all", rm.failure_tags(question(), {"conditions": [], "operation": "count"}))

    def test_일부만_버린_것을_따로_센다(self):
        item = question(conditions=["경영학과", "3학년"], text="경영학과 3학년 몇 명")
        tags = rm.failure_tags(item, {"conditions": ["경영학과"], "operation": "count"})
        self.assertIn("drop_some", tags)
        self.assertNotIn("drop_all", tags)

    def test_질문에_없는_조건은_지어낸_것이다(self):
        self.assertIn("invented", rm.failure_tags(question(), {"conditions": ["경영학부"], "operation": "count"}))

    def test_미응답_형식과_질문의_낱말은_지어낸_것이_아니다(self):
        item = question(conditions=["식사 신청 - 미응답"], text="식사 신청에 응답 안 한 사람")
        self.assertNotIn("invented", rm.failure_tags(item, {"conditions": ["식사 신청 - 미응답"], "operation": "count"}))

    def test_operation이_다르면_따로_센다(self):
        self.assertIn("operation_mismatch", rm.failure_tags(question(), {"conditions": ["경영학과"], "operation": None}))

    def test_명사_붙임과_덩어리를_구분한다(self):
        self.assertIn("noun_attached", rm.failure_tags(question(), {"conditions": ["경영학과 학생"], "operation": "count"}))
        item = question(conditions=["경영학과", "3학년"], text="경영학과 3학년 몇 명")
        self.assertIn("bundled", rm.failure_tags(item, {"conditions": ["경영학과 3학년"], "operation": "count"}))

    def test_파싱_실패를_센다(self):
        self.assertEqual(["parse_failed"], rm.failure_tags(question(), None))


class Aggregate(unittest.TestCase):
    def setUp(self):
        self.questions = [question("q1"), question("q2", conditions=[], operation="count", text="몇 명")]

    def rows(self, prompt, q1_context, q2_context):
        rows = []
        for number in range(1, 6):
            rows.append(result(prompt, number, "q1", q1_context))
            rows.append(result(prompt, number, "q2", q2_context))
        return rows

    def test_일치율과_질문별_횟수와_통과를_낸다(self):
        good = {"conditions": ["경영학과"], "operation": "count"}
        empty = {"conditions": [], "operation": "count"}
        summary = rm.aggregate(self.rows("v2", empty, empty) + self.rows("v3", good, empty), self.questions)
        self.assertEqual(5, summary["v2"]["matched"])
        self.assertEqual(10, summary["v3"]["matched"])
        self.assertEqual(5, summary["v3"]["per_question"]["q1"])
        self.assertEqual(5, summary["v2"]["tags"]["drop_all"])
        self.assertEqual(["q1"], summary["v3_only"])
        self.assertEqual([], summary["v2_only"])
        self.assertTrue(summary["passed"]["all"])

    def test_조건을_버리면_합격하지_못한다(self):
        empty = {"conditions": [], "operation": "count"}
        rows = self.rows("v2", empty, empty) + self.rows("v3", empty, empty)
        summary = rm.aggregate(rows, self.questions)
        self.assertFalse(summary["passed"]["no_drop"])
        self.assertFalse(summary["passed"]["all"])

    def test_재호출로_성공한_것도_일치로_센다(self):
        good = {"conditions": ["경영학과"], "operation": "count"}
        rows = [result("v2", 1, "q1", good, attempts=2), result("v3", 1, "q1", good)]
        summary = rm.aggregate(rows, self.questions)
        self.assertEqual(1, summary["v2"]["matched"])
        self.assertEqual(1, summary["v2"]["retried_ok"])

    def test_회차별_최솟값과_최댓값을_낸다(self):
        good = {"conditions": ["경영학과"], "operation": "count"}
        bad = {"conditions": ["다른 값"], "operation": "count"}
        rows = [result("v2", 1, "q1", good), result("v2", 2, "q1", bad), result("v3", 1, "q1", good)]
        summary = rm.aggregate(rows, self.questions)
        self.assertEqual(0.0, summary["v2"]["min_round"])
        self.assertEqual(1.0, summary["v2"]["max_round"])

    def test_operation_흔들림을_질문별로_낸다(self):
        count = {"conditions": ["경영학과"], "operation": "count"}
        none = {"conditions": ["경영학과"], "operation": None}
        rows = [result("v2", 1, "q1", count), result("v2", 2, "q1", none), result("v3", 1, "q1", count)]
        self.assertEqual(["q1"], rm.aggregate(rows, self.questions)["v2"]["operation_unstable"])

    def test_v2만_맞은_질문을_낸다(self):
        good = {"conditions": ["경영학과"], "operation": "count"}
        bad = {"conditions": ["다른 값"], "operation": "count"}
        empty = {"conditions": [], "operation": "count"}
        summary = rm.aggregate(self.rows("v2", good, empty) + self.rows("v3", bad, empty), self.questions)
        self.assertEqual(["q1"], summary["v2_only"])


class Questions(unittest.TestCase):
    def write(self, rows):
        directory = tempfile.mkdtemp()
        path = Path(directory) / "q.csv"
        with path.open("w", encoding="utf-8-sig", newline="") as sink:
            writer = csv.DictWriter(sink, fieldnames=["id", "form", "pattern", "question", "conditions", "match", "operation", "sort_by", "sort_order", "target", "topic", "memo"])
            writer.writeheader()
            writer.writerows(rows)
        return path

    def rows(self, **override):
        rows = []
        for index in range(rm.QUESTION_COUNT):
            row = {"id": f"q{index}", "form": "f", "pattern": "p", "question": f"새 질문 {index}", "conditions": "[]", "match": "", "operation": "count", "sort_by": "", "sort_order": "", "target": "", "topic": "", "memo": ""}
            rows.append(row)
        rows[0].update(override)
        return rows

    def test_채운_값을_읽는다(self):
        path = self.write(self.rows(conditions="가 | 나", match="all", operation="list", sort_by="다", sort_order="asc"))
        expected = rm.load_questions(path)[0]["expected"]
        self.assertEqual({"conditions": ["가", "나"], "operation": "list", "match": "all", "sort": {"by": "다", "order": "asc"}}, expected)

    def test_조건_없음과_null을_읽는다(self):
        path = self.write(self.rows(operation="null", target="가"))
        self.assertEqual({"conditions": [], "operation": None, "target": "가"}, rm.load_questions(path)[0]["expected"])

    def test_빈_기대값이면_거부한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.load_questions(self.write(self.rows(conditions="", operation="")))

    def test_잘못된_값이면_거부한다(self):
        for override in ({"operation": "average"}, {"match": "and"}, {"sort_by": "가"}, {"sort_order": "asc"}, {"sort_by": "가", "sort_order": "up"}):
            with self.assertRaises(rm.ValidityError):
                rm.load_questions(self.write(self.rows(**override)))

    def test_문서에_나온_질문이면_거부한다(self):
        with self.assertRaises(rm.ValidityError):
            rm.load_questions(self.write(self.rows(question="수의학과 학생 수 알려줘")))

    def test_저장소의_질문은_겹치지_않고_30개다(self):
        self.assertEqual(rm.QUESTION_COUNT, len(rm.load_questions(require_expected=False)))


class Calls(unittest.TestCase):
    def test_예상_호출_수(self):
        self.assertEqual((300, 600), rm.planned_calls())

    def test_승인된_호출_수를_넘기지_않는다(self):
        budget = rm.Budget(1)
        budget.spend()
        with self.assertRaises(SystemExit):
            budget.spend()

    def test_호출_시작_간격을_지킨다(self):
        budget = rm.Budget(5)
        with mock.patch.object(rm.time, "monotonic", return_value=100.0), mock.patch.object(rm.time, "sleep") as sleep:
            budget.wait_turn()
            sleep.assert_not_called()
            budget.wait_turn()
        sleep.assert_called_once_with(rm.MIN_CALL_INTERVAL)


class ExtraSets(unittest.TestCase):
    def test_E8_질문은_30문항과_별개로_3개다(self):
        extra = rm.load_questions(rm.E8_QUESTIONS_PATH, count=3)
        main = rm.load_questions()
        self.assertEqual(["e01", "e02", "e03"], [item["id"] for item in extra])
        self.assertFalse({item["question"] for item in extra} & {item["question"] for item in main})
        self.assertEqual((30, 60), rm.planned_calls(3))

    def test_고정한_30문항의_해시가_그대로다(self):
        self.assertEqual("6f5d295cdd464aac7edc9ca77b916d6605789668f8b6c8374aa3997b269d812f", rm.file_sha256(rm.QUESTIONS_PATH))

    def test_모델을_바꾸면_요청의_모델만_달라진다(self):
        schema = rm.load_schema()
        base = rm.request_body("지시", schema, "질문", None)
        other = rm.request_body("지시", schema, "질문", None, "gpt-6.1-terra")
        self.assertEqual("gpt-6-luna", base["model"])
        self.assertEqual("gpt-6.1-terra", other["model"])
        self.assertEqual({**base, "model": "x"}, {**other, "model": "x"})

    def test_같은_질문의_출력이_갈리면_불안정으로_낸다(self):
        items = [question("q01", conditions=[], operation=None)]
        rows = [result(prompt, number, "q01", {"conditions": [], "operation": None if number == 1 else "count"}) for prompt in rm.PROMPT_IDS for number in (1, 2, 3)]
        summary = rm.aggregate(rows, items)
        self.assertEqual({"q01": 2}, summary["v2"]["distinct_outputs"])
        self.assertEqual(["q01"], summary["v2"]["output_unstable"])

    def test_조건_순서만_다른_출력은_같은_출력이다(self):
        first = {"conditions": ["가", "나"], "operation": "count"}
        second = {"conditions": ["나", "가"], "operation": "count"}
        self.assertEqual(rm.canonical_output(first), rm.canonical_output(second))

    def run_folder(self, folder, model, question_set):
        path = Path(folder)
        (path / "manifest.json").write_text(json.dumps({"model_requested": model, "question_set": question_set}), encoding="utf-8")
        (path / "calls.jsonl").write_text("", encoding="utf-8")
        return path

    def test_합격선은_luna의_30문항에만_낸다(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertIn("passed", rm.summarize_run(self.run_folder(folder, rm.MODEL, "main")))
            self.assertNotIn("passed", rm.summarize_run(self.run_folder(folder, "gpt-6.1-terra", "main")))
            self.assertNotIn("passed", rm.summarize_run(self.run_folder(folder, rm.MODEL, "e8")))


def http_error(status, code="", headers=None):
    body = json.dumps({"error": {"code": code, "message": "비밀 내용"}}).encode("utf-8")
    return urllib.error.HTTPError(rm.API_URL, status, "error", headers or {}, io.BytesIO(body))


def waits(sleep):
    return [entry.args[0] for entry in sleep.call_args_list if entry.args[0] >= rm.RATE_LIMIT_WAIT]


class RateLimit(unittest.TestCase):
    def call(self, side_effect, limit=10):
        budget = rm.Budget(limit)
        with mock.patch.object(rm.urllib.request, "urlopen", side_effect=side_effect), mock.patch.object(rm, "output_text", return_value="{}"), \
                mock.patch.object(rm.time, "sleep") as sleep, contextlib.redirect_stdout(io.StringIO()):
            try:
                return budget, sleep, rm.http_call("test", {}, budget, {}), None
            except rm.ApiFailure as error:
                return budget, sleep, None, error

    def ok(self):
        response = mock.MagicMock()
        response.__enter__.return_value.read.return_value = b"{}"
        return response

    def test_속도_제한은_안내받은_시간만큼_기다려_다시_호출하고_모두_센다(self):
        budget, sleep, output, failure = self.call([http_error(429, "rate_limit_exceeded", {"Retry-After": "45"}), self.ok()])
        self.assertIsNone(failure)
        self.assertEqual("{}", output)
        self.assertEqual(2, budget.used)
        self.assertEqual([45.0], waits(sleep))

    def test_오래_기다리라는_안내를_받으면_기다리지_않고_멈춘다(self):
        headers = {"Retry-After": "3600", "x-ratelimit-limit-tokens": "50000"}
        budget, sleep, _, failure = self.call([http_error(429, "rate_limit_exceeded", headers)])
        self.assertEqual(1, budget.used)
        self.assertEqual([], waits(sleep))
        self.assertIn("3600초", str(failure))
        self.assertIn("50000", str(failure))

    def test_안내가_없으면_기본_대기를_쓴다(self):
        _, sleep, _, _ = self.call([http_error(429, "rate_limit_exceeded"), self.ok()])
        self.assertEqual([rm.RATE_LIMIT_WAIT], waits(sleep))

    def test_잔액_부족은_다시_호출하지_않는다(self):
        budget, _, _, failure = self.call([http_error(429, "insufficient_quota")])
        self.assertEqual(1, budget.used)
        self.assertEqual("HTTP 429 insufficient_quota", str(failure))

    def test_계속_제한되면_횟수를_지키고_마지막_뒤에는_기다리지_않는다(self):
        budget, sleep, _, failure = self.call([http_error(429, "rate_limit_exceeded") for _ in range(rm.API_RETRIES)])
        self.assertEqual(rm.API_RETRIES, budget.used)
        self.assertEqual(rm.API_RETRIES - 1, len(waits(sleep)))
        self.assertIn("rate_limit_exceeded", str(failure))

    def test_오류_메시지는_남기지_않는다(self):
        _, _, _, failure = self.call([http_error(401, "invalid_api_key")])
        self.assertNotIn("비밀", str(failure))


class Resume(unittest.TestCase):
    def arguments(self, sha, max_calls, resume=None, model=rm.MODEL, question_set="main"):
        return argparse.Namespace(input_sha=sha, max_calls=max_calls, spent_before=0, resume=resume, model=model, question_set=question_set)

    def e8_sha(self):
        return rm.file_sha256(rm.E8_QUESTIONS_PATH)

    def test_고른_모델이_요청에_실리고_이어서_실행할_때_모델이_다르면_중단한다(self):
        seen = []

        def failing(_key, body, budget, _meta):
            seen.append(body["model"])
            budget.spend()
            raise rm.ApiFailure("HTTP 400 unsupported_parameter")

        with tempfile.TemporaryDirectory() as folder, mock.patch.object(rm, "RUNS_DIR", Path(folder)), mock.patch.dict(os.environ, {"OPENAI_API_KEY": "test"}), \
                mock.patch.object(rm, "http_call", side_effect=failing), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(3, rm.run(self.arguments(self.e8_sha(), 10, model="gpt-6.1-terra", question_set="e8")))
            self.assertEqual(["gpt-6.1-terra"], seen)
            run_dir = next(Path(folder).iterdir())
            with self.assertRaises(rm.ValidityError):
                rm.run(self.arguments(self.e8_sha(), 10, resume=str(run_dir), model=rm.MODEL, question_set="e8"))

    def test_30문항_세트의_입력_SHA로_E8_세트를_돌릴_수_없다(self):
        with mock.patch.dict(os.environ, {"OPENAI_API_KEY": "test"}), self.assertRaises(rm.ValidityError):
            rm.run(self.arguments(rm.file_sha256(rm.QUESTIONS_PATH), 10, question_set="e8"))

    def test_API_실패는_종료코드_3과_호출_수를_남기고_이어서_실행해도_상한을_지킨다(self):
        sha = rm.input_facts(rm.load_prompts())["questions_sha256"]

        def failing(_key, _body, budget, _meta):
            budget.spend()
            raise rm.ApiFailure("HTTP 429 rate_limit_exceeded")

        with tempfile.TemporaryDirectory() as folder, mock.patch.object(rm, "RUNS_DIR", Path(folder)), mock.patch.dict(os.environ, {"OPENAI_API_KEY": "test"}), \
                mock.patch.object(rm, "http_call", side_effect=failing):
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                code = rm.run(self.arguments(sha, 1))
            self.assertEqual(3, code)
            self.assertIn("rate_limit_exceeded", stderr.getvalue())
            run_dir = next(Path(folder).iterdir())
            self.assertEqual(1, json.loads((run_dir / "manifest.json").read_text(encoding="utf-8"))["calls_used_total"])
            with self.assertRaises(SystemExit):
                rm.run(self.arguments(sha, 1, resume=str(run_dir)))


if __name__ == "__main__":
    unittest.main()
