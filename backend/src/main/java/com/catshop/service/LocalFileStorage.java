package com.catshop.service;

import com.catshop.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorage implements FileStorage {
    private final Path directory;
    public LocalFileStorage(@Value("${app.storage.directory:./uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String storeImage(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw BusinessException.badRequest("圖片必須介於 1 byte 至 5 MB");
        try {
            byte[] bytes = file.getBytes();
            String extension;
            // Detect file contents; never use the supplied filename or MIME type for storage paths.
            if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) extension = ".png";
            else if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) extension = ".jpg";
            else throw BusinessException.badRequest("僅接受 PNG 或 JPEG 圖片");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw BusinessException.badRequest("無法讀取圖片");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 16000000) throw BusinessException.badRequest("圖片像素不可超過 1600 萬");
                    reader.read(0);
                } finally { reader.dispose(); }
            }
            Files.createDirectories(directory);
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Files.write(directory.resolve(filename), bytes, StandardOpenOption.CREATE_NEW);
            return "/api/images/" + filename;
        } catch (IOException ex) {
            throw BusinessException.badRequest("圖片無法讀取或儲存");
        }
    }

    @Override
    public Resource loadImage(String filename) {
        if (!filename.matches("[a-f0-9]{32}\\.(png|jpg)")) throw BusinessException.notFound("找不到圖片");
        Path path = directory.resolve(filename).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) throw BusinessException.notFound("找不到圖片");
        return new FileSystemResource(path);
    }
}
