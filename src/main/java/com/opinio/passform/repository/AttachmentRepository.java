package com.opinio.passform.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.attachment.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
}
