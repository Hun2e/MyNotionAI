package com.mynotionai.repository;

import com.mynotionai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderUserId(User.Provider provider, String providerUserId);
    boolean existsByEmail(String email);
}
