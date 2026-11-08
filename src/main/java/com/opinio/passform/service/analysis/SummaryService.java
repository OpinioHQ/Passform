package com.opinio.passform.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.opinio.passform.domain.analysis.ContactMasker;
import com.opinio.passform.domain.analysis.RagSettings;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.domain.analysis.SummaryUnavailableVersion;
import com.opinio.passform.domain.analysis.ThemeTally;
import com.opinio.passform.domain.form.DefinitionQuestion;
import com.opinio.passform.domain.form.VersionQuestions;
import com.opinio.passform.domain.form.VersionQuestions.Question;
import com.opinio.passform.domain.response.Response;
import com.opinio.passform.domain.result.ResultStatistics;
import com.opinio.passform.llm.EmbeddingClient;
import com.opinio.passform.llm.SummaryWriter;
import com.opinio.passform.llm.SummaryWriter.EvidenceInput;
import com.opinio.passform.llm.ThemeClassifier;
import com.opinio.passform.service.analysis.SummaryView.EvidenceView;
import com.opinio.passform.service.analysis.SummaryView.OptionStatsItem;
import com.opinio.passform.service.analysis.SummaryView.OptionView;
import com.opinio.passform.service.analysis.SummaryView.SummaryItem;
import com.opinio.passform.service.analysis.SummaryView.TargetView;
import com.opinio.passform.service.analysis.SummaryView.ThemeView;
import com.opinio.passform.service.analysis.SummaryView.ThemesItem;
import com.opinio.passform.service.analysis.SummaryView.TopicView;

// SPEC 316~332행의 요약. 대상 응답·개수·순서는 코드가 정하고 LLM은 라벨과 문장만 쓴다(AGENTS 3절 3항).
// 응답은 AnalysisService가 읽은 일치 응답만 받는다. LLM을 부르는 동안 DB 연결을 잡지 않도록 트랜잭션을 걸지 않는다.
@Service
public class SummaryService {

	private static final Pattern VALUE_ONLY = Pattern
		.compile("[-+]?\\d[\\d,]*(\\.\\d+)?|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}\\.?");

	private final ThemeClassifier classifier;

	private final SummaryWriter writer;

	private final EmbeddingClient embeddings;

	private final RagSettings settings;

	public SummaryService(ThemeClassifier classifier, SummaryWriter writer, EmbeddingClient embeddings,
			RagSettings settings) {
		this.classifier = classifier;
		this.writer = writer;
		this.embeddings = embeddings;
		this.settings = settings;
	}

	public record Outcome(SummaryView summary, List<SummaryUnavailableVersion> unavailableVersions) {
	}

	// 동의 응답의 대상 문항 답 하나. text는 가린 값이고, 외부로는 이 값만 나간다.
	private record AnswerChunk(String responseId, int formVersion, String questionId, String questionNote, String text,
			boolean valueOnly) {
	}

	private static final class QuestionGroup {

		private final Question question;

		private final List<Integer> formVersions = new ArrayList<>();

		private final List<AnswerChunk> answers = new ArrayList<>();

		private QuestionGroup(Question question) {
			this.question = question;
		}

		private TargetView target() {
			return new TargetView(question.questionId(), question.questionNote(), List.copyOf(formVersions));
		}

	}

	private record GroupKey(String questionId, String questionNote, String questionType) {
	}

	private record Scored(AnswerChunk answer, double score) {
	}

	// matched는 조건에 일치한 제출 응답 전체이고 response_id 오름차순이다.
	public Outcome summarize(RequestContext context, List<VersionQuestions> versions, List<Response> matched) {
		List<SummaryUnavailableVersion> unavailable = unavailableVersions(context, versions, matched);
		List<Response> consented = matched.stream().filter(Response::isAiSummaryAgreed).toList();
		int excluded = matched.size() - consented.size();
		TopicView emptyTopic = context.topic() == null ? null : new TopicView(context.topic(), null, List.of());
		// 판정 순서는 SPEC 329행이다. 상한은 LLM·임베딩 호출 전에 본다.
		if (matched.isEmpty()) {
			return new Outcome(new SummaryView("no_matches", excluded, List.of(), emptyTopic), unavailable);
		}
		if (consented.isEmpty()) {
			return new Outcome(new SummaryView("no_consented_responses", excluded, List.of(), emptyTopic), unavailable);
		}
		if (consented.size() > settings.maxResponsesPerRequest()) {
			return new Outcome(new SummaryView("too_many_responses", excluded, List.of(), emptyTopic), unavailable);
		}

		List<QuestionGroup> groups = groups(context, versions, matched, consented);
		if (context.topic() != null) {
			return new Outcome(topicSummary(context.topic(), groups, excluded), unavailable);
		}
		List<SummaryItem> items = new ArrayList<>(themeItems(context, groups));
		if (context.target() == null) {
			items.addAll(optionStats(versions, matched));
		}
		return new Outcome(new SummaryView("ok", excluded, List.copyOf(items), null), unavailable);
	}

