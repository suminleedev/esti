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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * 벤더 가격행을 1회만 로드해 "대표품번 → 가격행" 인덱스를 만든다.
     * 제품 1건마다 전체 테이블을 로드하던 O(제품수 x 전체 행) 풀스캔을 제거한다.
     *
     * <p>돌려주는 컨텍스트가 {@link SyncMatchCounters}라 동기화 리포트에 매칭 상세가 실린다.
     * 예전에는 맨 {@code HashMap}이라 <b>{@code dryRun=true}가 아무것도 하지 않고 성공했고,</b>
     * 실반영도 몇 건이 붙었는지 알 수 없었다.
     */
    @Override
    public Object prepare(String vendorCode) {
        Map<String, List<CodeMatch>> byBaseCode = new LinkedHashMap<>();

        for (VendorItemPrice vip : vendorItemPriceRepository.findAllByVendor_VendorCode(vendorCode)) {
            // 한 가격행의 후보 4종이 같은 대표품번으로 접힐 수 있다. 그때 첫 후보만 남긴다 —
            // getMatchedRawCode()가 "대표품번이 같은 첫 후보"를 고르는 것과 같은 규칙이라야
            // 인덱스가 가리키는 품번과 실제로 갱신되는 제품이 어긋나지 않는다.
            Set<String> seenBases = new HashSet<>();

            for (String candidate : getCandidateCodes(vip)) {
                String base = extractAstdBaseCodeFromDb(candidate);
                if (base != null && seenBases.add(base)) {
                    byBaseCode.computeIfAbsent(base, k -> new ArrayList<>())
                            .add(new CodeMatch(vip.getId(), normalizeCode(candidate)));
                }
            }
        }

        // 제품 행의 이미지 보유 상태. dry-run이 "충전 몇 건 / 교체 몇 건"을 세려면 필요하다.
        // 여기 없는 품번은 곧 제품 행이 없다는 뜻이고, 그건 저장 시 행이 새로 생긴다는 신호다.
        Map<String, Boolean> imageByProductCode = new HashMap<>();
        int withImage = 0;

        for (VendorProduct product : vendorProductRepository.findAllByVendor_VendorCode(vendorCode)) {
            String code = normalizeCode(product.getProductCode());
            if (code == null || code.isBlank()) {
                continue;
            }

            boolean hasImage = product.getImageUrl() != null && !product.getImageUrl().isBlank();
            imageByProductCode.put(code, hasImage);
            if (hasImage) {
                withImage++;
            }
        }

        log.info("[{}] 인덱스 준비 — 대표품번 {}종 / 제품 {}행(이미지 보유 {}행)",
                MAKER, byBaseCode.size(), imageByProductCode.size(), withImage);

        return new MatchContext(byBaseCode, imageByProductCode);
    }

    @Override
    @Transactional
    public void save(CrawledProduct crawled, Object context) {
        if (!(context instanceof MatchContext ctx)) {
            save(crawled); // 방어적 폴백(구 경로)
            return;
        }

        List<CodeMatch> matches = match(crawled, ctx);
        if (matches.isEmpty()) {
            return;
        }

        String siteCode = normalizeCode(crawled.getProductCode());
        String fileName = crawled.getVendorCode() + "_" + siteCode + ".jpg";

        ImageDownloadService.DownloadResult downloaded;
        try {
            downloaded = imageDownloadService.download(resolveSourceUrl(crawled), fileName);
        } catch (Exception e) {
            ctx.downloadFailed++;
            log.error("[{}] 이미지 내려받기 실패. siteCode={}", MAKER, siteCode, e);
            return;
        }

        // 인덱스의 id는 트랜잭션 밖(detach)에서 나온 값이라 관리 상태로 다시 조회한다.
        List<Long> vipIds = matches.stream().map(CodeMatch::vipId).toList();
        for (VendorItemPrice vip : vendorItemPriceRepository.findAllById(vipIds)) {
            upsertVendorProduct(vip, crawled, downloaded.relativePath());
        }

        countRows(matches, ctx);

        log.info("[{}] 저장. siteCode={}, 가격행 {}건, path={}",
                MAKER, siteCode, vipIds.size(), downloaded.relativePath());
    }

    /** 매칭만 하고 아무것도 바꾸지 않는다. 내려받지도 저장하지도 않는다. */
    @Override
    public void inspect(CrawledProduct crawled, Object context) {
        if (!(context instanceof MatchContext ctx)) {
            throw new IllegalStateException("ASTD dry-run에 필요한 매칭 인덱스가 없습니다: " + context);
        }

        countRows(match(crawled, ctx), ctx);
    }

    /**
     * 제품 1건을 인덱스에 대조하고 집계를 올린다. 반영 여부와 무관한 공통 경로라
     * 실반영과 dry-run이 <b>같은 규칙으로 같은 숫자</b>를 낸다.
     *
     * @return 갱신 대상 가격행과 그 품번. 매칭이 없거나 처리할 수 없으면 빈 목록
     */
    private List<CodeMatch> match(CrawledProduct crawled, MatchContext ctx) {
        ctx.collected++;

        String siteCode = normalizeCode(crawled.getProductCode());
        if (siteCode == null || siteCode.isBlank()) {
            ctx.skippedNoCode++;
            log.info("[{}] 품번 없음 — 건너뜀. url={}", MAKER, crawled.getProductUrl());
            return List.of();
        }

        if (resolveSourceUrl(crawled) == null) {
            ctx.skippedNoImage++;
            log.info("[{}] 이미지 없음 — 건너뜀. siteCode={}", MAKER, siteCode);
            return List.of();
        }

        List<CodeMatch> matches = ctx.byBaseCode.getOrDefault(siteCode, List.of());
        if (matches.isEmpty()) {
            ctx.notInDb++;
            return List.of();
        }

        ctx.exactMatched++;
        return matches;
    }

    /**
     * 갱신될 제품 행을 센다.
     *
     * <p>가격행이 아니라 <b>제품 행</b>을 센다 — 여러 가격행이 같은 품번을 가리키면
     * 실제로 사진이 붙는 곳은 하나다. 가격행 수로 세면 반영 규모가 부풀려진다.
     */
    private void countRows(List<CodeMatch> matches, MatchContext ctx) {
        Set<String> targetCodes = new LinkedHashSet<>();
        for (CodeMatch match : matches) {
            if (match.productCode() != null && !match.productCode().isBlank()) {
                targetCodes.add(match.productCode());
            }
        }

        for (String code : targetCodes) {
            Boolean hasImage = ctx.imageByProductCode.get(code);

            ctx.rowsAffected++;

            if (hasImage == null) {
                // 제품 행이 없다 = 저장하면 분류도 itemType도 없는 행이 새로 생긴다.
                // 매칭된 후보가 구품번이면 이 길로 온다. C-3(매칭 축 이관)이 없애려는 것이 이 경로다.
                ctx.rowsCreated++;
                ctx.rowsFilled++;
                log.warn("[{}] ⚠️ 제품 행 신규 생성 예정 — DB에 없는 품번이다. code={}", MAKER, code);
            } else if (hasImage) {
                ctx.rowsReplaced++;
            } else {
                ctx.rowsFilled++;
            }
        }
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

        return candidates.stream()
                .map(this::extractAstdBaseCodeFromDb)
                .filter(Objects::nonNull)
                .anyMatch(siteCode::equals);
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

        String mstCode = extractAstdBaseCodeFromDb(normalizedMatchedCode);
        String detailCode = extractDetailCodeFromDb(normalizedMatchedCode);

        VendorProduct vendorProduct = vendorProductRepository
                .findByVendorAndProductCode(vendor, normalizedMatchedCode)
                .orElseGet(() -> VendorProduct.builder()
                        .vendor(vendor)
                        .productCode(normalizedMatchedCode)
                        .masterCode(mstCode)
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

    /**
     * 인덱스 한 칸 — 어느 가격행이, 어느 품번으로 걸렸는가.
     *
     * <p>품번을 함께 들고 다니는 이유는 <b>갱신 대상 제품 행을 미리 알기 위해서다.</b>
     * 가격행 id만으로는 dry-run이 무엇이 바뀔지 말할 수 없다.
     *
     * @param productCode 매칭된 후보 품번(정규화). 이 품번의 제품 행에 사진이 붙는다
     */
    private record CodeMatch(Long vipId, String productCode) {
    }

    /**
     * {@link #prepare(String)}이 만든 매칭 인덱스와, 한 번의 동기화에서 모인 집계.
     *
     * <p>동기화는 관리자가 한 번에 하나씩 돌리는 단일 스레드 경로라 평범한 필드로 센다.
     *
     * <p>완화 매칭은 아직 없다 — {@code relaxedOnly}는 늘 0이고 후보 목록도 비어 있다.
     * 있지도 않은 기능의 숫자를 지어내지 않는다(G-4에서 정한다).
     */
    static final class MatchContext implements SyncMatchCounters {

        private final Map<String, List<CodeMatch>> byBaseCode;
        private final Map<String, Boolean> imageByProductCode;

        private int collected;
        private int exactMatched;
        private int notInDb;
        private int skippedNoCode;
        private int skippedNoImage;
        private int downloadFailed;
        private int rowsAffected;
        private int rowsFilled;
        private int rowsReplaced;

        /** 제품 행이 <b>새로 생기는</b> 건수. 리포트에는 없고 로그로만 남긴다 — §4의 위험 실측치다. */
        private int rowsCreated;

        private MatchContext(Map<String, List<CodeMatch>> byBaseCode,
                             Map<String, Boolean> imageByProductCode) {
            this.byBaseCode = byBaseCode;
            this.imageByProductCode = imageByProductCode;
        }

        @Override public int indexedCodes()   { return byBaseCode.size(); }
        @Override public int collected()      { return collected; }
        @Override public int exactMatched()   { return exactMatched; }
        @Override public int relaxedOnly()    { return 0; }
        @Override public int notInDb()        { return notInDb; }
        @Override public int skippedNoCode()  { return skippedNoCode; }
        @Override public int skippedNoImage() { return skippedNoImage; }
        @Override public int downloadFailed() { return downloadFailed; }
        @Override public int rowsAffected()   { return rowsAffected; }
        @Override public int rowsFilled()     { return rowsFilled; }
        @Override public int rowsReplaced()   { return rowsReplaced; }

        @Override
        public List<String> relaxedCandidates() {
            return List.of();
        }

        int rowsCreated() {
            return rowsCreated;
        }
    }
}
