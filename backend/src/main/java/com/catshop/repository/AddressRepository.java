package com.catshop.repository;

import com.catshop.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByMemberIdOrderByIsDefaultDescIdDesc(Long memberId);
}
