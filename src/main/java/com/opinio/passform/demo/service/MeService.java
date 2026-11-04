package com.opinio.passform.demo.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.demo.domain.NotFoundException;
import com.opinio.passform.domain.analysis.ResponseStatus;
import com.opinio.passform.demo.domain.form.Form;
import com.opinio.passform.demo.domain.form.FormDefinition;
import com.opinio.passform.demo.domain.form.FormJson;
import com.opinio.passform.demo.domain.form.FormVersion;
import com.opinio.passform.demo.domain.form.Question;
import com.opinio.passform.demo.domain.response.Response;
import com.opinio.passform.demo.domain.response.SavedForm;
import com.opinio.passform.demo.domain.user.User;
import com.opinio.passform.demo.repository.FormRepository;
import com.opinio.passform.demo.repository.FormVersionRepository;
import com.opinio.passform.demo.repository.ResponseRepository;
import com.opinio.passform.demo.repository.SavedFormRepository;
import com.opinio.passform.demo.repository.UserRepository;

// 개인 리소스는 해당 사용자의 소유 관계로만 접근한다(AGENTS 3절 1항).
@Service
public class MeService {

	private final UserRepository users;

	private final FormRepository forms;

	private final FormVersionRepository versions;

	private final ResponseRepository responses;

	private final SavedFormRepository savedForms;

	private final Clock clock;

	private final String shareBaseUrl;

	public MeService(UserRepository users, FormRepository forms, FormVersionRepository versions,
			ResponseRepository responses, SavedFormRepository savedForms, Clock clock,
			@Value("${passform.share-base-url:http://localhost:5173}") String shareBaseUrl) {
		this.users = users;
		this.forms = forms;
		this.versions = versions;
		this.responses = responses;
		this.savedForms = savedForms;
		this.clock = clock;
		this.shareBaseUrl = shareBaseUrl;
	}

	public record UserView(String userId, String name, String email, String uiMode, boolean deadlineReminder) {
	}

	public record SavedFormView(String formId, String title, Instant deadline, String state, boolean hasDraft) {
	}

	public record CreatedFormView(String formId, String title, String status, Instant deadline, String shareUrl,
			long responseCount) {
	}

	public record HistoryItem(String responseId, String formId, String formTitle, int formVersion, Instant submittedAt,
			Instant updatedAt, boolean editable, boolean cancellable) {
	}

	public record AnswerDetail(String questionId, String questionNote, Object value) {
	}

	public record ResponseDetail(String responseId, String formTitle, int formVersion, List<AnswerDetail> content,
			List<Object> attachments, Instant submittedAt, Instant updatedAt, boolean editable, boolean cancellable) {
	}

	@Transactional(readOnly = true)
	public UserView me(String userId) {
		User user = users.findById(userId).orElseThrow(NotFoundException::new);
		return toView(user);
	}

	@Transactional
	public UserView changeUiMode(String userId, String uiMode) {
		User user = users.findById(userId).orElseThrow(NotFoundException::new);
		if ("respondent".equals(uiMode) || "creator".equals(uiMode)) {
			user.changeUiMode(uiMode);
		}
		return toView(user);
	}

	@Transactional
	public boolean saveForm(String userId, String formId) {
		Form form = forms.findById(formId).orElseThrow(NotFoundException::new);
		if (!form.isPublished() && !form.isOwnedBy(userId)) {
			throw new NotFoundException();
		}
		String id = SavedForm.idOf(userId, formId);
		if (savedForms.existsById(id)) {
			return false;
		}
		savedForms.save(new SavedForm(userId, formId, SavedForm.SAVED));
		return true;
	}

