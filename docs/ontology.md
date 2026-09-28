### E. 미니 온톨로지

먼저 로그에서 어휘를 뽑는다.

- 반복된 명사 → 클래스 후보: ____
    - 폼/설문/설문지/신청폼 → `Form`
    - 사용자/제작자/응답자 → `User`
    - 질문/문항/질문지 → `Question`
    - 응답/응답 내용/제출 내용 → `Response`
    - 응답 결과/통계/분석 결과 → `Result`
    - 외부 앱/엑셀·스프레드시트/은행 앱/외부 AI/연동 서비스 → `ExternalService`
- 반복된 동사 → 관계 후보: 폼을 만든다(제작한다), 질문을 넣는다(구성한다), 질문에 답한다(응답한다), 폼을 제출한다, 응답을 모아 본다(집계한다), 결과를 분석한다, 외부 서비스를 사용한다(연동한다)
- 동의어 통합: ____ = ____ → 대표어 `____`
    - ‘폼’ = ‘설문’ = ‘설문지’ = ‘신청폼’ → `Form`,
    - ‘제작자’ = ‘응답자’ = ‘사용자’ → `User`,
    - ‘질문’ = ‘문항’ = ‘질문지’ → `Question`,
    - ‘응답’ = ‘응답 내용’ = ‘제출 내용’ → `Response`,
    - ‘응답 결과’ = ‘통계’ = ‘집계·분석 결과’ → `Result`,
    - ‘외부 앱’ = ‘외부 도구’ = ‘연동 서비스’ → `ExternalService`
- 범위 밖: 실제 은행 송금·결제 처리 / Google Sheets·Google Drive 자체 기능 구현 / 외부 AI 서비스 자체 구현 / 카카오톡 등 외부 서비스 자체 기능 구현

```yaml
version:1 ( 양식 참고용 )
classes:
	<클래스명>:
		description: <이 개념이 무엇인지 한 줄>
		evidence:[로그 _, 로그 _]                       # ← ① 클래스의 근거 (필수)
		attributes:
			<속성명>:{type: <타입>,examples:[<예시>],evidence:[로그 _]}   # ← ② 속성의 근거
		relations:
			-<관계(동사)>: <대상 클래스>                   # ← 반드시 이 파일에 정의된 클래스
		evidence:[로그 _]                           # ← ③ 관계의 근거
# 범위 밖(명시): <이번 제품에서 다루지 않을 개념>
# 핵심 흐름: <클래스> --(동사)--> <클래스> --(동사)--> <클래스>

```

