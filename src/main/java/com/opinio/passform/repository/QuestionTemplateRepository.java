package com.opinio.passform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.template.QuestionTemplate;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, String> {
}
