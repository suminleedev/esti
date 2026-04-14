package com.example.esti.util;

import java.nio.file.Path;

public class ImagePathUtils {

    private ImagePathUtils() {}

    public static Path toFilePath(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl.replace("\\", "/");

        if (normalized.startsWith("/uploads/")) {
            return Path.of("." + normalized);
        }

        if (normalized.startsWith("uploads/")) {
            return Path.of("./" + normalized);
        }

        return Path.of(normalized);
    }
}
