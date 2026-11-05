package com.opinio.passform.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.inbox.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
