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

        String masterCode = trimToNull(row.masterCodeHint());
        String name = trimToNull(row.productName());
        String categoryLarge = trimToNull(row.categoryLarge());
        String categorySmall = trimToNull(row.categorySmall());

        // 1) masterCode 우선 조회
        if (masterCode != null) {
            List<ProductCatalog> byMasterCode = catalogRepository.findAllByMasterCode(masterCode);

            if (byMasterCode.size() == 1) {
                catalog = byMasterCode.get(0);
            } else if (byMasterCode.size() > 1) {
                // 중복 데이터 존재
                // 운영 중이면 로그 남기고 첫 번째 선택
                // 더 엄격하게 하려면 예외 던져도 됨
                System.err.printf(
                        "[WARN] ProductCatalog masterCode 중복: masterCode=%s, count=%d%n",
                        masterCode, byMasterCode.size()
                );

                catalog = pickBestCatalog(byMasterCode, name, categoryLarge, categorySmall);
            }
        }

        // 2) masterCode로 못 찾았으면 이름 + 대/소분류 조회
        if (catalog == null && name != null && categoryLarge != null && categorySmall != null) {
            List<ProductCatalog> byNameCategory =
                    catalogRepository.findAllByNameAndCategoryLargeAndCategorySmall(
                            name, categoryLarge, categorySmall
                    );

            if (byNameCategory.size() == 1) {
                catalog = byNameCategory.get(0);
            } else if (byNameCategory.size() > 1) {
                System.err.printf(
                        "[WARN] ProductCatalog 이름/분류 중복: name=%s, large=%s, small=%s, count=%d%n",
                        name, categoryLarge, categorySmall, byNameCategory.size()
                );

                catalog = pickBestCatalog(byNameCategory, name, categoryLarge, categorySmall);
            }
        }

        // 3) 그래도 없으면 신규 생성
        if (catalog == null) {
            catalog = new ProductCatalog();
            catalog.setMasterCode(masterCode);
        }

        // 4) 값 반영
        catalog.setName(row.productName());
        catalog.setCategoryLarge(row.categoryLarge());
        catalog.setCategorySmall(row.categorySmall());
        catalog.setItemType(row.priceType());

        // masterCode가 비어있던 기존 데이터에 새 코드가 들어온 경우 보완
        if (isBlank(catalog.getMasterCode()) && masterCode != null) {
            catalog.setMasterCode(masterCode);
        }

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

    private ProductCatalog pickBestCatalog(
            List<ProductCatalog> candidates,
            String name,
            String categoryLarge,
            String categorySmall
    ) {
        // 1순위: 이름/대분류/소분류 모두 일치 + masterCode 있는 것 우선
        for (ProductCatalog c : candidates) {
            if (equalsTrim(c.getName(), name)
                    && equalsTrim(c.getCategoryLarge(), categoryLarge)
                    && equalsTrim(c.getCategorySmall(), categorySmall)
                    && !isBlank(c.getMasterCode())) {
                return c;
            }
        }

        // 2순위: 이름/대분류/소분류 모두 일치하는 첫 번째
        for (ProductCatalog c : candidates) {
            if (equalsTrim(c.getName(), name)
                    && equalsTrim(c.getCategoryLarge(), categoryLarge)
                    && equalsTrim(c.getCategorySmall(), categorySmall)) {
                return c;
            }
        }

        // 3순위: 그냥 첫 번째
        return candidates.get(0);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean equalsTrim(String a, String b) {
        String aa = trimToNull(a);
        String bb = trimToNull(b);
        if (aa == null && bb == null) return true;
        if (aa == null || bb == null) return false;
        return aa.equals(bb);
    }
}
