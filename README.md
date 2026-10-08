# Passform
---
## 프로젝트 소개
- 2026년 2학기 AI캡스톤디자인 Opinio 팀
- 사용자 중심의 설문조사 서비스, Passform 개발
## 팀원 소개
- 202322087 소프트웨어학과 손예은
- 202321989 소프트웨어학과 박소연

---

## Github

<details>
<summary>저장소 구조</summary>

```jsx
<팀-저장소>/
  AGENTS.md                          # 강의 4 — 도메인 용어집, 절대 규칙, 완료의 정의
  docs/
    research/interviews.md           # 강의 2 — 인터뷰 프로토콜과 로그
    ontology.yaml                    # 강의 2 — 미니 온톨로지
    PROBLEM.md                       # 강의 4 — 문제 정의서
    SPEC.md                          # 강의 4 — 제품 스펙(SDD)과 수용 기준(AC)
    spikes/YYYY-MM-DD_<가설>.md      # 강의 4 — 기술 타당성 스파이크
    ARCHITECTURE.md                  # 강의 6 — 아키텍처 설계서
    prompts/delegation_examples.md   # 강의 5 — 위임 프롬프트와 검증 루프 기록
  src/response_analysis/
    schemas/request_context.schema.json     # 강의 3 — 출력 스키마
    prompts/parse_query.md           # 강의 3 — 파싱 프롬프트
    tools/                           # 강의 6 — 도구 구현
  config/rag.yaml                    # 강의 6 — RAG 설정
  tests/
    harness/golden_cases.yaml        # 강의 5 — 골든 케이스(데이터)
    test_<기능>_golden.py            # 강의 5 — 골든 테스트
  evals/
    evalset.jsonl                    # 강의 7 — 평가 데이터셋
    README.md                        # 강의 7 — evals 설계
    judge_prompt.md                  # 강의 7 — judge 프롬프트
    run_evals.py                     # 강의 7 — 러너
    results/YYYY-MM-DD.json          # 강의 7 — 측정 기록
  data/seed/                         # 예시 데이터
```
</details>

<details>
<summary>커밋 규칙</summary>

```jsx
{tag}: {Message} ({issueNum})

ex) feat: 예약 Dto 수정 (#31)
```

| 태그(tag) | **의미** | **사용 예시** |
| --- | --- | --- |
| feat | 새로운 기능 추가 | `Feat: 카카오 로그인 기능 구현` |
| fix | 버그 수정 | `Fix: 메인 페이지 결제 버튼 클릭 오류 수정` |
| docs | 문서 수정 (README 등) | `Docs: README에 프로젝트 설치 방법 추가` |
| style | 코드 포맷팅, 세미콜론 누락 등 (코드 로직 변경 없음) | `Style: 로그인 컴포넌트 들여쓰기 수정` |
| **refactor** | 코드 리팩토링 (기능 변경 없이 코드 구조 개선) | `Refactor: 중복 코드 함수화 처리` |
| **chore** | 빌드 업무, 패키지 매니저 설정, 기타 잡동사니 | `Chore: package.json 라이브러리 추가` |
- 커밋 메시지는 **한글로** 작성 (50자 이내)
- 파일명, 디렉토리명은 **커밋 메시지에 작성 금지**
- `:` 뒤에만 스페이스 있음 → `feat: 메시지`
</details>

<details>
<summary>이슈 작성 규칙</summary>

### **제목 형식**

```
[{tag}] {issue title}

ex) [feat] 로그인 기능 개발
```

### **템플릿 예시**

```
---
title: issue_template_feature
about: 기능개발 시 이슈 템플릿
tag: "[feat]"
---

## 🤔 기능 설명
> 추가/수정하려는 기능에 대해 간결하게 설명해주세요
예시:
	[1] 구현할 기능 1
	[2] 구현할 기능 2

## 💻 작업 상세 내용
- [TODO]
- 기능 관련해서 필요한 작업

## 참고할 수 있는 자료 (선택)
```
</details>

<details>
<summary> 풀 리퀘스트 규칙 </summary>

### PR 제목 규칙

```jsx
[{tag}] PR 한 줄 제목 (#이슈번호)
ex) [feat] 카카오 소셜 로그인 기능 구현 (#12)
```

### PR 본문 내용

```jsx
## 📌 관련 이슈
ex) #12

## 📝 작업 내용
- [ ] 주요 변경 사항 1
- [ ] 주요 변경 사항 2

## 📸 스크린샷 (선택)
<!-- UI 변경 사항이나 결과 화면이 있다면 첨부해주세요 -->

## 💬 리뷰어에게 전달할 내용
<!-- 집중해서 봐주었으면 하는 부분이나 논의하고 싶은 내용을 자유롭게 작성해주세요 -->
```
</details>

<details>
<summary> 브랜치 규칙 </summary>

```jsx
{tag}/{작업내용}
ex) feature/login
```

**주요 브랜치 종류 (Branch Strategy)**

- **`main` (또는 `master`):** **최종 완제품 브랜치.** 버그 없이 완전히 작동하는 대표 코드만 존재하며, 여기서 직접 작업하거나 `git push`하는 것은 금지합니다.
- **`feature/...` (기능 개발 브랜치):** **각자의 작업실**입니다. 이슈 단위로 새 브랜치를 만들어 작업한 뒤, 작업이 끝나면 `main`으로 PR을 보냅니다.
</details>
