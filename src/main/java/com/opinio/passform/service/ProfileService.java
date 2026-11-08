package com.opinio.passform.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;
import com.opinio.passform.domain.user.Profile;
import com.opinio.passform.repository.ProfileRepository;

// 다중 프로필(SPEC 5절 102~105행). 소유자는 호출한 쪽이 CurrentUserService로 얻은 사용자다. 프로필 값을 로그에 남기지 않는다.
@Service
public class ProfileService {

	private final ProfileRepository profiles;

	private final Clock clock;

	public ProfileService(ProfileRepository profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ProfileView> list(UUID userId) {
		return profiles.findByUserIdOrderByCreatedAtDescIdAsc(userId).stream().map(ProfileView::of).toList();
	}

	@Transactional
	public UUID create(ProfileCommand command) {
		Profile profile = Profile.create(command.userId(), command.label(), command.personalInfo(), clock.instant());
		return profiles.save(profile).getId();
	}

	@Transactional
	public void update(String profileId, ProfileCommand command) {
		owned(profileId, command.userId()).replace(command.label(), command.personalInfo());
	}

	// 행을 지워 personal_info도 함께 지운다. 제출 응답은 프로필을 참조하지 않아 바뀌지 않는다(AC5).
	@Transactional
	public void delete(UUID userId, String profileId) {
		profiles.delete(owned(profileId, userId));
	}

	private Profile owned(String profileId, UUID userId) {
		return profiles.findByIdAndUserId(FormService.parseId(profileId), userId)
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
	}

	public record ProfileCommand(UUID userId, Object label, Map<String, Object> personalInfo) {
	}

	public record ProfileView(UUID profileId, String label, Map<String, String> personalInfo) {

		static ProfileView of(Profile profile) {
			return new ProfileView(profile.getId(), profile.getLabel(), profile.getPersonalInfo());
		}

	}

}
