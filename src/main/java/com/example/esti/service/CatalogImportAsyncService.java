package com.example.esti.service;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorExcelRow;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.repository.ProductCatalogRepository;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogImportAsyncService {

    private final VendorExcelParserFactory parserFactory;
    private final VendorRepository vendorRepository;
    private final ProductCatalogRepository catalogRepository;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final ImportProgressStore progressStore;

    private static String resolveVendorName(String vendorCode) {
        return switch (vendorCode) {
            case "A" -> "아메리칸스탠다드";
            case "B" -> "이누스";
            default -> vendorCode + "사";
        };
    }

    @Async
    @Transactional
    public void importVendorCatalogAsync(String jobId, String vendorCode, Path savedPath) {
        try {
            progressStore.update(jobId, 30, "엑셀 파싱 중...");

            // 1) vendor 조회/생성 (동기 서비스와 동일)
            Vendor vendor = vendorRepository.findByVendorCode(vendorCode)
                    .orElseGet(() -> {
                        Vendor v = new Vendor();
                        v.setVendorCode(vendorCode);
                        v.setVendorName(resolveVendorName(vendorCode));
                        return vendorRepository.save(v);
                    });

            // 2) Path로 파싱
            VendorExcelParser parser = parserFactory.getParser(vendorCode);

            // 핵심: MultipartFile이 아니라 Path로 파싱
            List<VendorExcelRow> rows = parser.parse(savedPath);

            int total = Math.max(rows.size(), 1);
            progressStore.update(jobId, 35, "DB 저장 시작");

            int done = 0;
            for (VendorExcelRow row : rows) {
                // 3) upsert 순서: catalog -> vendorItemPrice
                // 기존 upsert 로직 실행
                ProductCatalog catalog = upsertCatalog(row);
                upsertVendorItemPrice(vendor, catalog, row);

                done++;

                // 35~99 구간 진행률
                int percent = 35 + (int) Math.floor(done * 64.0 / total);
                if (percent > 99) percent = 99;

                // 너무 잦은 업데이트 방지(10건마다/마지막)
                if (done % 10 == 0 || done == total) {
                    progressStore.update(jobId, percent, "DB 저장 중...");
                }
            }

            progressStore.done(jobId, "완료!");

        } catch (Exception e) {
            progressStore.fail(jobId, "실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(savedPath); } catch (Exception ignore) {}
        }
    }


    private ProductCatalog upsertCatalog(VendorExcelRow row) {
        ProductCatalog catalog = null;

        if (row.masterCodeHint() != null && !row.masterCodeHint().isBlank()) {
            catalog = catalogRepository.findByMasterCode(row.masterCodeHint()).orElse(null);
        }

        if (catalog == null) {
            catalog = catalogRepository.findByNameAndCategoryLargeAndCategorySmall(
                    row.productName(), row.categoryLarge(), row.categorySmall()
            ).orElse(null);
        }

        if (catalog == null) {
            catalog = new ProductCatalog();
            catalog.setMasterCode(row.masterCodeHint());
        }

        catalog.setName(row.productName());
        catalog.setCategoryLarge(row.categoryLarge());
        catalog.setCategorySmall(row.categorySmall());
        catalog.setItemType(row.priceType());

        return catalogRepository.save(catalog);
    }

    private VendorItemPrice upsertVendorItemPrice(Vendor vendor, ProductCatalog catalog, VendorExcelRow row) {
        VendorItemPrice vip = vendorItemPriceRepository
                .findByVendorAndCatalogAndProposalItemCode(vendor, catalog, row.proposalItemCode())
                .orElse(null);

        if (vip == null) {
            vip = new VendorItemPrice();
            vip.setVendor(vendor);
            vip.setCatalog(catalog);
            vip.setProposalItemCode(row.proposalItemCode());
        }

        vip.setMainItemCode(row.mainItemCode());
        vip.setSubItemCode(row.subItemCode());
        vip.setOldItemCode(row.oldItemCode());
        vip.setVendorItemName(row.vendorItemName());
        vip.setVendorSpec(row.vendorSpec());
        vip.setRemark(row.remark());

        // 단가 null이면 0원 정책
        vip.setUnitPrice(row.unitPrice() != null ? row.unitPrice() : BigDecimal.ZERO);

        vip.setPriceType(row.priceType());
        vip.setCurrency("KRW");

        return vendorItemPriceRepository.save(vip);
    }
}
