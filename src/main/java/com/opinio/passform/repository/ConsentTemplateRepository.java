package com.opinio.passform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.template.ConsentTemplate;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplate, String> {
}
