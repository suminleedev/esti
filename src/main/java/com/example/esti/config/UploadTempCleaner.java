package com.example.esti.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 기동할 때 업로드 임시 폴더를 비운다 (F-005).
 *
 * <p>업로드는 받은 파일을 {@code uploads/tmp}에 두고 비동기로 적재한 뒤
 * {@code CatalogImportAsyncService}의 {@code finally}에서 지운다. 그런데 <b>적재 도중 JVM이 죽으면
 * 그 {@code finally}가 돌지 못해</b> 원본 크기 그대로 남는다. QA 중 업로드를 끊어 보니
 * 단가표 한 벌(637 KB)이 그대로 남아 있었다.
 *
 * <p>지우는 시점을 «기동할 때»로 잡은 이유는, 그때가 <b>도는 업로드가 없다고 확신할 수 있는
 * 유일한 순간</b>이기 때문이다. 주기적으로 지우면 처리 중인 파일을 걷어찰 수 있다.
 *
 * <p>실패해도 기동을 막지 않는다 — 임시파일이 남는 것이 앱이 안 뜨는 것보다 낫다.
 */
@Slf4j
@Component
public class UploadTempCleaner implements ApplicationRunner {

    /** {@code VendorCatalogController}가 업로드 파일을 두는 곳과 같은 경로여야 한다. */
    private static final Path TEMP_DIR = Paths.get("uploads", "tmp");

    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(TEMP_DIR)) return;

        try (Stream<Path> files = Files.list(TEMP_DIR)) {
            List<Path> leftovers = files.filter(Files::isRegularFile).toList();
            if (leftovers.isEmpty()) return;

            long bytes = 0;
            int removed = 0;
            for (Path file : leftovers) {
                try {
                    long size = Files.size(file);
                    Files.delete(file);
                    bytes += size;
                    removed++;
                } catch (IOException e) {
                    log.warn("업로드 임시파일을 지우지 못했다: {} ({})", file, e.getMessage());
                }
            }
            if (removed > 0) {
                log.info("업로드 임시파일 {}개 정리 ({} KB) — 적재 중 중단된 흔적이다", removed, bytes / 1024);
            }
        } catch (IOException e) {
            log.warn("업로드 임시 폴더를 읽지 못했다: {} ({})", TEMP_DIR, e.getMessage());
        }
    }
}
