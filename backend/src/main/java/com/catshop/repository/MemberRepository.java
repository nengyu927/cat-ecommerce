package com.catshop.repository;

import com.catshop.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @org.springframework.data.jpa.repository.Query("""
        select m from Member m where (:status is null or m.status=:status) and (:role is null or m.role=:role)
        and (:keyword is null or lower(m.name) like concat('%',:keyword,'%') escape '!'
        or lower(m.email) like concat('%',:keyword,'%') escape '!'
        or m.phone like concat('%',:keyword,'%') escape '!')
        """)
    org.springframework.data.domain.Page<Member> adminSearch(
        @org.springframework.data.repository.query.Param("keyword") String keyword,
        @org.springframework.data.repository.query.Param("status") String status,
        @org.springframework.data.repository.query.Param("role") String role,org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> lockForPasswordChange(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.email = :email")
    Optional<Member> lockForLogin(@org.springframework.data.repository.query.Param("email") String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> lockForCartCreation(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> lockForWishlistMutation(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> lockForAddressMutation(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}
