package com.catshop.repository;

import com.catshop.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @Query("select s.memberId from AuthSession s where s.id=:id")
    Optional<Long> findMemberIdForCredentialLock(@Param("id") String id);
    @Query("select s.id from AuthSession s where s.expiresAt <= :cutoff order by s.expiresAt, s.id")
    List<String> findExpiredIds(@Param("cutoff") LocalDateTime cutoff, Pageable page);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuthSession s where s.id in :ids and s.expiresAt <= :cutoff")
    int deleteExpiredIds(@Param("ids") List<String> ids, @Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> lockById(@Param("id") String id);

    @Modifying
    @Query("update AuthSession s set s.revoked = true where s.memberId = :memberId")
    void revokeMember(@Param("memberId") Long memberId);
}
