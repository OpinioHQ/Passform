package com.opinio.passform.demo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.opinio.passform.demo.config.AnalysisProperties;
import com.opinio.passform.demo.domain.analysis.AnswerMasker;
import com.opinio.passform.demo.domain.analysis.OptionStatistics;
import com.opinio.passform.demo.domain.analysis.RequestContext;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.demo.domain.analysis.ThemeAggregator;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.Question;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.llm.LlmClient;
import com.opinio.passform.demo.llm.SummaryWriter;
import com.opinio.passform.demo.llm.ThemeClassifier;
import com.opinio.passform.demo.service.AnalysisResponse.EvidenceView;
import com.opinio.passform.demo.service.AnalysisResponse.OptionView;
import com.opinio.passform.demo.service.AnalysisResponse.SummaryItem;
import com.opinio.passform.demo.service.AnalysisResponse.SummaryView;
import com.opinio.passform.demo.service.AnalysisResponse.TargetView;
import com.opinio.passform.demo.service.AnalysisResponse.ThemeView;
import com.opinio.passform.demo.service.AnalysisResponse.TopicView;
import com.opinio.passform.demo.service.AnalysisResponse.VersionCount;

// SPEC 5절 316~332행. 대상 응답·주제별 개수·순서는 코드가 정하고 LLM은 라벨과 문장만 만든다(AC86~AC89, AC93).
// LLM 호출 수는 상한이 있다: 분류 = 상한 응답 수 / 묶음 크기, 요약문 = 문항 수 × 주제 상한.
@Service
public class SummaryService {

	private static final int EXCERPT_LIMIT = 200;

	private final LlmClient client;

	private final ThemeClassifier classifier;

	private final SummaryWriter writer;

	private final AnalysisProperties properties;

	public SummaryService(LlmClient client, ThemeClassifier classifier, SummaryWriter writer,
			AnalysisProperties properties) {
		this.client = client;
		this.classifier = classifier;
		this.writer = writer;
		this.properties = properties;
	}

	public record Outcome(SummaryView summary, List<VersionCount> unavailableVersions) {
	}

	private record AnswerRef(String responseId, int formVersion, String questionId, String questionNote, String text) {
	}

	public Outcome summarize(RequestContext context, FormSnapshot snapshot, List<Response> matched,
			Map<Integer, Question> targetByVersion) {
		List<VersionCount> unavailable = unavailableVersions(matched, targetByVersion);
		List<Response> consented = matched.stream().filter(Response::isAiSummaryAgreed).toList();
		int excluded = matched.size() - consented.size();
		TopicView emptyTopic = (context.topic() != null) ? new TopicView(context.topic(), null, List.of()) : null;
		// 판정 순서는 no_matches → no_consented_responses → too_many_responses(SPEC 5절 329행). LLM 호출 전에 끝낸다.
		if (matched.isEmpty()) {
			return new Outcome(new SummaryView("no_matches", excluded, List.of(), emptyTopic), unavailable);
		}
		if (consented.isEmpty()) {
			return new Outcome(new SummaryView("no_consented_responses", excluded, List.of(), emptyTopic), unavailable);
		}
		if (consented.size() > properties.maxResponsesPerRequest()) {
			return new Outcome(new SummaryView("too_many_responses", excluded, List.of(), emptyTopic), unavailable);
		}
		List<AnswerRef> answers = collectAnswers(snapshot, consented, targetByVersion);
		if (context.topic() != null) {
			return new Outcome(summarizeTopic(context.topic(), answers, excluded), unavailable);
		}
		List<SummaryItem> items = new ArrayList<>(summarizeThemes(answers, snapshot, matched, targetByVersion));
		if (targetByVersion == null) {
			items.addAll(optionStats(snapshot, matched));
		}
		return new Outcome(new SummaryView("ok", excluded, items, null), unavailable);
	}

	private static List<VersionCount> unavailableVersions(List<Response> matched,
			Map<Integer, Question> targetByVersion) {
		if (targetByVersion == null) {
			return List.of();
		}
		Map<Integer, Integer> counts = new TreeMap<>();
		matched.stream()
			.filter(response -> !targetByVersion.containsKey(response.getFormVersion()))
			.forEach(response -> counts.merge(response.getFormVersion(), 1, Integer::sum));
		return counts.entrySet().stream().map(entry -> new VersionCount(entry.getKey(), entry.getValue())).toList();
	}

