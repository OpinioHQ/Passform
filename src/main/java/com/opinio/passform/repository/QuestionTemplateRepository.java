package com.opinio.passform.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.template.QuestionTemplate;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, String> {

	List<QuestionTemplate> findByPurposeAndAudienceInOrderByIdAsc(String purpose, Collection<String> audiences);

}
