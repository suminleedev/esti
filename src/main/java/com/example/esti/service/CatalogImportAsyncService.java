package com.example.esti.service;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.entity.VendorProductRelation;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.excel.ExcelImageExtractor;
import com.example.esti.excel.ExcelImageExtractor.ExtractedImage;
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
import java.util.Map;

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
    private final ExcelImageExtractor imageExtractor;
    private final ImageDownloadService imageDownloadService;

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
            int saved = importVendorCatalog(vendorCode, savedPath, jobId);
            progressStore.done(jobId, "완료! (" + saved + "건)");
        } catch (Exception e) {
            progressStore.fail(jobId, "실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(savedPath); } catch (Exception ignore) {}
        }
    }

    /**
     * 동기 적재 — 파싱 + DB upsert. 진행률 갱신/파일 정리는 하지 않는다(재사용·테스트용).
     * 재호출 시 코드 기준 upsert로 멱등(중복 행 없음).
     *
     * @return 적재한 세트(VendorProductSet) 수
     */
    @Transactional
    public int importVendorCatalog(String vendorCode, Path savedPath) {
        return importVendorCatalog(vendorCode, savedPath, null);
    }

    private int importVendorCatalog(String vendorCode, Path savedPath, String jobId) {
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

        // 2-1) 임베디드 이미지 추출 (시트 → 행 → 이미지). 없으면 빈 맵 (D15)
        Map<String, Map<Integer, ExtractedImage>> images = imageExtractor.extract(savedPath);

        int total = Math.max(sets.size(), 1);
        if (jobId != null) progressStore.update(jobId, 35, "DB 저장 시작");

        int done = 0;
        for (VendorProductSet set : sets) {
            saveSet(vendor, set, images);
            done++;

            if (jobId != null) {
                int percent = 35 + (int) Math.floor(done * 64.0 / total);
                if (percent > 99) percent = 99;
                if (done % 10 == 0 || done == total) {
                    progressStore.update(jobId, percent, "DB 저장 중...");
                }
            }
        }
        return sets.size();
    }

    /** VendorProductSet 한 건을 대표품목 + 부속 + 관계 + 가격으로 적재. */
    private void saveSet(Vendor vendor, VendorProductSet set,
                         Map<String, Map<Integer, ExtractedImage>> images) {
        VendorParsedItem mainItem = set.main();
        if (mainItem == null) return;

        // 대표품목
        VendorProduct mainProduct = upsertVendorProduct(
                vendor, mainItem.productCode(), mainItem.productName(),
                set.categoryLarge(), set.categorySmall(), ITEM_TYPE_SET);

        // 임베디드 이미지 연결 (D15) — 대표품목 행에 앵커된 그림
        applyImage(mainProduct, set, images);

        // 대표품목 가격: 세트가 우선, 없으면 본품 단가
        BigDecimal mainPrice = set.setPrice() != null ? set.setPrice() : mainItem.unitPrice();
        String mainRemark = mainItem.remark();
        if (set.needsReview()) {
            mainRemark = appendRemark(mainRemark, "검수필요");
        }
        // 대표품목 가격은 시트(categoryLarge)별로 분리 보존 — 같은 품번이 시트마다 다른 가격일 때 충돌 방지
        upsertPrice(vendor, mainProduct, mainItem, mainPrice, mainRemark, ITEM_TYPE_SET, set.categoryLarge());

        // 부속품 + 관계
        for (VendorParsedItem part : set.parts()) {
            VendorProduct partProduct = upsertVendorProduct(
                    vendor, part.productCode(), part.productName(),
                    set.categoryLarge(), set.categorySmall(), ITEM_TYPE_PART);

            // 공유 부속 단가는 코드당 1건 유지(D13) → priceBasis=null
            upsertPrice(vendor, partProduct, part, part.unitPrice(), part.remark(), ITEM_TYPE_PART, null);
            upsertRelation(mainProduct, partProduct, part.relationType());
        }
    }

    /** 대표품목 행에 앵커된 임베디드 이미지를 저장하고 imageUrl을 연결(D15). 없으면 무시. */
    private void applyImage(VendorProduct mainProduct, VendorProductSet set,
                            Map<String, Map<Integer, ExtractedImage>> images) {
        if (set.imageKey() == null || images == null || images.isEmpty()) return;

        Map<Integer, ExtractedImage> byRow = images.get(set.categoryLarge());
        if (byRow == null) return;

        int row;
        try { row = Integer.parseInt(set.imageKey()); }
        catch (NumberFormatException e) { return; }

        ExtractedImage img = byRow.get(row);
        if (img == null || img.data() == null || img.data().length == 0) return;

        try {
            String hint = (mainProduct.getVendor().getVendorCode() + "_"
                    + (mainProduct.getProductCode() != null ? mainProduct.getProductCode() : "row" + row));
            ImageDownloadService.DownloadResult res = imageDownloadService.saveBytes(img.data(), hint, img.ext());
            mainProduct.setImageUrl(res.relativePath());
            vendorProductRepository.save(mainProduct);
        } catch (Exception e) {
            // 이미지 실패는 적재 전체를 막지 않는다(경고만)
            // (로깅은 상위 경고 로그 정책에 따름)
        }
    }

    private VendorProduct upsertVendorProduct(Vendor vendor, String productCode, String productName,
                                              String categoryLarge, String categorySmall, String itemType) {
        VendorProduct product = null;

        // 1) 코드(품번)가 있으면 코드로만 식별 — 공급사 범위 내.
        //    (이름이 같은 부속(예: "시트","도기")이 코드만 다른 경우 2)로 넘어가면 한 행으로 잘못 병합되므로
        //     코드가 있으면 이름 fallback을 타지 않는다.)
        if (productCode != null) {
            product = vendorProductRepository.findByVendorAndProductCode(vendor, productCode).orElse(null);
        }
        // 2) 코드가 아예 없는 항목(A사 신품번 없음 등)만 이름 + 대/소분류로 멱등 매칭
        else if (productName != null && categoryLarge != null && categorySmall != null) {
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

    /**
     * 가격 upsert. {@code priceBasis}(출처 시트)가 있으면 (vendor,product,proposalCode,basis) 기준으로
     * 분리 저장 — 같은 품번이 시트별로 다른 가격(대표품목)일 때 충돌 방지. basis=null이면 코드당 1건(D13).
     */
    private void upsertPrice(Vendor vendor, VendorProduct product, VendorParsedItem item,
                            BigDecimal price, String remark, String priceType, String priceBasis) {
        String proposalCode = item.productCode();

        VendorItemPrice vip;
        if (proposalCode != null && priceBasis != null) {
            vip = vendorItemPriceRepository
                    .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasis(vendor, product, proposalCode, priceBasis)
                    .orElse(null);
        } else if (proposalCode != null) {
            vip = vendorItemPriceRepository
                    .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasisIsNull(vendor, product, proposalCode)
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
        vip.setPriceBasis(priceBasis);
        vip.setCurrency("KRW");

        vendorItemPriceRepository.save(vip);
    }

    private void upsertRelation(VendorProduct source, VendorProduct target, String relationType) {
        if (source.getId() != null && source.getId().equals(target.getId())) return; // 자기 참조 방지

        String rel = (relationType != null && !relationType.isBlank())
                ? relationType
                : VendorParsedItem.RELATION_ACCESSORY;
        if (rel.length() > 50) rel = rel.substring(0, 50); // relation_type 컬럼 길이 방어

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
