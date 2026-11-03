## §0 위임 대상 선정 (강의 5 A절, 팀 결정 2026-11-03)

- 첫 위임 기능: `com.opinio.passform.domain.analysis.ConditionEvaluator` — RequestContext의 conditions·match, 매처 출력(조건·버전별 매칭), 버전별 폼 정의, 응답 목록을 받아 일치 응답 수·ID(오름차순), matched, coverage를 돌려준다.
- 대상 AC: AC24, AC78, AC23, AC20 일부, AC22 일부, AC31 일부(count·list).
- 직접 담당한 것(사람): 3값 판정 규칙과 coverage 구성 규칙(SPEC 5절), 골든 케이스 15건의 기대값 확정(Claude 초안 → 팀 확인).
- 위임한 이유: 입출력이 SPEC 5절 JSON으로 고정되어 있고, 매칭 방식(스파이크)과 무관한 순수 함수라 완료 여부를 골든 케이스로 판정할 수 있다.
- 직접 담당한 이유: 판정 불가를 어떻게 세는지가 "숫자를 지어내지 않는다"(절대 규칙 3)를 가르는 제품 판단이기 때문이다.
- 범위 밖: 분기로 건너뛴 문항의 판정 불가(응답 경로 계산, 2차 위임), clarify 판정, 정렬, 출력 형태(count/list/summarize).
- 위치: domain 패키지(DB·LLM 없는 순수 판정 로직, AC36 커버리지 대상).
- 골든 케이스: tests/harness/golden_cases.yaml(15건: 정상 7, 경계 4, 금지 행동 4), fixture는 data/seed/forms의 form_a·form_b(기준 커밋 15210e3).

## §1 테스트 하니스 위임과 결함 주입 기록

## §2 기능 위임 프롬프트 원본

## §3 검증 루프 기록

## §4 수용 판단
