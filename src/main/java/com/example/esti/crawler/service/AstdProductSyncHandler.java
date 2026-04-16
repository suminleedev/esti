package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AstdProductSyncHandler implements ManufacturerProductSyncHandler {

    private static final String MAKER = "ASTD";

    private final ImageDownloadService imageDownloadService;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final VendorProductRepository vendorProductRepository;

    @Override
    public boolean supports(String maker) {
        return MAKER.equalsIgnoreCase(maker);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    @Transactional
    public void save(CrawledProduct crawled) {
        try {
            String siteCode = normalizeCode(crawled.getProductCode());
            if (siteCode == null || siteCode.isBlank()) {
                log.info("[{}] skip. no productCode. url={}", crawled.getMaker(), crawled.getProductUrl());
                return;
            }

            String sourceUrl = resolveSourceUrl(crawled);
            if (sourceUrl == null) {
                log.info("[{}] skip. no image url. productCode={}", crawled.getMaker(), siteCode);
                return;
            }

            List<VendorItemPrice> vendorItems =
                    vendorItemPriceRepository.findAllByVendor_VendorCode(crawled.getVendorCode());

            List<VendorItemPrice> matchedItems = vendorItems.stream()
                    .filter(vip -> matchesAstdSiteCode(vip, siteCode))
                    .toList();

            if (matchedItems.isEmpty()) {
                log.info("[{}] no matched vendorItemPrice. vendorCode={}, siteCode={}",
                        crawled.getMaker(), crawled.getVendorCode(), siteCode);

                vendorItems.stream()
                        .limit(5)
                        .forEach(vip -> log.info(
                                "[ASTD] sample row. proposal={}, main={}, sub={}, old={}",
                                vip.getProposalItemCode(),
                                vip.getMainItemCode(),
                                vip.getSubItemCode(),
                                vip.getOldItemCode()
                        ));
                return;
            }

            String fileName = crawled.getVendorCode() + "_" + siteCode + ".jpg";
            ImageDownloadService.DownloadResult result =
                    imageDownloadService.download(sourceUrl, fileName);

            for (VendorItemPrice vip : matchedItems) {
                upsertVendorProduct(vip, crawled, result.relativePath());
            }

            log.info("[{}] saved vendorProducts. vendorCode={}, siteCode={}, count={}, path={}",
                    crawled.getMaker(),
                    crawled.getVendorCode(),
                    siteCode,
                    matchedItems.size(),
                    result.relativePath());

        } catch (Exception e) {
            log.error("[{}] save failed. vendorCode={}, productCode={}",
                    crawled.getMaker(), crawled.getVendorCode(), crawled.getProductCode(), e);
        }
    }

    private boolean matchesAstdSiteCode(VendorItemPrice vip, String siteCode) {
        List<String> candidates = getCandidateCodes(vip);

        boolean matched = candidates.stream()
                .map(this::extractAstdBaseCodeFromDb)
                .filter(Objects::nonNull)
                .anyMatch(siteCode::equals);

        if (matched) {
            log.info("[ASTD] matched. siteCode={}, proposal={}, main={}, sub={}, old={}",
                    siteCode,
                    vip.getProposalItemCode(),
                    vip.getMainItemCode(),
                    vip.getSubItemCode(),
                    vip.getOldItemCode());
        }

        return matched;
    }

    private List<String> getCandidateCodes(VendorItemPrice vip) {
        Set<String> unique = new LinkedHashSet<>();

        addIfPresent(unique, vip.getProposalItemCode());
        addIfPresent(unique, vip.getMainItemCode());
        addIfPresent(unique, vip.getSubItemCode());
        addIfPresent(unique, vip.getOldItemCode());

        return unique.stream().toList();
    }

    private void addIfPresent(Set<String> target, String value) {
        if (value == null) {
            return;
        }

        String trimmed = value.trim();
        if (!trimmed.isBlank()) {
            target.add(trimmed);
        }
    }

    private void upsertVendorProduct(VendorItemPrice vip, CrawledProduct crawled, String relativePath) {
        Vendor vendor = vip.getVendor();

        String matchedRawCode = getMatchedRawCode(vip, normalizeCode(crawled.getProductCode()));
        String normalizedMatchedCode = normalizeCode(matchedRawCode);

        String repCode = extractAstdBaseCodeFromDb(normalizedMatchedCode);
        String detailCode = extractDetailCodeFromDb(normalizedMatchedCode);

        VendorProduct vendorProduct = vendorProductRepository
                .findByVendorAndProductCode(vendor, normalizedMatchedCode)
                .orElseGet(() -> VendorProduct.builder()
                        .vendor(vendor)
                        .productCode(normalizedMatchedCode)
                        .representativeCode(repCode)
                        .detailCode(detailCode)
                        .build());

        vendorProduct.setProductName(crawled.getProductName());
        vendorProduct.setCollectionName(crawled.getCollectionName());
        vendorProduct.setImageUrl(relativePath);
        vendorProduct.setDetailUrl(crawled.getProductUrl());
        vendorProduct.setRawTagText(crawled.getRawTagText());

        vendorProduct = vendorProductRepository.save(vendorProduct);

        vip.setVendorProduct(vendorProduct);
        vendorItemPriceRepository.save(vip);
    }

    private String getMatchedRawCode(VendorItemPrice vip, String siteCode) {
        return getCandidateCodes(vip).stream()
                .filter(code -> siteCode.equals(extractAstdBaseCodeFromDb(code)))
                .findFirst()
                .orElse(vip.getProposalItemCode());
    }

    private String resolveSourceUrl(CrawledProduct crawled) {
        String sourceUrl = crawled.getDownloadUrl() != null && !crawled.getDownloadUrl().isBlank()
                ? crawled.getDownloadUrl()
                : crawled.getImageUrl();

        return (sourceUrl == null || sourceUrl.isBlank()) ? null : sourceUrl;
    }

    /**
     * ASTD 전용 DB 비교 규칙:
     * - DB 품번이 "대표품번-상세품번" 이면 하이픈 앞만 대표품번으로 사용
     * - 하이픈이 없으면 전체를 대표품번으로 사용
     */
    private String extractAstdBaseCodeFromDb(String dbCode) {
        if (dbCode == null || dbCode.isBlank()) {
            return null;
        }

        String normalized = normalizeCode(dbCode);
        int idx = normalized.indexOf("-");
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }

    /**
     * 상세품번은 하이픈 뒤 값.
     * 없으면 null.
     */
    private String extractDetailCodeFromDb(String dbCode) {
        if (dbCode == null || dbCode.isBlank()) {
            return null;
        }

        String normalized = normalizeCode(dbCode);
        int idx = normalized.indexOf("-");
        return idx >= 0 ? normalized.substring(idx + 1) : null;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        return code.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9\\-]", "");
    }
}