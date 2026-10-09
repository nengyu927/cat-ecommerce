package com.catshop.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {
    String storeImage(MultipartFile file);
    Resource loadImage(String filename);
}
