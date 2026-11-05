package com.opinio.passform.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.domain.user.Profile;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