	// 사람이 정한 것(2026-11-08): 일치 응답이 1건 이상인 버전만 넣는다.
	private static List<SummaryUnavailableVersion> unavailableVersions(RequestContext context,
			List<VersionQuestions> versions, List<Response> matched) {
		if (context.target() == null) {
			return List.of();
		}
		Map<Integer, Integer> matchedByVersion = new TreeMap<>();
		matched.forEach(response -> matchedByVersion.merge(response.getFormVersion(), 1, Integer::sum));
		List<SummaryUnavailableVersion> unavailable = new ArrayList<>();
		for (VersionQuestions version : versions) {
			boolean hasTarget = version.questions().stream().anyMatch(question -> isTarget(context, question));
			int count = matchedByVersion.getOrDefault(version.formVersion(), 0);
			if (!hasTarget && count > 0) {
				unavailable.add(new SummaryUnavailableVersion(version.formVersion(), count));
			}
		}
		return unavailable;
	}

	private static boolean isTarget(RequestContext context, Question question) {
		return question.questionNote().equals(context.target().strip());
	}

	// 통합 보기처럼 question_id·문구·유형이 같은 문항끼리 묶는다. 일치 응답이 있는 버전만, 낮은 버전부터 처음 나온 순이다.
	private static List<QuestionGroup> groups(RequestContext context, List<VersionQuestions> versions,
			List<Response> matched, List<Response> consented) {
		TreeSet<Integer> matchedVersions = new TreeSet<>();
		matched.forEach(response -> matchedVersions.add(response.getFormVersion()));
		Map<GroupKey, QuestionGroup> groups = new LinkedHashMap<>();
		Map<Integer, List<Question>> questionsByVersion = new TreeMap<>();
		for (VersionQuestions version : versions) {
			if (!matchedVersions.contains(version.formVersion())) {
				continue;
			}
			List<Question> questions = version.questions()
				.stream()
				.filter(Question::isSummarizable)
				.filter(question -> context.target() == null || isTarget(context, question))
				.toList();
			questionsByVersion.put(version.formVersion(), questions);
			questions.forEach(
					question -> groups.computeIfAbsent(keyOf(question), key -> new QuestionGroup(question)).formVersions
						.add(version.formVersion()));
		}
		for (Response response : consented) {
			ResponseRecord record = response.toResponseRecord();
			for (Question question : questionsByVersion.getOrDefault(record.formVersion(), List.of())) {
				List<String> values = record.answers().getOrDefault(question.questionId(), List.of());
				if (!values.isEmpty()) {
					String raw = String.join(" ", values);
					groups.get(keyOf(question)).answers
						.add(new AnswerChunk(record.responseId(), record.formVersion(), question.questionId(),
								question.questionNote(), ContactMasker.mask(raw), isValueOnly(question, raw)));
				}
			}
		}
		return List.copyOf(groups.values());
	}

	private static GroupKey keyOf(Question question) {
		return new GroupKey(question.questionId(), question.questionNote(), question.questionType());
	}

	private static boolean isValueOnly(Question question, String raw) {
		return "short_answer".equals(question.questionType()) && VALUE_ONLY.matcher(raw.strip()).matches();
	}

	// 사람이 정한 것(2026-11-08): 유형 A는 동의 답이 없는 문항과 답이 전부 숫자·날짜인 단답 문항을 뺀다(문항 단위).
	// 유형 B·C는 직접 고른 문항이므로 동의 답이 없어도 themes: []로 둔다.
	private List<ThemesItem> themeItems(RequestContext context, List<QuestionGroup> groups) {
		List<ThemesItem> items = new ArrayList<>();
		for (QuestionGroup group : groups) {
			boolean skip = group.answers.isEmpty() || group.answers.stream().allMatch(AnswerChunk::valueOnly);
			if (context.target() == null && skip) {
				continue;
			}
			items.add(ThemesItem.of(group.target(), group.answers.isEmpty() ? List.of() : themes(group)));
		}
		return items;
	}

	private List<ThemeView> themes(QuestionGroup group) {
		Map<String, AnswerChunk> byResponseId = new LinkedHashMap<>();
		Map<String, String> texts = new LinkedHashMap<>();
		for (AnswerChunk answer : group.answers) {
			byResponseId.put(answer.responseId(), answer);
			texts.put(answer.responseId(), answer.text());
		}
		Map<String, String> labels = classifier.classify(group.question.questionNote(), texts);
		List<ThemeTally.Theme> themes = ThemeTally.tally(labels, settings.maxThemes());

		List<String> inputs = new ArrayList<>(themes.stream().map(ThemeTally.Theme::label).toList());
		inputs.addAll(texts.values());
		List<double[]> vectors = embeddings.embed("theme_evidence", inputs);
		Map<String, double[]> answerVectors = new LinkedHashMap<>();
		int position = themes.size();
		for (String responseId : texts.keySet()) {
			answerVectors.put(responseId, vectors.get(position++));
		}

		List<ThemeView> views = new ArrayList<>();
		for (int index = 0; index < themes.size(); index++) {
			ThemeTally.Theme theme = themes.get(index);
			// 근거는 그 주제의 응답 안에서만 고른다(AC88).
			List<Scored> scored = new ArrayList<>();
			for (String responseId : theme.responseIds()) {
				scored.add(new Scored(byResponseId.get(responseId),
						cosine(vectors.get(index), answerVectors.get(responseId))));
			}
			List<AnswerChunk> evidence = top(scored, settings.themeTopK(), settings.themeMinScore());
			String text = evidence.isEmpty() ? null
					: writer.writeTheme(theme.label(), theme.count(), evidenceInputs(evidence));
			views.add(new ThemeView(theme.label(), theme.count(), theme.responseIds(), text, evidenceViews(evidence)));
		}
		return views;
	}

