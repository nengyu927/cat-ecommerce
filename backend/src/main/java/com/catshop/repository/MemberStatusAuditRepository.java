package com.catshop.repository;
import com.catshop.entity.MemberStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface MemberStatusAuditRepository extends JpaRepository<MemberStatusAudit,Long> {
 Page<MemberStatusAudit> findByMemberId(Long memberId,Pageable pageable);
}
