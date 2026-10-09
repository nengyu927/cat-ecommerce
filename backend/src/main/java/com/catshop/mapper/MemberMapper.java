package com.catshop.mapper;

import com.catshop.dto.member.MemberResponse;
import com.catshop.dto.address.AddressResponse;
import com.catshop.entity.*;
import org.mapstruct.Mapper;

@Mapper(config = DtoMapperConfig.class)
public interface MemberMapper {
    MemberResponse member(Member member);
    AddressResponse address(Address address);
}