	// 유형 D: 동의한 일치 응답의 요약 가능 문항 답(target이 있으면 그 문항) 안에서만 찾는다. 개수를 내지 않는다(AC89).
	private SummaryView topicSummary(String topic, List<QuestionGroup> groups, int excluded) {
		TopicView empty = new TopicView(topic, null, List.of());
		List<AnswerChunk> answers = groups.stream().flatMap(group -> group.answers.stream()).toList();
		if (answers.isEmpty()) {
			return new SummaryView("insufficient_evidence", excluded, List.of(), empty);
		}
		List<String> inputs = new ArrayList<>(List.of(topic));
		answers.forEach(answer -> inputs.add(answer.text()));
		List<double[]> vectors = embeddings.embed("topic_evidence", inputs);
		List<Scored> scored = new ArrayList<>();
		for (int index = 0; index < answers.size(); index++) {
			scored.add(new Scored(answers.get(index), cosine(vectors.get(0), vectors.get(index + 1))));
		}
		List<AnswerChunk> evidence = top(scored, settings.topicTopK(), settings.topicMinScore());
		if (evidence.isEmpty()) {
			return new SummaryView("insufficient_evidence", excluded, List.of(), empty);
		}
		String text = writer.writeTopic(topic, evidenceInputs(evidence));
		return new SummaryView("ok", excluded, List.of(), new TopicView(topic, text, evidenceViews(evidence)));
	}

	private static List<AnswerChunk> top(List<Scored> scored, int topK, double minScore) {
		return scored.stream()
			.filter(entry -> entry.score() >= minScore)
			.sorted(Comparator.comparingDouble(Scored::score)
				.reversed()
				.thenComparing(entry -> entry.answer().responseId())
				.thenComparing(entry -> entry.answer().questionId()))
			.limit(topK)
			.map(Scored::answer)
			.toList();
	}

	private List<EvidenceInput> evidenceInputs(List<AnswerChunk> evidence) {
		return evidence.stream()
			.map(answer -> new EvidenceInput(answer.formVersion(), answer.questionNote(), excerpt(answer.text())))
			.toList();
	}

	private List<EvidenceView> evidenceViews(List<AnswerChunk> evidence) {
		return evidence.stream()
			.map(answer -> new EvidenceView(answer.responseId(), answer.formVersion(), answer.questionId(),
					excerpt(answer.text())))
			.toList();
	}

	// 사람이 정한 것(2026-11-08): 발췌는 가린 문장이고, 상한을 넘으면 자르고 끝에 …를 붙인다.
	private String excerpt(String text) {
		if (text.codePointCount(0, text.length()) <= settings.excerptMaxChars()) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, settings.excerptMaxChars())) + "…";
	}

	// 일치 응답 전체를 코드로 센다. 외부로 보내지 않으므로 동의와 무관하다(SPEC 325행).
	// 사람이 정한 것(2026-11-08): 개인정보 문항은 AI 요약 대상이 아니므로 선택형이어도 넣지 않는다.
	private static List<OptionStatsItem> optionStats(List<VersionQuestions> versions, List<Response> matched) {
		Map<Integer, List<DefinitionQuestion>> definitions = new TreeMap<>();
		for (VersionQuestions version : versions) {
			definitions.put(version.formVersion(),
					version.questions()
						.stream()
						.filter(question -> !question.personal())
						.map(question -> new DefinitionQuestion(question.questionId(), question.questionNote(),
								question.questionType(), question.options()))
						.toList());
		}
		List<ResponseRecord> records = matched.stream().map(Response::toResponseRecord).toList();
		return ResultStatistics.calculate(definitions, records)
			.combined()
			.questions()
			.stream()
			.map(question -> OptionStatsItem.of(
					new TargetView(question.questionId(), question.questionNote(), List.copyOf(question.formVersions())),
					question.options()
						.stream()
						.map(option -> new OptionView(option.label(), option.count(), List.copyOf(option.formVersions())))
						.toList()))
			.toList();
	}

	private static double cosine(double[] left, double[] right) {
		double dot = 0;
		double leftNorm = 0;
		double rightNorm = 0;
		for (int index = 0; index < Math.min(left.length, right.length); index++) {
			dot += left[index] * right[index];
			leftNorm += left[index] * left[index];
			rightNorm += right[index] * right[index];
		}
		return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}

}
