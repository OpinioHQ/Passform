package com.opinio.passform.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.annotation.JsonDeserialize;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.NotificationService;
import com.opinio.passform.service.NotificationService.NotificationView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

// 마감 알림 목록과 읽음 처리(SPEC 5절 201~202행).
@RestController
public class NotificationController {

	private final NotificationService notifications;

	private final CurrentUserService currentUser;

	public NotificationController(NotificationService notifications, CurrentUserService currentUser) {
		this.notifications = notifications;
		this.currentUser = currentUser;
	}

	@GetMapping("/me/notifications")
	public List<NotificationView> list() {
		return notifications.list(currentUser.requireUserId());
	}

	@PatchMapping("/me/notifications/{notificationId}")
	public void markRead(@PathVariable String notificationId, @Valid @RequestBody ReadRequest request) {
		notifications.markRead(currentUser.requireUserId(), notificationId);
	}

	// 본문은 {read: true}만 받는다. 빠지거나 false거나 boolean이 아니면 invalid_request다.
	public record ReadRequest(
			@NotNull @AssertTrue @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean read) {
	}

}
