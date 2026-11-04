package com.opinio.passform.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.demo.domain.response.SavedForm;

public interface SavedFormRepository extends JpaRepository<SavedForm, String> {

	List<SavedForm> findByUserId(String userId);

}
