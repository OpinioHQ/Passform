package com.opinio.passform.domain.form;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

// 배포된 폼 정의(불변). 변경 메서드를 두지 않는다.
// @Immutable: getDefinition()은 수정 가능한 Map을 돌려주므로, 읽은 값을 바꿔도 flush가 DB에 쓰지 않게 한다.
// Persistable: 키를 직접 정하므로, 없으면 save()가 같은 버전을 거부하지 않고 덮어쓴다.
@Entity
@Immutable
@Table(name = "form_versions")
@IdClass(FormVersionId.class)
public class FormVersion implements Persistable<FormVersionId> {

	@Id
	@Column(name = "form_id")
	private UUID formId;

	@Id
	@Column(name = "version")
	private int version;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> definition;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Transient
	private boolean newEntity = true;

	protected FormVersion() {
	}

	public FormVersion(UUID formId, int version, Map<String, Object> definition, Instant createdAt) {
		this.formId = formId;
		this.version = version;
		this.definition = definition;
		this.createdAt = createdAt;
	}

	@Override
	public FormVersionId getId() {
		return new FormVersionId(formId, version);
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		newEntity = false;
	}

	public UUID getFormId() {
		return formId;
	}

	public int getVersion() {
		return version;
	}

	public Map<String, Object> getDefinition() {
		return definition;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
