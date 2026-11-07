package com.opinio.passform.support;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

// 폼 정의 예시. 모두 가상 데이터다. 호출마다 새 수정 가능한 값을 돌려준다.
public final class SampleForms {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private SampleForms() {
	}

	// 분기 없는 폼. 이름 형식 문항 2개(그중 하나만 본인 정보), 전화 형식 문항, 형식 없는 문항 2개가 있다.
	public static Map<String, Object> unbranched() {
		return parse("""
				{
				  "title": "가상 신청", "note": "가상 설명", "deadline": "2099-11-30T23:59:00+09:00",
				  "settings": {"editable": false, "cancellable": false, "response_limit": null,
				    "display": {"default_mode": "one_question"}},
				  "payment_link": null, "consent_template_id": "consent_default", "note_attachments": [],
				  "sections": [
				    {"section_id": "s1", "title": "기본", "questions": [
				      {"question_id": "q1", "template_id": null, "question_note": "학년", "question_type": "multiple_choice",
				       "options": ["1학년", "2학년"], "required": true, "profile_key": null, "attachments": [], "branch_rules": []},
				      {"question_id": "q2", "template_id": null, "question_note": "본인 이름", "question_type": "short_answer",
				       "options": [], "required": true, "input_format": {"kind": "name"}, "profile_key": "name",
				       "attachments": [], "branch_rules": []},
				      {"question_id": "q3", "template_id": null, "question_note": "보호자 이름", "question_type": "short_answer",
				       "options": [], "required": false, "input_format": {"kind": "name"}, "profile_key": null,
				       "attachments": [], "branch_rules": []}
				    ]},
				    {"section_id": "s2", "title": "연락", "questions": [
				      {"question_id": "q4", "template_id": null, "question_note": "연락처", "question_type": "short_answer",
				       "options": [], "required": false, "input_format": {"kind": "mobile_phone"}, "profile_key": null,
				       "attachments": [], "branch_rules": []},
				      {"question_id": "q5", "template_id": null, "question_note": "하고 싶은 말", "question_type": "long_answer",
				       "options": [], "required": false, "profile_key": null, "attachments": [], "branch_rules": []}
				    ]}
				  ]
				}
				""");
	}

	// section_page 분기 폼. s1의 마지막 문항 q2에서 "아니요"면 s3으로 건너뛴다.
	public static Map<String, Object> branched() {
		return parse("""
				{
				  "title": "가상 분기", "note": "", "deadline": null,
				  "settings": {"editable": false, "cancellable": false, "response_limit": null,
				    "display": {"default_mode": "section_page"}},
				  "payment_link": null, "consent_template_id": "consent_default", "note_attachments": [],
				  "sections": [
				    {"section_id": "s1", "title": "첫째", "questions": [
				      {"question_id": "q1", "template_id": null, "question_note": "학년", "question_type": "dropdown",
				       "options": ["1학년", "2학년"], "required": true, "profile_key": null, "attachments": [], "branch_rules": []},
				      {"question_id": "q2", "template_id": null, "question_note": "참여하나요", "question_type": "multiple_choice",
				       "options": ["예", "아니요"], "required": true, "profile_key": null, "attachments": [],
				       "branch_rules": [
				         {"when": {"match": "all", "conditions": [{"question_id": "q2", "option": "아니요"}]},
				          "go_to": {"section_id": "s3"}}
				       ]}
				    ]},
				    {"section_id": "s2", "title": "둘째", "questions": [
				      {"question_id": "q3", "template_id": null, "question_note": "역할", "question_type": "short_answer",
				       "options": [], "required": false, "profile_key": null, "attachments": [], "branch_rules": []}
				    ]},
				    {"section_id": "s3", "title": "셋째", "questions": [
				      {"question_id": "q4", "template_id": null, "question_note": "소감", "question_type": "long_answer",
				       "options": [], "required": false, "profile_key": null, "attachments": [], "branch_rules": []}
				    ]}
				  ]
				}
				""");
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> question(Map<String, Object> definition, int section, int question) {
		return (Map<String, Object>) ((List<Object>) ((Map<String, Object>) ((List<Object>) definition.get("sections"))
			.get(section)).get("questions")).get(question);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> settings(Map<String, Object> definition) {
		return (Map<String, Object>) definition.get("settings");
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parse(String json) {
		return JSON.readValue(json, Map.class);
	}

}
