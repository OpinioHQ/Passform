package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

// request_context.schema.json의 여섯 키(SPEC 5절 306행). operation은 null이어도 키를 둔다(305행).
@JsonInclude(Include.NON_NULL)
public record RequestContext(List<String> conditions, String match, @JsonInclude(Include.ALWAYS) String operation,
		Sort sort, String target, String topic) {

	static final String COUNT = "count";

	static final String LIST = "list";

	static final String SUMMARIZE = "summarize";

	public record Sort(String by, String order) {
	}

	// 동작에 맞지 않는 sort·target·topic은 검증하지 않고 버린다(SPEC 311·312행). 동작을 모르면 다시 고를 수 있게 둔다.
	RequestContext withoutUnusedKeys() {
		boolean keepSort = operation == null || LIST.equals(operation);
		boolean keepSummaryKeys = operation == null || SUMMARIZE.equals(operation);
		return new RequestContext(conditions, match, operation, keepSort ? sort : null,
				keepSummaryKeys ? target : null, keepSummaryKeys ? topic : null);
	}

	// 되살린 조건은 결합 방식을 원문에서 확인하지 못했으므로 match를 지운다. 2개 이상이면 다시 묻게 된다.
	RequestContext withRevivedConditions(List<String> revived) {
		return new RequestContext(revived, null, operation, sort, target, topic);
	}

	// 파서가 낸 조건 뒤에 되살린 조건을 붙인다. 파서가 낸 match는 그대로 둔다(골든 parser_partially_dropped_condition_clarify note).
	RequestContext withAddedConditions(List<String> added) {
		List<String> all = new ArrayList<>(conditions);
		all.addAll(added);
		return new RequestContext(List.copyOf(all), match, operation, sort, target, topic);
	}

}
