package com.opinio.passform.demo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.Question;
import com.opinio.passform.demo.domain.response.Response;

// 분석에 쓰는 폼 한 개의 읽기 전용 묶음. LLM 호출 전에 읽어 두어 트랜잭션을 잡지 않는다.
record FormSnapshot(Form form, Map<Integer, FormDefinition> definitions, List<Response> submitted,
		List<ResponseRecord> records) {

	FormDefinition latestDefinition() {
		return definitions.get(latestVersion());
	}

	int latestVersion() {
		return new TreeMap<>(definitions).lastKey();
	}

	// 현재 응답이 남은 버전. 응답이 하나도 없으면 최신 버전으로 대조한다.
	Map<Integer, FormDefinition> activeDefinitions() {
		Map<Integer, FormDefinition> active = new TreeMap<>();
		records.forEach(record -> active.put(record.formVersion(), definitions.get(record.formVersion())));
		if (active.isEmpty()) {
			active.put(latestVersion(), latestDefinition());
		}
		return active;
	}

	List<ResponseRecord> recordsOf(Collection<String> responseIds) {
		return records.stream().filter(record -> responseIds.contains(record.responseId())).toList();
	}

	List<Response> responsesOf(Collection<String> responseIds) {
		return submitted.stream().filter(response -> responseIds.contains(response.getId())).toList();
	}

	Optional<Question> question(int version, String questionId) {
		return definitions.get(version).question(questionId);
	}

	// 문항·선택지 문구. 원문 규칙 검사의 오탐을 거르는 데 쓴다.
	List<String> formTexts() {
		List<String> texts = new ArrayList<>();
		definitions.values().forEach(definition -> definition.questions().forEach(question -> {
			texts.add(question.questionNote());
			texts.addAll(question.options());
		}));
		return texts;
	}

}
