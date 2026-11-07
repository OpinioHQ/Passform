package com.opinio.passform.domain.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.repository.FormRepository;
import com.opinio.passform.repository.FormVersionRepository;
import com.opinio.passform.repository.UserRepository;
import com.opinio.passform.support.DatabaseTest;
import com.opinio.passform.support.SampleForms;
import com.opinio.passform.support.TestData;

import jakarta.persistence.EntityManager;

// getDefinition()이 수정 가능한 Map을 돌려줘도 배포 버전은 DB에서 바뀌지 않는다(SPEC 5절 156행). 폼 삭제 이슈가 쓸 삭제는 막히지 않는다.
@Transactional
class FormVersionImmutabilityTest extends DatabaseTest {

	@Autowired
	private UserRepository users;

	@Autowired
	private FormRepository forms;

	@Autowired
	private FormVersionRepository versions;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbc;

	private FormVersionId saveVersion() {
		UUID ownerId = users.saveAndFlush(TestData.newUser()).getId();
		UUID formId = forms.saveAndFlush(new Form(ownerId, SampleForms.unbranched(), TestData.NOW)).getId();
		FormVersionId id = new FormVersionId(formId, 1);
		versions.saveAndFlush(new FormVersion(formId, 1, SampleForms.unbranched(), TestData.NOW));
		entityManager.clear();
		return id;
	}

	@Test
	void 읽어서_고치고_flush한_뒤_다시_읽어도_처음_값이다() {
		FormVersionId id = saveVersion();

		Map<String, Object> definition = versions.findById(id).orElseThrow().getDefinition();
		definition.put("title", "고친 제목");
		SampleForms.question(definition, 0, 0).put("question_note", "고친 문구");
		entityManager.flush();
		entityManager.clear();

		FormVersion reloaded = versions.findById(id).orElseThrow();
		assertThat(reloaded.getDefinition()).isEqualTo(SampleForms.unbranched());
		assertThat(jdbc.queryForObject("select definition ->> 'title' from form_versions where form_id = ?", String.class,
				id.formId()))
			.isEqualTo("가상 신청");
	}

	@Test
	void 버전을_삭제할_수_있다() {
		FormVersionId id = saveVersion();

		versions.delete(versions.findById(id).orElseThrow());
		entityManager.flush();
		entityManager.clear();

		assertThat(versions.findById(id)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from form_versions where form_id = ?", Integer.class, id.formId()))
			.isZero();
	}

}
