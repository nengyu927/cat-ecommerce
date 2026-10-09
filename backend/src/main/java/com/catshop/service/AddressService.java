package com.catshop.service;

import com.catshop.dto.address.AddressRequest;
import com.catshop.dto.address.AddressResponse;
import com.catshop.entity.Address;
import com.catshop.entity.Member;
import com.catshop.exception.BusinessException;
import com.catshop.repository.AddressRepository;
import com.catshop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final com.catshop.mapper.MemberMapper memberMapper;

    private final AddressRepository addressRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public AddressResponse detail(Long memberId, Long addressId) {
        return memberMapper.address(findOwned(memberId, addressId));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long memberId) {
        return addressRepository.findByMemberIdOrderByIsDefaultDescIdDesc(memberId)
                .stream().map(memberMapper::address).toList();
    }

    @Transactional
    public AddressResponse create(Long memberId, AddressRequest req) {
        Member member = memberRepository.lockForAddressMutation(memberId)
                .orElseThrow(() -> BusinessException.notFound("找不到會員"));
        Address address = Address.builder()
                .member(member)
                .receiverName(req.receiverName())
                .phone(req.phone())
                .city(req.city())
                .district(req.district())
                .line1(req.line1())
                .line2(req.line2())
                .postalCode(req.postalCode())
                .isDefault(Boolean.TRUE.equals(req.isDefault()))
                .build();
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefault(memberId);
        }
        return memberMapper.address(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long memberId, Long addressId, AddressRequest req) {
        lockMember(memberId);
        Address address = findOwned(memberId, addressId);
        address.setReceiverName(req.receiverName());
        address.setPhone(req.phone());
        address.setCity(req.city());
        address.setDistrict(req.district());
        address.setLine1(req.line1());
        address.setLine2(req.line2());
        address.setPostalCode(req.postalCode());
        if (Boolean.TRUE.equals(req.isDefault())) {
            clearDefault(memberId);
            address.setIsDefault(true);
        } else if (Boolean.FALSE.equals(req.isDefault())) {
            address.setIsDefault(false);
        }
        return memberMapper.address(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        lockMember(memberId);
        addressRepository.delete(findOwned(memberId, addressId));
    }

    private void lockMember(Long memberId) {
        memberRepository.lockForAddressMutation(memberId)
                .orElseThrow(() -> BusinessException.notFound("找不到地址"));
    }

    private void clearDefault(Long memberId) {
        addressRepository.findByMemberIdOrderByIsDefaultDescIdDesc(memberId).forEach(a -> {
            if (Boolean.TRUE.equals(a.getIsDefault())) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        });
    }

    private Address findOwned(Long memberId, Long addressId) {
        if (addressId == null || addressId < 1) throw BusinessException.badRequest("地址編號須為正整數");
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> BusinessException.notFound("找不到地址"));
        if (!address.getMember().getId().equals(memberId)) {
            throw BusinessException.notFound("找不到地址");
        }
        return address;
    }
}
