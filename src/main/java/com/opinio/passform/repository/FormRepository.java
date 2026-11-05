package com.opinio.passform.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.form.Form;

public interface FormRepository extends JpaRepository<Form, UUID> {
}
