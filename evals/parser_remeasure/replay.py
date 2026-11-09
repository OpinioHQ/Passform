"""저장한 파서 출력을 코드 보정 뒤 결과로 비교한다. OpenAI를 부르지 않는다.

순서: python evals/parser_remeasure/replay.py prepare → ParserReplayTest를 src/test로 복사해 실행 → summarize
기대값도 같은 흐름에 넣어(prompt "expected") 제품이 내야 할 결과를 코드로 얻고, 저장한 출력의 결과와 비교한다.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import remeasure

HERE = Path(__file__).resolve().parent
RUNS = HERE / "runs"
REPLAY = HERE / "replay"
INPUT = REPLAY / "input.jsonl"
OUTPUT = REPLAY / "output.jsonl"


def expected_output(expected: dict[str, Any]) -> dict[str, Any]:
    """기대값을 파서 출력 형태로 만든다. 비어 있는 선택 키는 뺀다."""
    output: dict[str, Any] = {"conditions": expected["conditions"], "operation": expected.get("operation")}
    for key in ("match", "sort", "target", "topic"):
        if expected.get(key):
            output[key] = expected[key]
    return output


def run_dirs() -> list[Path]:
    return sorted(path for path in RUNS.iterdir() if path.is_dir())


def questions_of(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    path, count = remeasure.QUESTION_SETS[manifest.get("question_set", "main")]
    return remeasure.load_questions(path, count=count)


def build_input() -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for run_dir in run_dirs():
        manifest = json.loads((run_dir / "manifest.json").read_text(encoding="utf-8"))
        question_set = manifest.get("question_set", "main")
        questions = {item["id"]: item for item in questions_of(manifest)}
        for question in questions.values():
            if (question_set, question["id"]) not in seen:
                seen.add((question_set, question["id"]))
                rows.append({"model": "expected", "set": question_set, "prompt": "expected", "round": 0, "question_id": question["id"],
                             "form": question["form"], "query": question["question"], "output": expected_output(question["expected"])})
        for row in remeasure.load_results(run_dir):
            question = questions[row["question_id"]]
            rows.append({"model": manifest["model_requested"], "set": question_set, "prompt": row["prompt"], "round": row["round"],
                         "question_id": row["question_id"], "form": question["form"], "query": question["question"], "output": row["context"]})
    return rows


def outcome(row: dict[str, Any]) -> tuple[Any, Any, Any, Any]:
    if row.get("error"):
        return ("error", row["error"], None, None)
    corrected = row.get("corrected") or {}
    # 되묻기는 사용자에게 사유만 보이므로 내부 operation 차이는 결과 차이로 세지 않는다.
    operation = corrected.get("operation") if row.get("status") == "ok" else None
    return (row.get("status"), row.get("reason"), row.get("count"), operation)


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    expected = {(row["set"], row["question_id"]): row for row in rows if row["prompt"] == "expected"}
    groups: dict[tuple[str, str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        if row["prompt"] != "expected":
            groups[(row["model"], row["set"], row["prompt"])].append(row)
    summary: dict[str, Any] = {"expected": {f"{key[0]}/{key[1]}": list(outcome(row)) for key, row in sorted(expected.items())}, "groups": {}}
    for (model, question_set, prompt), items in sorted(groups.items()):
        same = 0
        per_question: Counter[str] = Counter()
        reasons: Counter[str] = Counter()
        wrong_number: Counter[str] = Counter()
        whole_count: Counter[str] = Counter()
        for row in items:
            target = expected[(question_set, row["question_id"])]
            got = outcome(row)
            reasons[str(row.get("reason")) if row.get("status") == "clarify" else ("error" if row.get("error") else "ok")] += 1
            if got == outcome(target):
                same += 1
                per_question[row["question_id"]] += 1
            elif row.get("status") == "ok":
                wrong_number[row["question_id"]] += 1
                corrected = row.get("corrected") or {}
                # 조건 없는 summarize는 원래 전체 대상이라 count·list만 전체 건수 위험으로 센다.
                if corrected.get("operation") in ("count", "list") and not corrected.get("conditions") and (target.get("corrected") or {}).get("conditions"):
                    whole_count[row["question_id"]] += 1
        summary["groups"][f"{model}/{question_set}/{prompt}"] = {
            "total": len(items), "same_as_expected": same, "reasons": dict(sorted(reasons.items())),
            "per_question": {key: per_question[key] for key in sorted({row["question_id"] for row in items})},
            "answered_with_different_result": dict(sorted(wrong_number.items())), "whole_count_risk": dict(sorted(whole_count.items())),
        }
    return summary


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "summarize"])
    arguments = parser.parse_args(argv)
    if arguments.command == "prepare":
        REPLAY.mkdir(exist_ok=True)
        rows = build_input()
        INPUT.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8", newline="\n")
        print(f"재생 입력 {len(rows)}행")
        return 0
    json.dump(summarize(read_jsonl(OUTPUT)), sys.stdout, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
