package com.example.esti.service;

import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.VendorCatalogImporter.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 카탈로그 엑셀 업로드의 <b>비동기 오케스트레이션</b> — 진행률 표시와 임시파일 정리를 담당한다.
 *
 * <p>실제 적재(파싱 + DB upsert)는 {@link VendorCatalogImporter}가 자신의 트랜잭션 안에서 수행한다.
 * 여기서 {@code @Transactional}을 붙이면 안 된다(B-1): 아래 try/catch가 트랜잭션 경계 <b>안에서</b>
 * 예외를 삼켜 롤백 마킹이 되지 않고 부분 적재분이 커밋됐던 것이 원래 버그다.
 * 지금은 예외가 트랜잭션(=importer 프록시 호출)을 빠져나온 <b>뒤에</b> 잡히므로 롤백이 끝난 상태다.
 */
@Service
@RequiredArgsConstructor
public class CatalogImportAsyncService {

    private final VendorCatalogImporter importer;
    private final ImportProgressStore progressStore;

    @Async
    public void importVendorCatalogAsync(String jobId, String vendorCode, Path savedPath) {
        try {
            progressStore.update(jobId, 30, "엑셀 파싱 중...");
            // 프록시 경유 호출 — 트랜잭션이 이 호출 안에서 열리고 닫힌다.
            ImportResult result = importer.importVendorCatalog(vendorCode, savedPath, jobId);
            String message = "완료! (총 " + result.total() + "건, 신규 "
                    + result.created() + " · 갱신 " + result.updated() + ")";
            progressStore.done(jobId, message, result.created(), result.updated());
        } catch (Exception e) {
            // 여기 도달했을 때 적재분은 이미 롤백된 상태다(부분 적재 없음).
            progressStore.fail(jobId, "실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(savedPath); } catch (Exception ignore) {}
        }
    }

    /**
     * 동기 적재 — 진행률 갱신/파일 정리는 하지 않는다(재사용·테스트용).
     * 실패 시 예외를 그대로 던지며 해당 실행분은 전부 롤백된다.
     *
     * @return 적재한 세트(VendorProductSet) 수
     */
    public int importVendorCatalog(String vendorCode, Path savedPath) {
        return importer.importVendorCatalog(vendorCode, savedPath, null).total();
    }
}
