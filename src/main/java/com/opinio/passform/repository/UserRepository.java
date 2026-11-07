package com.opinio.passform.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.opinio.passform.domain.user.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

	// 같은 사용자의 초안 첫 저장이 동시에 와도 한 번에 하나씩 처리해 폼당 한 건만 만든다(SPEC 5절 206행).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

}
