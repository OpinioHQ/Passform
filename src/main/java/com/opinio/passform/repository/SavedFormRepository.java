package com.opinio.passform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.inbox.SavedForm;
import com.opinio.passform.domain.inbox.SavedFormId;

public interface SavedFormRepository extends JpaRepository<SavedForm, SavedFormId> {
}
