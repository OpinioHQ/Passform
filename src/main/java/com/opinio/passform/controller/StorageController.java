package com.opinio.passform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.service.CurrentUserService;
import com.opinio.passform.service.StorageQuotaService;

// 내 저장 공간 사용량(SPEC 5절 100행).
@RestController
public class StorageController {

	private final StorageQuotaService quota;

	private final CurrentUserService currentUser;

	public StorageController(StorageQuotaService quota, CurrentUserService currentUser) {
		this.quota = quota;
		this.currentUser = currentUser;
	}

	@GetMapping("/me/storage")
	public StorageView storage() {
		return new StorageView(quota.usedBytes(currentUser.requireUserId()), StorageQuotaService.LIMIT_BYTES);
	}

	public record StorageView(long usedBytes, long limitBytes) {
	}

}
