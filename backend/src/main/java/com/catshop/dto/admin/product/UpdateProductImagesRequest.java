package com.catshop.dto.admin.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.catshop.dto.admin.product.CreateProductRequest.ImageItem;

public record UpdateProductImagesRequest(
        @NotNull @Size(max = 100) List<@NotNull @Positive Long> expectedImageIds,
        @NotNull @Size(max = 100) List<@NotNull @Valid ImageItem> images
) {}
