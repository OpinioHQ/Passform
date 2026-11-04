package com.opinio.passform.demo.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.opinio.passform.demo.domain.NotFoundException;
import com.opinio.passform.demo.domain.analysis.OptionStatistics;
import com.opinio.passform.domain.analysis.ResponseRecord;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.response.Response;

// GET /forms/{form_id}/results, GET /forms/{form_id}/responses/{response_id} (AC19, AC20, AC62). AI를 쓰지 않는다.
@Service
public class ResultService {

	private final FormSnapshotLoader loader;

	public ResultService(FormSnapshotLoader loader) {
		this.loader = loader;
	}

	public record ResultsView(String title, long responseCount, Combined combined, List<VersionView> versions) {
	}

	public record Combined(List<OptionStatistics.Item> questions) {
	}

	public record VersionView(int formVersion, long responseCount, List<VersionQuestion> questions) {
	}

	public record VersionQuestion(String questionId, String questionNote, Map<String, Integer> counts) {
	}

	public record OwnerResponseView(String responseId, int formVersion, Instant submittedAt, Instant updatedAt,
			List<MeService.AnswerDetail> content, List<Object> attachments) {
	}

	public ResultsView results(String formId, String userId) {
		FormSnapshot snapshot = loader.loadOwned(formId, userId);
		Map<Integer, List<ResponseRecord>> byVersion = snapshot.records()
			.stream()
			.collect(Collectors.groupingBy(ResponseRecord::formVersion, TreeMap::new, Collectors.toList()));
		List<VersionView> versions = new ArrayList<>();
		byVersion.forEach((version, records) -> {
			FormDefinition definition = snapshot.definitions().get(version);
			List<VersionQuestion> questions = OptionStatistics.combine(Map.of(version, definition), records)
				.stream()
				.map(item -> new VersionQuestion(item.questionId(), item.questionNote(), counts(item)))
				.toList();
			versions.add(new VersionView(version, records.size(), questions));
		});
		return new ResultsView(snapshot.latestDefinition().title(), snapshot.records().size(),
				new Combined(OptionStatistics.combine(snapshot.definitions(), snapshot.records())), versions);
	}

	public OwnerResponseView response(String formId, String userId, String responseId) {
		FormSnapshot snapshot = loader.loadOwned(formId, userId);
		Response response = snapshot.submitted()
			.stream()
			.filter(candidate -> candidate.getId().equals(responseId))
			.findFirst()
			.orElseThrow(NotFoundException::new);
		FormDefinition definition = snapshot.definitions().get(response.getFormVersion());
		return new OwnerResponseView(response.getId(), response.getFormVersion(), response.getSubmittedAt(),
				response.getUpdatedAt(), MeService.content(definition, response), List.of());
	}

	private static Map<String, Integer> counts(OptionStatistics.Item item) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		item.options().forEach(option -> counts.put(option.label(), option.count()));
		return counts;
	}

}
