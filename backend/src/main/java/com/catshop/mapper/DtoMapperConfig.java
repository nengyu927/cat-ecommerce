package com.catshop.mapper;

import org.mapstruct.*;

/** New response fields must be mapped explicitly instead of silently becoming null. */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DtoMapperConfig {}
