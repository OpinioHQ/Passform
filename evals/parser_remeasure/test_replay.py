"""재생 집계 테스트. 실제 호출을 하지 않는다."""
from __future__ import annotations

import unittest

import replay


def row(prompt, question_id="q1", **fields):
    base = {"model": "expected" if prompt == "expected" else "m", "set": "main", "prompt": prompt, "round": 0 if prompt == "expected" else 1, "question_id": question_id}
    return {**base, **fields}


EXPECTED_COUNT = row("expected", status="ok", reason=None, count=2, corrected={"conditions": ["가"], "operation": "count"})


class Summarize(unittest.TestCase):
    def group(self, *rows):
        return replay.summarize([EXPECTED_COUNT, *rows])["groups"]["m/main/v2"]

    def test_보정_뒤_같은_결과면_일치로_센다(self):
        group = self.group(row("v2", status="ok", reason=None, count=2, corrected={"conditions": ["가"], "operation": "count"}))
        self.assertEqual(1, group["same_as_expected"])
        self.assertEqual({}, group["answered_with_different_result"])

    def test_조건이_사라져_다른_숫자를_내면_전체_건수_위험이다(self):
        group = self.group(row("v2", status="ok", reason=None, count=9, corrected={"conditions": [], "operation": "count"}))
        self.assertEqual(0, group["same_as_expected"])
        self.assertEqual({"q1": 1}, group["whole_count_risk"])

    def test_되묻기는_숫자_오답으로_세지_않고_사유로_센다(self):
        group = self.group(row("v2", status="clarify", reason="unknown_condition", count=None, corrected={"conditions": ["나"], "operation": "count"}))
        self.assertEqual({"unknown_condition": 1}, group["reasons"])
        self.assertEqual({}, group["answered_with_different_result"])

    def test_실행_오류는_error로_센다(self):
        group = self.group(row("v2", error="NullPointerException: x"))
        self.assertEqual({"error": 1}, group["reasons"])
        self.assertEqual(0, group["same_as_expected"])


class ExpectedOutput(unittest.TestCase):
    def test_빈_선택_키를_빼고_operation_null은_남긴다(self):
        self.assertEqual({"conditions": [], "operation": None}, replay.expected_output({"conditions": [], "match": None, "operation": None, "sort": None, "target": "", "topic": ""}))


if __name__ == "__main__":
    unittest.main()
