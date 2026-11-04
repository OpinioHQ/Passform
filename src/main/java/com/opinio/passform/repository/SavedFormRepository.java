package com.opinio.passform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.response.SavedForm;

public interface SavedFormRepository extends JpaRepository<SavedForm, String> {

	List<SavedForm> findByUserId(String userId);

}
