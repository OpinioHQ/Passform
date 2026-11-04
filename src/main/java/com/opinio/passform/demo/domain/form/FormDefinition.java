package com.opinio.passform.demo.domain.form;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FormDefinition(String title, String note, List<Section> sections) {

	public FormDefinition {
		sections = (sections != null) ? sections : List.of();
	}

	public List<Question> questions() {
		return sections.stream().flatMap(section -> section.questions().stream()).toList();
	}

	public Optional<Question> question(String questionId) {
		return questions().stream().filter(question -> question.questionId().equals(questionId)).findFirst();
	}

	public boolean hasBranching() {
		return questions().stream().anyMatch(question -> !question.branchRules().isEmpty());
	}

	public record Section(@JsonProperty("section_id") String sectionId, String title, List<Question> questions) {

		public Section {
			questions = (questions != null) ? questions : List.of();
		}

	}

}
