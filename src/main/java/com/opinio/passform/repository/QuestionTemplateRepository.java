package com.opinio.passform.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.template.QuestionTemplate;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, String> {

	// 고른 목적의 대상·목적 공통 문항, 목적과 상관없는 대상 공통 문항(purpose all), 기본 정보(purpose·audience 모두 all).
	// 순서는 서비스가 정한다.
	@Query("""
			select t from QuestionTemplate t
			where (t.purpose = :purpose and t.audience in (:audience, :common))
			   or (t.purpose = :common and t.audience in (:audience, :common))
			""")
	List<QuestionTemplate> findSuggestions(@Param("purpose") String purpose, @Param("audience") String audience,
			@Param("common") String common);

}