	// 미제출(마감 임박순, deadline 없음은 뒤) → 제출(submitted_at 내림차순), 같으면 form_id 오름차순(AGENTS 5절).
	@Transactional(readOnly = true)
	public List<SavedFormView> myForms(String userId) {
		Map<String, Instant> lastSubmitted = new HashMap<>();
		for (Response response : responses.findByUserIdAndStatusOrderBySubmittedAtDescIdAsc(userId,
				ResponseStatus.SUBMITTED)) {
			lastSubmitted.putIfAbsent(response.getFormId(), response.getSubmittedAt());
		}
		List<Row> rows = new ArrayList<>();
		for (SavedForm saved : savedForms.findByUserId(userId)) {
			forms.findById(saved.getFormId()).ifPresent(form -> rows
				.add(new Row(form, saved.getState(), lastSubmitted.get(form.getId()))));
		}
		Comparator<Row> pending = Comparator.comparing((Row row) -> row.form().getDeadline(),
				Comparator.nullsLast(Comparator.naturalOrder()));
		Comparator<Row> submitted = Comparator.comparing(Row::submittedAt,
				Comparator.nullsLast(Comparator.<Instant>naturalOrder().reversed()));
		return rows.stream()
			.sorted(Comparator.comparing((Row row) -> SavedForm.SUBMITTED.equals(row.state()))
				.thenComparing((left, right) -> SavedForm.SUBMITTED.equals(left.state())
						? submitted.compare(left, right) : pending.compare(left, right))
				.thenComparing(row -> row.form().getId()))
			.map(row -> new SavedFormView(row.form().getId(), row.form().getTitle(), row.form().getDeadline(),
					row.state(), false))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CreatedFormView> createdForms(String userId) {
		return forms.findByOwnerIdOrderByCreatedAtDescIdAsc(userId)
			.stream()
			.map(form -> new CreatedFormView(form.getId(), form.getTitle(), form.getStatus().name().toLowerCase(),
					form.getDeadline(), shareBaseUrl + "/f/" + form.getSlug(),
					responses.countByFormIdAndStatus(form.getId(), ResponseStatus.SUBMITTED)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<HistoryItem> history(String userId) {
		Instant now = clock.instant();
		return responses.findByUserIdAndStatusOrderBySubmittedAtDescIdAsc(userId, ResponseStatus.SUBMITTED)
			.stream()
			.map(response -> {
				Form form = forms.findById(response.getFormId()).orElseThrow(NotFoundException::new);
				return new HistoryItem(response.getId(), form.getId(),
						definitionOf(response).title(), response.getFormVersion(), response.getSubmittedAt(),
						response.getUpdatedAt(), form.isAcceptingAt(now) && form.isEditable(),
						form.isAcceptingAt(now) && form.isCancellable());
			})
			.toList();
	}

	// 문항·문구는 제출 버전 기준이다(AC15).
	@Transactional(readOnly = true)
	public ResponseDetail historyDetail(String userId, String responseId) {
		Response response = responses.findById(responseId).orElseThrow(NotFoundException::new);
		if (!userId.equals(response.getUserId()) || response.getStatus() != ResponseStatus.SUBMITTED) {
			throw new NotFoundException();
		}
		Form form = forms.findById(response.getFormId()).orElseThrow(NotFoundException::new);
		Instant now = clock.instant();
		FormDefinition definition = definitionOf(response);
		return new ResponseDetail(response.getId(), definition.title(), response.getFormVersion(),
				content(definition, response), List.of(), response.getSubmittedAt(), response.getUpdatedAt(),
				form.isAcceptingAt(now) && form.isEditable(), form.isAcceptingAt(now) && form.isCancellable());
	}

	static List<AnswerDetail> content(FormDefinition definition, Response response) {
		Map<String, List<String>> answers = FormJson.parseAnswers(response.getAnswersJson());
		List<AnswerDetail> content = new ArrayList<>();
		for (Question question : definition.questions()) {
			List<String> values = answers.get(question.questionId());
			if (values != null) {
				content.add(new AnswerDetail(question.questionId(), question.questionNote(),
						FormJson.displayValue(question, values)));
			}
		}
		return content;
	}

	private FormDefinition definitionOf(Response response) {
		return versions.findByFormIdAndVersion(response.getFormId(), response.getFormVersion())
			.map(FormVersion::definition)
			.orElseThrow(NotFoundException::new);
	}

	private static UserView toView(User user) {
		return new UserView(user.getId(), user.getName(), user.getEmail(), user.getUiMode(), user.isDeadlineReminder());
	}

	private record Row(Form form, String state, Instant submittedAt) {
	}

}
