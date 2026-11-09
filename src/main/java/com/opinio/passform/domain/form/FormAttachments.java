package com.opinio.passform.domain.form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 폼 정의 JSON의 첨부 목록(note_attachments, Question.attachments)을 읽고 바꾼다. 입력을 바꾸지 않고 새 정의를 돌려준다.
// questionId가 null이면 폼 설명란 첨부다.
public final class FormAttachments {

	private static final String NOTE = "note_attachments";

	private static final String SECTIONS = "sections";

	private static final String QUESTIONS = "questions";

	private static final String QUESTION_ID = "question_id";

	private static final String ATTACHMENTS = "attachments";

	private static final String ATTACHMENT_ID = "attachment_id";

	private FormAttachments() {
	}

	public static boolean hasQuestion(Map<String, Object> definition, String questionId) {
		return questionsOf(definition).stream().anyMatch(question -> questionId.equals(question.get(QUESTION_ID)));
	}

	public static boolean contains(Map<String, Object> definition, String questionId, String attachmentId) {
		if (definition == null) {
			return false;
		}
		if (questionId == null) {
			return hasItem(definition.get(NOTE), attachmentId);
		}
		return questionsOf(definition).stream()
			.filter(question -> questionId.equals(question.get(QUESTION_ID)))
			.anyMatch(question -> hasItem(question.get(ATTACHMENTS), attachmentId));
	}

	public static Set<String> allIds(Map<String, Object> definition) {
		Set<String> ids = new LinkedHashSet<>();
		if (definition == null) {
			return ids;
		}
		collectIds(definition.get(NOTE), ids);
		questionsOf(definition).forEach(question -> collectIds(question.get(ATTACHMENTS), ids));
		return ids;
	}

	public static Map<String, Object> withAdded(Map<String, Object> definition, String questionId,
			Map<String, Object> item) {
		return withList(definition, questionId, items -> items.add(item));
	}

	public static Map<String, Object> withRemoved(Map<String, Object> definition, String questionId,
			String attachmentId) {
		return withList(definition, questionId, items -> items.removeIf(item -> attachmentId.equals(idOf(item))));
	}

	// 첨부 목록은 첨부 API로만 바뀐다. 빌더가 보낸 목록을 버리고 저장된 목록을 문항 ID로 찾아 넣는다(사람 결정, #63).
	// stored가 null이면(새 폼) 목록을 비운다. 다른 폼의 attachment_id를 끌어올 수 없다.
	// 값의 모양이 틀린 곳은 그대로 두어 스키마 검증이 거부하게 한다.
	public static Map<String, Object> keepStoredLists(Map<String, Object> incoming, Map<String, Object> stored) {
		Map<String, Object> result = new LinkedHashMap<>(incoming);
		if (result.get(NOTE) instanceof List<?>) {
			result.put(NOTE, stored == null ? List.of() : FormJson.array(stored.get(NOTE)));
		}
		if (!(result.get(SECTIONS) instanceof List<?> sections)) {
			return result;
		}
		Map<Object, Object> storedLists = new HashMap<>();
		if (stored != null) {
			questionsOf(stored).forEach(question -> storedLists.putIfAbsent(question.get(QUESTION_ID), question.get(ATTACHMENTS)));
		}
		List<Object> nextSections = new ArrayList<>();
		for (Object section : sections) {
			nextSections.add(section instanceof Map<?, ?> ? sectionWithStoredLists(FormJson.object(section), storedLists) : section);
		}
		result.put(SECTIONS, nextSections);
		return result;
	}

	private static Map<String, Object> sectionWithStoredLists(Map<String, Object> section, Map<Object, Object> storedLists) {
		Map<String, Object> next = new LinkedHashMap<>(section);
		if (next.get(QUESTIONS) instanceof List<?> questions) {
			List<Object> nextQuestions = new ArrayList<>();
			for (Object question : questions) {
				if (question instanceof Map<?, ?> && FormJson.object(question).get(ATTACHMENTS) instanceof List<?>) {
					Map<String, Object> copy = new LinkedHashMap<>(FormJson.object(question));
					copy.put(ATTACHMENTS, FormJson.array(storedLists.get(copy.get(QUESTION_ID))));
					nextQuestions.add(copy);
				}
				else {
					nextQuestions.add(question);
				}
			}
			next.put(QUESTIONS, nextQuestions);
		}
		return next;
	}

	private interface ListChange {

		void apply(List<Object> items);

	}

	private static Map<String, Object> withList(Map<String, Object> definition, String questionId, ListChange change) {
		Map<String, Object> result = new LinkedHashMap<>(definition);
		if (questionId == null) {
			result.put(NOTE, changed(definition.get(NOTE), change));
			return result;
		}
		List<Object> sections = new ArrayList<>();
		for (Object rawSection : FormJson.array(definition.get(SECTIONS))) {
			Map<String, Object> section = new LinkedHashMap<>(FormJson.object(rawSection));
			List<Object> questions = new ArrayList<>();
			for (Object rawQuestion : FormJson.array(section.get(QUESTIONS))) {
				Map<String, Object> question = new LinkedHashMap<>(FormJson.object(rawQuestion));
				if (questionId.equals(question.get(QUESTION_ID))) {
					question.put(ATTACHMENTS, changed(question.get(ATTACHMENTS), change));
				}
				questions.add(question);
			}
			section.put(QUESTIONS, questions);
			sections.add(section);
		}
		result.put(SECTIONS, sections);
		return result;
	}

	private static List<Object> changed(Object list, ListChange change) {
		List<Object> items = new ArrayList<>(FormJson.array(list));
		change.apply(items);
		return items;
	}

	private static List<Map<String, Object>> questionsOf(Map<String, Object> definition) {
		List<Map<String, Object>> questions = new ArrayList<>();
		for (Object section : FormJson.array(definition.get(SECTIONS))) {
			for (Object question : FormJson.array(FormJson.object(section).get(QUESTIONS))) {
				questions.add(FormJson.object(question));
			}
		}
		return questions;
	}

	private static boolean hasItem(Object list, String attachmentId) {
		return FormJson.array(list).stream().anyMatch(item -> attachmentId.equals(idOf(item)));
	}

	private static void collectIds(Object list, Set<String> ids) {
		FormJson.array(list).forEach(item -> {
			String id = idOf(item);
			if (id != null) {
				ids.add(id);
			}
		});
	}

	private static String idOf(Object item) {
		return FormJson.text(FormJson.object(item).get(ATTACHMENT_ID));
	}

}
