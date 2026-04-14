package com.example.esti.crawler.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class ImageDownloadService {

    private final Path rootDir;

    public ImageDownloadService(@Value("${app.crawler.image-dir}") String rootDir) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    public DownloadResult download(String sourceUrl, String preferredFileName) throws Exception {
        Files.createDirectories(rootDir);

        String fileName = sanitize(preferredFileName);
        if (!hasImageExtension(fileName)) {
            fileName += ".jpg";
        }

        Path target = rootDir.resolve(fileName);
        if (Files.exists(target)) {
            fileName = UUID.randomUUID() + "_" + fileName;
            target = rootDir.resolve(fileName);
        }

        try (InputStream in = new URL(sourceUrl).openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return new DownloadResult(
                target.toAbsolutePath().toString(),
                "/uploads/product-images/" + fileName
        );
    }

    private boolean hasImageExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }

    public record DownloadResult(String absolutePath, String relativePath) {
    }
}