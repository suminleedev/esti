package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorExcelRow;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
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
    private final VendorProductRepository vendorProductRepository;
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
                // 3) upsert 순서: vendorProduct -> vendorItemPrice
                // 기존 upsert 로직 실행
                VendorProduct product = upsertVendorProduct(vendor, row);
                upsertVendorItemPrice(vendor, product, row);

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


    private VendorProduct upsertVendorProduct(Vendor vendor, VendorExcelRow row) {
        VendorProduct product = null;

        String productCode = trimToNull(row.productCodeHint());
        String name = trimToNull(row.productName());
        String categoryLarge = trimToNull(row.categoryLarge());
        String categorySmall = trimToNull(row.categorySmall());

        // 1) productCode 우선 조회
        if (productCode != null) {
            List<VendorProduct> byProductCode = vendorProductRepository.findAllByProductCode(productCode);

            if (byProductCode.size() == 1) {
                product = byProductCode.get(0);
            } else if (byProductCode.size() > 1) {
                // 중복 데이터 존재
                // 운영 중이면 로그 남기고 첫 번째 선택
                // 더 엄격하게 하려면 예외 던져도 됨
                System.err.printf(
                        "[WARN] VendorProduct productCode 중복: productCode=%s, count=%d%n",
                        productCode, byProductCode.size()
                );

                product = pickBestVendorProduct(byProductCode, name, categoryLarge, categorySmall);
            }
        }

        // 2) productCode로 못 찾았으면 이름 + 대/소분류 조회
        if (product == null && name != null && categoryLarge != null && categorySmall != null) {
            List<VendorProduct> byNameCategory =
                    vendorProductRepository.findAllByProductNameAndCategoryLargeAndCategorySmall(
                            name, categoryLarge, categorySmall
                    );

            if (byNameCategory.size() == 1) {
                product = byNameCategory.get(0);
            } else if (byNameCategory.size() > 1) {
                System.err.printf(
                        "[WARN] VendorProduct 이름/분류 중복: name=%s, large=%s, small=%s, count=%d%n",
                        name, categoryLarge, categorySmall, byNameCategory.size()
                );

                product = pickBestVendorProduct(byNameCategory, name, categoryLarge, categorySmall);
            }
        }

        // 3) 그래도 없으면 신규 생성
        if (product == null) {
            product = new VendorProduct();
            product.setProductCode(productCode);
        }

        // 4) 값 반영
        product.setVendor(vendor);
        product.setProductName(row.productName());
        product.setCategoryLarge(row.categoryLarge());
        product.setCategorySmall(row.categorySmall());
        product.setItemType(row.priceType());

        // ASTD masterCode, detailCode 반영
        if ("A".equals(vendor.getVendorCode()) && productCode != null) {
            String[] codes = productCode.split("-", 2);
            product.setMasterCode(codes[0].trim());
            product.setDetailCode(codes.length > 1 && !codes[1].isBlank() ? codes[1].trim() : null);
        }

        // productCode가 비어있던 기존 데이터에 새 코드가 들어온 경우 보완
        if (isBlank(product.getProductCode()) && productCode != null) {
            product.setProductCode(productCode);
        }

        return vendorProductRepository.save(product);
    }

    private void upsertVendorItemPrice(Vendor vendor, VendorProduct product, VendorExcelRow row) {
        VendorItemPrice vip = vendorItemPriceRepository
                .findByVendorAndVendorProductAndProposalItemCode(vendor, product, row.proposalItemCode())
                .orElse(null);

        if (vip == null) {
            vip = new VendorItemPrice();
            vip.setVendor(vendor);
            vip.setVendorProduct(product);
            vip.setProposalItemCode(row.proposalItemCode());
        }

        vip.setMainItemCode(row.mainItemCode());
        vip.setSubItemCode(row.subItemCode());
        vip.setOldItemCode(row.oldItemCode());
        vip.setVendorItemName(row.vendorItemName());
        vip.setRemark(row.remark());

        // 단가 null이면 0원 정책
        vip.setUnitPrice(row.unitPrice() != null ? row.unitPrice() : BigDecimal.ZERO);

        vip.setPriceType(row.priceType());
        vip.setCurrency("KRW");

        vendorItemPriceRepository.save(vip);
    }

    private VendorProduct pickBestVendorProduct (
            List<VendorProduct> candidates,
            String name,
            String categoryLarge,
            String categorySmall
    ) {
        // 1순위: 이름/대분류/소분류 모두 일치 + productCode 있는 것 우선
        for (VendorProduct p : candidates) {
            if (equalsTrim(p.getProductName(), name)
                    && equalsTrim(p.getCategoryLarge(), categoryLarge)
                    && equalsTrim(p.getCategorySmall(), categorySmall)
                    && !isBlank(p.getProductCode())) {
                return p;
            }
        }

        // 2순위: 이름/대분류/소분류 모두 일치하는 첫 번째
        for (VendorProduct p : candidates) {
            if (equalsTrim(p.getProductName(), name)
                    && equalsTrim(p.getCategoryLarge(), categoryLarge)
                    && equalsTrim(p.getCategorySmall(), categorySmall)) {
                return p;
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
