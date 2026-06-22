package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorParsedItem;
import com.example.esti.excel.VendorProductSet;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
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

    private static final String ITEM_TYPE_SET = "SET";
    private static final String ITEM_TYPE_PART = "PART";

    private final VendorExcelParserFactory parserFactory;
    private final VendorRepository vendorRepository;
    private final VendorProductRepository vendorProductRepository;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final VendorProductRelationRepository vendorProductRelationRepository;
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

            // 1) vendor 조회/생성
            Vendor vendor = vendorRepository.findByVendorCode(vendorCode)
                    .orElseGet(() -> {
                        Vendor v = new Vendor();
                        v.setVendorCode(vendorCode);
                        v.setVendorName(resolveVendorName(vendorCode));
                        return vendorRepository.save(v);
                    });

            // 2) 파싱 (대표품목 + 부속 묶음)
            VendorExcelParser parser = parserFactory.getParser(vendorCode);
            List<VendorProductSet> sets = parser.parseSets(savedPath);

            int total = Math.max(sets.size(), 1);
            progressStore.update(jobId, 35, "DB 저장 시작");

            int done = 0;
            for (VendorProductSet set : sets) {
                saveSet(vendor, set);
                done++;

                int percent = 35 + (int) Math.floor(done * 64.0 / total);
                if (percent > 99) percent = 99;
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

    /** VendorProductSet 한 건을 대표품목 + 부속 + 관계 + 가격으로 적재. */
    private void saveSet(Vendor vendor, VendorProductSet set) {
        VendorParsedItem mainItem = set.main();
        if (mainItem == null) return;

        // 대표품목
        VendorProduct mainProduct = upsertVendorProduct(
                vendor, mainItem.productCode(), mainItem.productName(),
                set.categoryLarge(), set.categorySmall(), ITEM_TYPE_SET);

        // 대표품목 가격: 세트가 우선, 없으면 본품 단가
        BigDecimal mainPrice = set.setPrice() != null ? set.setPrice() : mainItem.unitPrice();
        String mainRemark = mainItem.remark();
        if (set.needsReview()) {
            mainRemark = appendRemark(mainRemark, "검수필요");
        }
        upsertPrice(vendor, mainProduct, mainItem, mainPrice, mainRemark, ITEM_TYPE_SET);

        // 부속품 + 관계
        for (VendorParsedItem part : set.parts()) {
            VendorProduct partProduct = upsertVendorProduct(
                    vendor, part.productCode(), part.productName(),
                    set.categoryLarge(), set.categorySmall(), ITEM_TYPE_PART);

            upsertPrice(vendor, partProduct, part, part.unitPrice(), part.remark(), ITEM_TYPE_PART);
            upsertRelation(mainProduct, partProduct, part.relationType());
        }
    }

    private VendorProduct upsertVendorProduct(Vendor vendor, String productCode, String productName,
                                              String categoryLarge, String categorySmall, String itemType) {
        VendorProduct product = null;

        // 1) 코드(품번) 우선 — 공급사 범위 내
        if (productCode != null) {
            product = vendorProductRepository.findByVendorAndProductCode(vendor, productCode).orElse(null);
        }

        // 2) 코드가 없으면 이름 + 대/소분류 (같은 공급사 한정)
        if (product == null && productName != null && categoryLarge != null && categorySmall != null) {
            product = vendorProductRepository
                    .findAllByProductNameAndCategoryLargeAndCategorySmall(productName, categoryLarge, categorySmall)
                    .stream()
                    .filter(p -> p.getVendor() != null
                            && vendor.getVendorCode().equals(p.getVendor().getVendorCode()))
                    .findFirst()
                    .orElse(null);
        }

        // 3) 신규
        if (product == null) {
            product = new VendorProduct();
            product.setProductCode(productCode);
        }

        product.setVendor(vendor);
        product.setProductName(productName);
        product.setCategoryLarge(categoryLarge);
        product.setCategorySmall(categorySmall);
        product.setItemType(itemType);

        // A사 masterCode/detailCode 분리
        if ("A".equals(vendor.getVendorCode()) && productCode != null) {
            String[] codes = productCode.split("-", 2);
            product.setMasterCode(codes[0].trim());
            product.setDetailCode(codes.length > 1 && !codes[1].isBlank() ? codes[1].trim() : null);
        }

        if (isBlank(product.getProductCode()) && productCode != null) {
            product.setProductCode(productCode);
        }

        return vendorProductRepository.save(product);
    }

    private void upsertPrice(Vendor vendor, VendorProduct product, VendorParsedItem item,
                            BigDecimal price, String remark, String priceType) {
        String proposalCode = item.productCode();

        VendorItemPrice vip;
        if (proposalCode != null) {
            vip = vendorItemPriceRepository
                    .findByVendorAndVendorProductAndProposalItemCode(vendor, product, proposalCode)
                    .orElse(null);
        } else {
            // 신품번 없는 항목: product 기준으로 기존 가격 재사용(멱등)
            vip = vendorItemPriceRepository.findFirstByVendorAndVendorProduct(vendor, product).orElse(null);
        }

        if (vip == null) {
            vip = new VendorItemPrice();
            vip.setVendor(vendor);
            vip.setVendorProduct(product);
            vip.setProposalItemCode(proposalCode);
        }

        vip.setMainItemCode(item.productCode());
        vip.setSubItemCode(item.subItemCode());
        vip.setOldItemCode(item.oldItemCode());
        vip.setVendorItemName(item.productName());
        vip.setRemark(remark);
        vip.setUnitPrice(price != null ? price : BigDecimal.ZERO);
        vip.setPriceType(priceType);
        vip.setCurrency("KRW");

        vendorItemPriceRepository.save(vip);
    }

    private void upsertRelation(VendorProduct source, VendorProduct target, String relationType) {
        if (source.getId() != null && source.getId().equals(target.getId())) return; // 자기 참조 방지

        String rel = (relationType != null && !relationType.isBlank())
                ? relationType
                : VendorParsedItem.RELATION_ACCESSORY;

        boolean exists = vendorProductRelationRepository
                .findBySourceProductAndTargetProductAndRelationType(source, target, rel)
                .isPresent();
        if (exists) return;

        vendorProductRelationRepository.save(
                VendorProductRelation.builder()
                        .sourceProduct(source)
                        .targetProduct(target)
                        .relationType(rel)
                        .build()
        );
    }

    private String appendRemark(String remark, String tag) {
        if (remark == null || remark.isBlank()) return tag;
        return remark + " | " + tag;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
