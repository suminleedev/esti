package com.example.esti.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기동 시 업로드 임시파일 정리 (F-005).
 *
 * <p>적재 도중 JVM이 죽으면 {@code CatalogImportAsyncService}의 {@code finally}가 돌지 못해
 * {@code uploads/tmp}에 원본이 그대로 남는다. 그걸 다음 기동 때 걷어낸다.
 *
 * <p>정리기가 실제 경로를 보므로 테스트도 그 경로를 쓴다. 대신 <b>표식이 붙은 제 파일만</b>
 * 만들고 끝나면 지운다 — 폴더에 다른 것이 있어도 건드리지 않는다.
 */
class UploadTempCleanerTest {

    private static final Path TEMP_DIR = Paths.get("uploads", "tmp");
    private static final String MARKER = "uploadtempcleanertest-";

    @AfterEach
    void 테스트가_만든_것만_치운다() throws Exception {
        if (!Files.isDirectory(TEMP_DIR)) return;
        try (Stream<Path> files = Files.list(TEMP_DIR)) {
            for (Path p : files.filter(p -> p.getFileName().toString().startsWith(MARKER)).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    @Test
    @DisplayName("중단된 업로드가 남긴 임시파일을 지운다")
    void 남은_임시파일을_지운다() throws Exception {
        Files.createDirectories(TEMP_DIR);
        Path leftover = Files.write(TEMP_DIR.resolve(MARKER + "leftover.xlsx"), new byte[] {1, 2, 3});
        assertThat(leftover).exists();

        new UploadTempCleaner().run(null);

        assertThat(leftover).as("기동 때 걷어내야 한다").doesNotExist();
    }

    @Test
    @DisplayName("폴더 자체는 남긴다 — 다음 업로드가 그대로 쓴다")
    void 폴더는_지우지_않는다() throws Exception {
        Files.createDirectories(TEMP_DIR);
        Files.write(TEMP_DIR.resolve(MARKER + "x.xls"), new byte[] {9});

        new UploadTempCleaner().run(null);

        assertThat(TEMP_DIR).isDirectory();
    }

    @Test
    @DisplayName("지울 것이 없어도 조용히 끝난다 — 기동을 막지 않는다")
    void 빈_폴더에서도_예외가_없다() throws Exception {
        Files.createDirectories(TEMP_DIR);

        new UploadTempCleaner().run(null);   // 예외 없이 끝나면 통과

        assertThat(TEMP_DIR).isDirectory();
    }
}
