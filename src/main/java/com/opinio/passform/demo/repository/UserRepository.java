package com.opinio.passform.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opinio.passform.demo.domain.user.User;

public interface UserRepository extends JpaRepository<User, String> {

}
