package com.opinio.passform.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.demo.domain.form.ConsentTemplate;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplate, String> {

}
