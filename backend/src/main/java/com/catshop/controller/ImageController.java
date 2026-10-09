package com.catshop.controller;

import com.catshop.common.ApiResponse;
import com.catshop.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ImageController {
    private final FileStorage storage;

    @PostMapping(value = "/api/admin/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("url", storage.storeImage(file)));
    }

    @GetMapping("/api/images/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = storage.loadImage(filename);
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)))
                .contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(resource);
    }
}
