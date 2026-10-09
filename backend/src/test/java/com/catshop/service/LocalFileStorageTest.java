package com.catshop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class LocalFileStorageTest {
    @TempDir Path directory;

    @Test void savesValidImageUnderGeneratedNameAndLoadsIt() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        LocalFileStorage storage = new LocalFileStorage(directory.toString());
        String url = storage.storeImage(new MockMultipartFile("file", "../../evil.png", "image/png", bytes.toByteArray()));
        assertThat(url).matches("/api/images/[a-f0-9]{32}\\.png");
        assertThat(storage.loadImage(url.substring(url.lastIndexOf('/') + 1)).getContentAsByteArray()).isEqualTo(bytes.toByteArray());
    }

    @Test void rejectsEmptyInvalidAndTraversalInputs() {
        LocalFileStorage storage = new LocalFileStorage(directory.toString());
        assertThatThrownBy(() -> storage.storeImage(new MockMultipartFile("file", new byte[0]))).hasMessageContaining("5 MB");
        assertThatThrownBy(() -> storage.storeImage(new MockMultipartFile("file", "fake.png", "image/png", "<script/>".getBytes()))).hasMessageContaining("PNG");
        assertThatThrownBy(() -> storage.loadImage("../secret.txt")).hasMessageContaining("找不到圖片");
    }
}
