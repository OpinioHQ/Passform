# evals 러너

평가셋(`evalset.jsonl`)을 실제 `AnalysisService`·`SummaryService`로 실행하고 `EVAL_DESIGN.md` 1절의 지표를 계산한다. 저장소만 모의 객체이고 LLM 호출은 실제다. `./gradlew build`·`test`에는 들어가지 않는다.

## 실행

| 하고 싶은 것 | 명령 |
|---|---|
| 실제 LLM으로 실행 | `./gradlew evals --args="--mode live --max-calls N --change '설명' --golden '골든 결과'"` |
| 저장한 응답으로 다시 실행 | `./gradlew evals --args="--mode stored --from <run_id>"` |
| judge만 다시 실행(judge 프롬프트를 바꾼 뒤) | `./gradlew evals --args="--judge-only <run_id> --mode live --max-calls N"` |
| 사람이 채운 라벨 시트를 옮기기 | `./gradlew evals --args="--import-labels evals/results/labels/<시트>.csv"` |
| judge 일치율 재기 | `./gradlew evals --args="--judge-agreement <run_id>"` |

- `OPENAI_API_KEY`는 OS 환경변수로만 읽는다. live는 `--max-calls`(사람이 승인한 수)가 필수이고, 닿기 전에 멈춘다.
- 베이스라인은 머지된 `main` 커밋에서 돌린다. 작업 트리에 바뀐 파일이 있으면 결과의 `commit`이 `-dirty`로 남는다.
- live 응답은 `evals/stored/<run_id>/`에 저장된다. 이 폴더는 저장소에 올리지 않는다(`.gitignore`).

## 결과 읽기

- `results/<run_id>.json`: 실행 정보, 지표(값·분자·분모·목표·충족), 케이스별 출력과 판정.
- `results/history.csv`: 실행마다 한 줄.
- `met`이 `null`이면 판정 보류다. 실행 정보의 `met_status`가 이유를 알려 준다.
  - `hold:incomplete`: 호출 상한에 닿아 일부 케이스만 돌았다.
  - `hold:not_comparable`: stored에서 코드가 만든 요청이 저장된 요청과 달라졌다. 저장 응답이 다른 요청에 끼워졌을 수 있어 비교하지 않는다.
  - 인젝션 저항만 보류된 경우는 결과를 내지 않은 인젝션 케이스가 있다는 뜻이다(지표의 `not_evaluable`).

## 사람 라벨 (베이스라인 중 한 회차만)

러너는 실행마다 `results/labels/`에 시트 두 개를 만든다. 라벨은 베이스라인 3회 중 요약 문장이 가장 많이 나온 회차의 시트에만 매긴다(`EVAL_DESIGN.md` 3절). 회차는 시트의 줄 수로만 고른다.

**라벨을 다 매길 때까지 그 run의 결과 JSON(`judge_units`, `judge_summary`, 지표)과 `history.csv`를 열지 않는다.** judge 판정과 요약문 충실도 값이 들어 있다.

### judge 라벨 (`<run_id>_summary_sentences.csv`)

1. 시트를 스프레드시트로 연다. 한 줄이 요약 문장 하나이고, 근거(`evidence`)와 주제가 함께 있다.
2. 두 사람이 따로 `c1`~`c3`(e30은 `c4`도)에 통과면 `1`, 실패면 `0`을 적는다(`o`/`x`, `통과`/`실패`도 된다). 기준은 `judge_prompt.md`의 마커 안과 같다.
3. 엇갈린 칸은 둘이 이야기해 하나로 정한다(합의 라벨). 판단 근거는 `note` 칸에 적는다.
4. 합의한 시트를 CSV(UTF-8)로 저장하고 `--import-labels`로 옮긴다. `judge_labels.jsonl`에 `{"run_id","unit_id","case_id","c1",...}` 줄이 붙는다. 같은 run의 줄이 이미 있으면 멈추므로, 다시 옮기려면 그 줄을 지우고 실행한다.
5. `--judge-agreement <run_id>`로 기준별 일치율을 잰다. judge의 `unclear`와 오류는 불일치로 센다. 코드가 먼저 걸러 judge를 부르지 않은 문장은 비교에서 빠진다. 엇갈린 단위는 `results/judge_agreement_<run_id>.json`에 남는다.
6. 기준별 일치율이 80% 미만이면 엇갈린 단위를 읽고 판정 조건을 고쳐 `judge_v2`로 올린다. 그 뒤 `--judge-only <run_id>`로 저장된 출력에 judge만 다시 돌리고, 새 run에 `--judge-agreement`를 실행한다. 라벨은 원래 run의 것을 그대로 쓴다.

### 주제 라벨 (`<run_id>_theme_labels.csv`)

1. 한 줄이 답 하나다. `theme_list`(그 run에서 나온 주제 목록) 중 하나를 `human_label`에 적는다. 어디에도 맞지 않으면 `기타`다.
2. `--import-labels`로 옮기면 `theme_labels.jsonl`에 `{"run_id","case_id","response_id","question_id","label"}` 줄이 붙는다.
3. 주제 이름은 run마다 LLM이 새로 만들기 때문에, 러너는 같은 run(stored로 다시 돌린 run 포함)의 분류기 출력과만 비교한다. 다른 run에서는 주제 분류 정확도가 `not_measured`로 남는다.