```yaml
version:1
classes:
	Form:
		description: <질문과 이에 따른 전체 응답을 갖는 설문조사>
		evidence:[로그 2, 로그 4, 로그 6, 로그 7, 로그 8]             # 설문조사 = 설문 = 폼 => 대표어 Form
		attributes:
			title:{type: String}    # 설문조사 제목
			note:{type: String}    # 설문조사 설명
			progress:{type: float, range:[0, 1], evidence: {로그 3, 로그 4, 로그 6}}    # 진행률 퍼센트
			question_num:{type: int, evidence:{로그 1, 로그 4, 로그 8, 로그 9}}   # 설문길이(질문 수)
			editable:{type: boolean, evidence: [로그 3, 로그 4, 로그 6]}    # 제출 후 응답 내용 확인·수정 가능 여부
			status:{type: enum, values: [open, closed], evidence: [로그 2]}    # 응답 가능 여부를 표시함

		relations:
			- has: Question                   # ← 반드시 이 파일에 정의된 클래스
				evidence:[로그 1]                           # ← ③ 관계의 근거
			- has: Result
				evidence:[로그 2]
			- has: ExternalService
				evidence:[로그 10]
				
	User:
		description: <설문조사를 제작하고 응답하는 서비스 이용자>
		evidence:[로그 6, 로그 7]                       # ← ① 클래스의 근거 (필수)
		attributes:
			id:{type: int, key:true}
			user_type:{type: enum, value:[creator, respondent]}
			personal_info:{type: list, example:[name, phone_number, email], evidence:{로그 3}}
		relations:
			-make: Form                   # ← 반드시 이 파일에 정의된 클래스
				evidence:[로그 1]                           # ← ③ 관계의 근거
			-make: Response
				evidence:[로그 3, 로그 7]
			
	Question:
		description: <제작자가 의도를 갖고 만든 질문, 폼을 구성함>
		evidence:[로그 1]                       # ← ① 클래스의 근거 (필수)
		attributes:
			question_note:{type: String}    # 질문 내용
			question_type:{type: enum, value:[multiple_choice, short_answer, long_answer, dropdown, checkbox]}
			input_format:{type: String, example:["010-0000-0000", "공백포함500자"], evidence:{로그 6, 관찰 1, 관찰 2}}    # 입력 형식(전화번호 형식, 길이제한 등)
			attachments:{type: list, example:["jpg", "mp4", "pdf"], evidence:{로그 6}}    # 질문에 첨부하고 싶은 참고자료
		relations:
			-belongs_to: Form                   # ← 반드시 이 파일에 정의된 클래스
				evidence:[로그 1]                           # ← ③ 관계의 근거
			-by: User
				evidence:[로그 1, 로그 3]
		
	Response:
		description: 응답자가 하나의 Form에 작성하거나 제출한 응답 내용
		evidence:[로그 3, 로그 4, 로그 6, 관찰 2]                       
		attributes:
			content:{type: list, evidence:[로그 3, 로그 6, 로그 7, 로그 10, 관찰 2]}
      status:{type: enum, values: [in_progress, submitted, abandoned], evidence: [로그 4, 로그 8, 로그 9, 관찰 2]}    
      attachments:{type: list, example:["jpg", "mp4", "pdf"], evidence: [로그 1, 로그 2]}		
		relations:
			- belongs_to: Form
        evidence: [로그 2, 로그 6]
      - by: User
        evidence: [로그 3, 로그 6]
      - answers: Question
        evidence: [로그 7, 로그 10, 관찰 2]                 
		
	Result:
		description: 여러 Response를 집계·분석하여 제작자가 확인하는 응답 결과
		evidence:[로그 1, 로그 2, 로그 5, 관찰 1]                       
		attributes:
			response_count:{type: int, evidence: [로그 5]}
			statistics:{type: list, evidence: [로그 1, 로그 5, 관찰 1]}
			summary:{type: string, evidence: [로그 1, 관찰 1]}
			external_service_linked:{type: boolean, evidence: [로그 2, 관찰 1]}
		relations:
      - aggregates: Response
        evidence: [로그 1, 로그 5, 관찰 1]
      - for: Form
        evidence: [로그 2, 로그 5, 관찰 1]
      - viewed_by: User
        evidence: [로그 5, 관찰 1]
      - integrates_with: ExternalService
        evidence: [로그 2, 관찰 1]
		
	ExternalService:
		description: Form 제작·응답·결과 확인 과정에서 사용하거나 연동하는 외부 서비스
		evidence:[로그 2, 로그 3, 로그 10, 관찰 1, 관찰 2]
		attributes:
			name:{type: string, examples: [Google Sheets, Google Drive, 글자수 세기], evidence: [로그 2, 로그 3, 로그 10, 관찰 1, 관찰 2]}
			used_by_roles:{type: list, values: [creator, respondent], evidence: [관찰 1, 관찰 2]}
			usage_mode:{type: enum, values: [integration, external_transition], evidence: [로그 2, 로그 10, 관찰 1, 관찰 2]}
		relations:
			- used_by: User
      evidence: [로그 3, 로그 10, 관찰 1, 관찰 2]
	    - used_during: Response
	      evidence: [로그 10, 관찰 2]
	    - linked_to: Result
	      evidence: [로그 2, 관찰 1]
      
# 범위 밖(명시): 실제 은행 송금·결제 처리 / Google Sheets·Google Drive 자체 기능 구현 / 외부 AI 서비스 자체 구현 / 카카오톡 등 외부 서비스 자체 기능 구현
# 핵심 흐름: <User> --(제작한다)--> <Form> --(응답한다)--> <Response> --(집계한다)--> <Result>

```
