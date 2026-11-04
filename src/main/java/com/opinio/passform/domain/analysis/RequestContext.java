package com.opinio.passform.domain.analysis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestContext(List<String> conditions, String match, String operation, Sort sort, String target,
		String topic) {

	public static final String COUNT = "count";

	public static final String LIST = "list";

	public static final String SUMMARIZE = "summarize";

	// list가 아니면 sort를, summarize가 아니면 target·topic을 버리고 진행한다(parse_query.md 실패 모드 11, 14).
	public RequestContext withoutIrrelevantFields() {
		return new RequestContext(conditions, match, operation, LIST.equals(operation) ? sort : null,
				SUMMARIZE.equals(operation) ? target : null, SUMMARIZE.equals(operation) ? topic : null);
	}

	public RequestContext withMatch(String newMatch) {
		return new RequestContext(conditions, newMatch, operation, sort, target, topic);
	}

	public MatchMode matchMode() {
		if ("any".equals(match)) {
			return MatchMode.ANY;
		}
		return "not".equals(match) ? MatchMode.NOT : MatchMode.ALL;
	}

	public record Sort(String by, String order) {
	}

}