	// 동의한 일치 응답의 요약 가능 문항 답만 모으고 전화번호·이메일을 가린다(AC88, AC92).
	private static List<AnswerRef> collectAnswers(FormSnapshot snapshot, List<Response> consented,
			Map<Integer, Question> targetByVersion) {
		Map<String, ResponseRecord> records = new LinkedHashMap<>();
		snapshot.recordsOf(consented.stream().map(Response::getId).toList())
			.forEach(record -> records.put(record.responseId(), record));
		List<AnswerRef> answers = new ArrayList<>();
		for (Response response : consented.stream().sorted(Comparator.comparing(Response::getId)).toList()) {
			int version = response.getFormVersion();
			ResponseRecord record = records.get(response.getId());
			for (Question question : summarizableQuestions(snapshot.definitions().get(version), version,
					targetByVersion)) {
				List<String> values = record.answers().getOrDefault(question.questionId(), List.of());
				if (!values.isEmpty() && !values.get(0).isBlank()) {
					answers.add(new AnswerRef(response.getId(), version, question.questionId(),
							question.questionNote(), AnswerMasker.mask(values.get(0))));
				}
			}
		}
		return answers;
	}

	private static List<Question> summarizableQuestions(FormDefinition definition, int version,
			Map<Integer, Question> targetByVersion) {
		if (targetByVersion != null) {
			Question target = targetByVersion.get(version);
			return (target != null && target.isSummarizable()) ? List.of(target) : List.of();
		}
		return definition.questions().stream().filter(Question::isSummarizable).toList();
	}

	private List<SummaryItem> summarizeThemes(List<AnswerRef> answers, FormSnapshot snapshot, List<Response> matched,
			Map<Integer, Question> targetByVersion) {
		Map<String, ThemeGroup> groups = new LinkedHashMap<>();
		TreeSet<Integer> versions = new TreeSet<>();
		matched.forEach(response -> versions.add(response.getFormVersion()));
		for (int version : versions) {
			for (Question question : summarizableQuestions(snapshot.definitions().get(version), version,
					targetByVersion)) {
				groups.computeIfAbsent(groupKey(question), key -> new ThemeGroup(question)).formVersions.add(version);
			}
		}
		for (AnswerRef answer : answers) {
			Question question = snapshot.definitions().get(answer.formVersion()).question(answer.questionId()).orElseThrow();
			groups.get(groupKey(question)).answers.add(answer);
		}
		List<SummaryItem> items = new ArrayList<>();
		for (ThemeGroup group : groups.values()) {
			// 전체 요약에서 숫자·날짜만 있는 단답은 건너뛴다. 근거 몇 건으로 "모두 19세"처럼 쓰는 일을 막는다(SPEC 확인 필요).
			if (targetByVersion == null && group.answers.stream().allMatch(answer -> isValueOnly(answer.text()))) {
				continue;
			}
			TargetView target = new TargetView(group.question.questionId(), group.question.questionNote(),
					List.copyOf(group.formVersions));
			items.add(new SummaryItem("themes", target, themesOf(group), null));
		}
		return items;
	}

	private List<ThemeView> themesOf(ThemeGroup group) {
		if (group.answers.isEmpty()) {
			return List.of();
		}
		Map<String, AnswerRef> byResponseId = new LinkedHashMap<>();
		Map<String, String> texts = new LinkedHashMap<>();
		group.answers.forEach(answer -> {
			byResponseId.put(answer.responseId(), answer);
			texts.put(answer.responseId(), answer.text());
		});
		Map<String, String> labels = classifier.classify(group.question.questionNote(), texts);
		List<ThemeAggregator.Theme> themes = ThemeAggregator.aggregate(byResponseId.keySet(), labels,
				properties.maxThemes());
		if (themes.isEmpty()) {
			return List.of();
		}
		List<String> inputs = new ArrayList<>(themes.stream().map(ThemeAggregator.Theme::label).toList());
		List<String> answerIds = List.copyOf(byResponseId.keySet());
		answerIds.forEach(id -> inputs.add(byResponseId.get(id).text()));
		List<float[]> vectors = client.embed("theme_evidence", inputs);
		Map<String, float[]> answerVectors = new LinkedHashMap<>();
		for (int index = 0; index < answerIds.size(); index++) {
			answerVectors.put(key(byResponseId.get(answerIds.get(index))), vectors.get(themes.size() + index));
		}
		// 주제별 요약문은 서로 의존하지 않아 동시에 부른다. 순서는 주제 순서를 유지한다.
		return IntStream.range(0, themes.size()).parallel().mapToObj(index -> {
			ThemeAggregator.Theme theme = themes.get(index);
			List<AnswerRef> pool = theme.responseIds().stream().map(byResponseId::get).toList();
			List<AnswerRef> evidence = topEvidence(vectors.get(index), pool, answerVectors, properties.themeMinScore());
			String text = evidence.isEmpty() ? null : writer.writeTheme(theme.label(), theme.count(), toInputs(evidence));
			return new ThemeView(theme.label(), theme.count(), theme.responseIds(), text, toViews(evidence));
		}).toList();
	}

