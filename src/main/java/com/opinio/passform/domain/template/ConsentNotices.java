package com.opinio.passform.domain.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 템플릿 밖의 고정 문구. 코드 안에 두어 배포마다 같은 값을 쓴다.
public final class ConsentNotices {

	// 동의 거부 안내의 기본 문구(SPEC 5절 210행). AI가 공식 자료로 초안을 쓰고 사람이 승인했다. 법률 자문이 아니다.
	public static final String REFUSAL_NOTICE = "동의를 거부할 수 있습니다. 동의하지 않으면 이 폼에 응답을 제출할 수 없습니다.";

	private ConsentNotices() {
	}

	// AI 요약 외부 전송 고지(SPEC 5절 211행 그대로, 확인일 2026-11-09). 폼의 첫 배포 때 이 값을 기록해 고정한다(158행).
	public static Map<String, Object> aiSummaryNotice() {
		Map<String, Object> notice = new LinkedHashMap<>();
		notice.put("provider", "OpenAI");
		notice.put("processing_country", "OpenAI가 정하는 국외 지역(이 서비스에서 처리 국가를 지정하지 않음)");
		notice.put("items", List.of("AI 요약 대상 주관식 응답 내용(개인정보 문항 제외, 연락처 마스킹)"));
		notice.put("purpose", "제작자의 응답 요약·주제 분류");
		notice.put("retention", "제공사 정책상 남용 감시 로그를 최대 30일 보관(법적 요구가 있으면 더 길어질 수 있음). 기본 설정에서 학습에 사용하지 않음");
		notice.put("refusal_effect", "제출할 수 있으며, 내 응답은 AI 요약에서만 제외된다");
		notice.put("checked_on", "2026-11-09");
		return notice;
	}

}
