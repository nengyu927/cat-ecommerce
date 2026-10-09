package com.catshop.dto.product;


public record ProductImageResponse(Long id, String url, String altText, Integer sortOrder, Boolean isPrimary) {}
