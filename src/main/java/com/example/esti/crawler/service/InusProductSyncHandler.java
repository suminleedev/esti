package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * INUS(이누스) 이미지 매칭·저장 핸들러.
 *
 * <p>ASTD·Default 핸들러와 다른 점이 하나 있다. 그쪽은 {@code VendorItemPrice}를 찾아
 * {@code VendorProduct}를 만들어 내지만, <b>B사는 엑셀 적재가 이미 만들어 둔 행에
 * 이미지만 붙이는 일이다.</b> 그래서 여기서는 행을 새로 만들지 않는다 —
 * 사이트에만 있고 DB에 없는 제품은 그냥 건너뛴다. 단가가 없는 제품을 카탈로그에 넣지 않는다.
 *
 * <p>매칭 대상은 {@code itemType='SET'}뿐이다. 부속(PART)은 부모 제품과 품번이 겹쳐
 * 서로 다른 부속이 같은 사진을 달게 되므로 제외한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InusProductSyncHandler implements ManufacturerProductSyncHandler {

    private static final String MAKER = "INUS";
    private static final String ITEM_TYPE_SET = "SET";

    /** 완화 매칭으로 볼 수 있는 앞뒤 길이 차이의 한계. */
    private static final int RELAXED_MAX_LENGTH_DIFF = 3;

    private final ImageDownloadService imageDownloadService;
    private final VendorProductRepository vendorProductRepository;

    @Override
    public boolean supports(String maker) {
        return MAKER.equalsIgnoreCase(maker);
    }

    @Override
    public int order() {
        return 10;   // Default(1000)보다 먼저 잡히게 한다
    }

    /**
     * SET 행을 1회만 읽어 "정규화 품번 → VendorProduct id" 인덱스를 만든다.
     *
     * <p>담는 것은 <b>id뿐이다.</b> 이 메서드는 트랜잭션 밖에서 돌아 엔티티가 detach 상태로
     * 나오므로, 저장할 때 id로 다시 조회한다.
     */
    @Override
    public Object prepare(String vendorCode) {
        Map<String, List<Long>> idsByCode = new HashMap<>();

        for (VendorProduct product :
                vendorProductRepository.findAllByVendor_VendorCodeAndItemType(vendorCode, ITEM_TYPE_SET)) {

            String code = normalizeCode(product.getProductCode());
            if (code != null && !code.isBlank()) {
                idsByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(product.getId());
            }
        }

        log.info("[{}] SET 인덱스 {}개 품번 준비", MAKER, idsByCode.size());

        return new MatchContext(idsByCode);
    }

    /**
     * 컨텍스트 없이 부를 수 없다. {@link ProductImageSyncService}는 언제나
     * {@link #prepare(String)} 결과를 함께 넘긴다.
     */
    @Override
    public void save(CrawledProduct crawled) {
        throw new IllegalStateException(
                "INUS 저장은 prepare()가 만든 매칭 인덱스가 필요합니다. save(crawled, context)를 쓰십시오.");
    }

    /**
     * ⚠️ 인터페이스의 default 구현에 맡기면 프록시를 우회해 {@code @Transactional}이 걸리지 않는다.
     * 명시적으로 오버라이드한다.
     */
    @Override
    @Transactional
    public void save(CrawledProduct crawled, Object context) {
        if (!(context instanceof MatchContext ctx)) {
            throw new IllegalStateException("INUS 저장에 필요한 매칭 인덱스가 없습니다: " + context);
        }

        ctx.collected++;

        Set<String> siteCodes = matchingKeys(crawled);
        if (siteCodes.isEmpty()) {
            ctx.skippedNoCode++;
            log.info("[{}] 품번 없음 — 건너뜀. url={}", MAKER, crawled.getProductUrl());
            return;
        }

        String sourceUrl = resolveSourceUrl(crawled);
        if (sourceUrl == null) {
            ctx.skippedNoImage++;
            log.info("[{}] 이미지 없음 — 건너뜀. code={}", MAKER, crawled.getProductCode());
            return;
        }

        List<Long> matchedIds = findExactMatches(ctx, siteCodes);

        if (matchedIds.isEmpty()) {
            // 정확 매칭이 없을 때만 완화 후보를 찾는다. 찾아도 쓰지 않는다 —
            // 뒤 몇 자가 다른 건 색상·사양 변형인 다른 제품일 수 있어서, 목록으로 뽑아
            // 눈으로 보고 승인하는 쪽이 오매칭 사진을 넣는 것보다 싸다(G-1 ⓒ).
            List<RelaxedCandidate> candidates = findRelaxedCandidates(ctx, siteCodes);
            if (candidates.isEmpty()) {
                ctx.notInDb++;
            } else {
                ctx.relaxedOnly++;
                ctx.relaxedCandidates.addAll(candidates);
            }
            return;
        }

        ctx.exactMatched++;
        applyImage(crawled, sourceUrl, matchedIds, ctx);
    }

    private void applyImage(CrawledProduct crawled, String sourceUrl, List<Long> matchedIds, MatchContext ctx) {
        String fileName = crawled.getVendorCode() + "_" + normalizeFileNamePart(crawled.getProductCode());

        ImageDownloadService.DownloadResult downloaded;
        try {
            downloaded = imageDownloadService.download(sourceUrl, fileName);
        } catch (Exception e) {
            ctx.downloadFailed++;
            log.error("[{}] 이미지 내려받기 실패. code={}, url={}",
                    MAKER, crawled.getProductCode(), sourceUrl, e);
            return;
        }

        // 인덱스의 id는 detach 상태에서 나온 값이라 관리 상태로 다시 조회한다.
        for (VendorProduct product : vendorProductRepository.findAllById(matchedIds)) {
            boolean hadImage = product.getImageUrl() != null && !product.getImageUrl().isBlank();

            // 이미지만 갱신한다. productName·collectionName은 단가표 쪽이 정본이다.
            product.setImageUrl(downloaded.relativePath());
            vendorProductRepository.save(product);

            ctx.rowsUpdated++;
            if (hadImage) {
                ctx.rowsReplaced++;
            } else {
                ctx.rowsFilled++;
            }
        }
    }

    /** 사이트 품번과 별칭 품번을 둘 다 매칭 키로 쓴다. */
    private Set<String> matchingKeys(CrawledProduct crawled) {
        Set<String> keys = new LinkedHashSet<>();

        addNormalized(keys, crawled.getProductCode());
        addNormalized(keys, crawled.getRawTagText());   // 파서가 별칭 품번을 여기 담아 넘긴다

        return keys;
    }

    private void addNormalized(Set<String> target, String raw) {
        String code = normalizeCode(raw);
        if (code != null && !code.isBlank()) {
            target.add(code);
        }
    }

    private List<Long> findExactMatches(MatchContext ctx, Set<String> siteCodes) {
        Set<Long> ids = new LinkedHashSet<>();

        for (String code : siteCodes) {
            ids.addAll(ctx.idsByCode.getOrDefault(code, List.of()));
        }

        return new ArrayList<>(ids);
    }

    /**
     * 한쪽이 다른 쪽으로 시작하고 길이 차이가 {@value #RELAXED_MAX_LENGTH_DIFF}자 이내인 짝을 찾는다.
     * 사이트가 더 긴 경우와 DB가 더 긴 경우를 모두 본다.
     */
    private List<RelaxedCandidate> findRelaxedCandidates(MatchContext ctx, Set<String> siteCodes) {
        List<RelaxedCandidate> found = new ArrayList<>();

        for (String siteCode : siteCodes) {
            for (String dbCode : ctx.idsByCode.keySet()) {
                if (isRelaxedMatch(siteCode, dbCode)) {
                    found.add(new RelaxedCandidate(siteCode, dbCode));
                }
            }
        }

        return found;
    }

    private boolean isRelaxedMatch(String siteCode, String dbCode) {
        if (siteCode.equals(dbCode)) {
            return false;
        }
        if (siteCode.startsWith(dbCode)) {
            return siteCode.length() - dbCode.length() <= RELAXED_MAX_LENGTH_DIFF;
        }
        if (dbCode.startsWith(siteCode)) {
            return dbCode.length() - siteCode.length() <= RELAXED_MAX_LENGTH_DIFF;
        }
        return false;
    }

    private String resolveSourceUrl(CrawledProduct crawled) {
        String url = crawled.getDownloadUrl() != null && !crawled.getDownloadUrl().isBlank()
                ? crawled.getDownloadUrl()
                : crawled.getImageUrl();

        return (url == null || url.isBlank()) ? null : url;
    }

    /** 대소문자 무시 + 영숫자 외 제거. {@code C853-2}와 {@code C8532}가 같은 것으로 취급된다. */
    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** 파일명에는 사이트 표기를 살리되 경로로 쓸 수 없는 문자만 걷어낸다. */
    private String normalizeFileNamePart(String code) {
        return code == null ? "" : code.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** 사이트 품번과 DB 품번의 완화 후보 짝. 반영하지 않고 리포트로만 쓴다. */
    public record RelaxedCandidate(String siteCode, String dbCode) {
    }

    /**
     * {@link #prepare(String)}이 만든 매칭 인덱스와, 한 번의 동기화에서 모인 집계.
     *
     * <p>집계는 dry-run 리포트(I-7)와 실반영 결과 대조(I-10)가 함께 쓴다. 동기화는
     * 관리자가 한 번에 하나씩 돌리는 단일 스레드 경로라 평범한 필드로 센다.
     */
    @Getter
    public static final class MatchContext {

        private final Map<String, List<Long>> idsByCode;
        private final List<RelaxedCandidate> relaxedCandidates = new ArrayList<>();

        private int collected;
        private int exactMatched;
        private int relaxedOnly;
        private int notInDb;
        private int skippedNoCode;
        private int skippedNoImage;
        private int downloadFailed;
        private int rowsUpdated;
        private int rowsFilled;
        private int rowsReplaced;

        private MatchContext(Map<String, List<Long>> idsByCode) {
            this.idsByCode = idsByCode;
        }

        /** 인덱스에 담긴 서로 다른 품번 수 — 매칭 모수. */
        public int indexedCodeCount() {
            return idsByCode.size();
        }
    }
}