	private static boolean isValueOnly(String text) {
		return text.trim().matches("[-+]?\\d+(\\.\\d+)?|\\d{4}-\\d{2}-\\d{2}");
	}

	private SummaryView summarizeTopic(String topic, List<AnswerRef> answers, int excluded) {
		TopicView empty = new TopicView(topic, null, List.of());
		if (answers.isEmpty()) {
			return new SummaryView("insufficient_evidence", excluded, List.of(), empty);
		}
		List<String> inputs = new ArrayList<>(List.of(topic));
		answers.forEach(answer -> inputs.add(answer.text()));
		List<float[]> vectors = client.embed("topic_evidence", inputs);
		Map<String, float[]> answerVectors = new LinkedHashMap<>();
		for (int index = 0; index < answers.size(); index++) {
			answerVectors.put(key(answers.get(index)), vectors.get(index + 1));
		}
		List<AnswerRef> evidence = topEvidence(vectors.get(0), answers, answerVectors, properties.topicMinScore());
		if (evidence.isEmpty()) {
			return new SummaryView("insufficient_evidence", excluded, List.of(), empty);
		}
		String text = writer.writeTopic(topic, toInputs(evidence));
		return new SummaryView("ok", excluded, List.of(), new TopicView(topic, text, toViews(evidence)));
	}

	private List<AnswerRef> topEvidence(float[] query, List<AnswerRef> pool, Map<String, float[]> vectors,
			double minScore) {
		record Scored(AnswerRef answer, double score) {
		}
		return pool.stream()
			.map(answer -> new Scored(answer, cosine(query, vectors.get(key(answer)))))
			.filter(scored -> scored.score() >= minScore)
			.sorted(Comparator.comparingDouble(Scored::score).reversed()
				.thenComparing(scored -> scored.answer().responseId()))
			.limit(properties.topK())
			.map(Scored::answer)
			.toList();
	}

	private static String key(AnswerRef answer) {
		return answer.responseId() + ":" + answer.questionId();
	}

	private List<SummaryItem> optionStats(FormSnapshot snapshot, List<Response> matched) {
		List<ResponseRecord> records = snapshot.recordsOf(matched.stream().map(Response::getId).toList());
		return OptionStatistics.combine(snapshot.definitions(), records).stream()
			.map(item -> new SummaryItem("option_stats",
					new TargetView(item.questionId(), item.questionNote(), item.formVersions()), null,
					item.options().stream()
						.map(option -> new OptionView(option.label(), option.count(), option.formVersions()))
						.toList()))
			.toList();
	}

	private static List<SummaryWriter.EvidenceInput> toInputs(List<AnswerRef> evidence) {
		return evidence.stream()
			.map(answer -> new SummaryWriter.EvidenceInput(answer.formVersion(), answer.questionNote(),
					excerpt(answer.text())))
			.toList();
	}

	private static List<EvidenceView> toViews(List<AnswerRef> evidence) {
		return evidence.stream()
			.map(answer -> new EvidenceView(answer.responseId(), answer.formVersion(), answer.questionId(),
					excerpt(answer.text())))
			.toList();
	}

	private static String excerpt(String text) {
		if (text.codePointCount(0, text.length()) <= EXCERPT_LIMIT) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, EXCERPT_LIMIT)) + "…";
	}

	private static double cosine(float[] left, float[] right) {
		double dot = 0;
		double leftNorm = 0;
		double rightNorm = 0;
		for (int index = 0; index < left.length; index++) {
			dot += left[index] * right[index];
			leftNorm += left[index] * left[index];
			rightNorm += right[index] * right[index];
		}
		return (leftNorm == 0 || rightNorm == 0) ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}

	private static String groupKey(Question question) {
		return question.questionId() + "\u0000" + question.questionNote() + "\u0000" + question.questionType();
	}

	private static final class ThemeGroup {

		final Question question;

		final TreeSet<Integer> formVersions = new TreeSet<>();

		final List<AnswerRef> answers = new ArrayList<>();

		ThemeGroup(Question question) {
			this.question = question;
		}

	}

}
