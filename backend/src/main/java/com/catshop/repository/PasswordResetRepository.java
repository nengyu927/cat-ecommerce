package com.catshop.repository;
import com.catshop.entity.PasswordReset;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDateTime;
public interface PasswordResetRepository extends JpaRepository<PasswordReset,Long> {
 Optional<PasswordReset> findByTokenHash(String tokenHash);
 interface State {String getTokenHash();LocalDateTime getExpiresAt();}
 // Scalar projection rechecks committed state, not the already-loaded entity in the persistence context.
 @Query("select r.tokenHash as tokenHash,r.expiresAt as expiresAt from PasswordReset r where r.memberId=:id")
 Optional<State> currentState(@Param("id") Long id);
}
