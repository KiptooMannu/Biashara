package com.biashara.iam.repository;

import com.biashara.iam.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
}
