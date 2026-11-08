package com.opinio.passform.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.user.Profile;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

	// 생성 시각 내림차순 → id 오름차순(AGENTS 5절).
	List<Profile> findByUserIdOrderByCreatedAtDescIdAsc(UUID userId);

	// 남의 프로필은 없는 것과 같다(AC2).
	Optional<Profile> findByIdAndUserId(UUID id, UUID userId);

}
