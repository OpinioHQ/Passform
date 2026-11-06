package com.opinio.passform.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.user.User;
import com.opinio.passform.repository.UserRepository;

@Service
public class AccountService {

	private final UserRepository users;

	private final Clock clock;

	public AccountService(UserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	// 같은 Google 계정은 User 하나다(AC1). 재로그인 때 이름과 이메일을 갱신하지 않는다.
	// 유일 제약 위반에서 복구하려면 저장이 자기 트랜잭션으로 끝나야 하므로 여기에 트랜잭션을 두지 않는다.
	public UUID findOrCreate(String provider, String subject, String email, String name) {
		return users.findByOauthProviderAndOauthSubject(provider, subject)
			.map(User::getId)
			.orElseGet(() -> create(provider, subject, email, name));
	}

	private UUID create(String provider, String subject, String email, String name) {
		try {
			return users.saveAndFlush(new User(name, email, provider, subject, clock.instant())).getId();
		}
		catch (DataIntegrityViolationException e) {
			// 같은 계정의 동시 첫 로그인. 먼저 만들어진 User로 로그인한다.
			return users.findByOauthProviderAndOauthSubject(provider, subject).map(User::getId).orElseThrow(() -> e);
		}
	}

	@Transactional(readOnly = true)
	public AccountView find(UUID userId) {
		User user = load(userId);
		return new AccountView(user.getId(), user.getName(), user.getEmail(), user.isDeadlineReminder());
	}

	@Transactional
	public void updateSettings(SettingsCommand command) {
		User user = load(command.userId());
		if (command.deadlineReminder() != null) {
			user.changeDeadlineReminder(command.deadlineReminder());
		}
	}

	private User load(UUID userId) {
		return users.findById(userId).orElseThrow(CurrentUserService::noLogin);
	}

	public record AccountView(UUID userId, String name, String email, boolean deadlineReminder) {
	}

	// deadlineReminder가 null이면 값을 유지한다.
	public record SettingsCommand(UUID userId, Boolean deadlineReminder) {
	}

}
