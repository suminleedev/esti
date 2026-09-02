package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ASTD(아메리칸스탠다드) 이미지 매칭·저장 핸들러.
 *
 * <p><b>매칭 축은 {@code VendorProduct}다.</b> 예전에는 {@code VendorItemPrice}를 훑어
 * 제품 행을 만들어 내고, 단가표가 채운 제품명을 사이트 이름으로 덮고, 가격행이 가리키는
 * 제품까지 바꿔 끼웠다. 이미지를 붙이려고 카탈로그의 정본을 흔드는 구조였다.
 * 지금은 이누스와 같다 — <b>이미 있는 행에 사진만 붙인다.</b> 행을 만들지 않으므로
 * 사이트에만 있고 DB에 없는 제품은 그냥 건너뛴다. 단가가 없는 제품을 카탈로그에 넣지 않는다.
 *
 * <p>매칭 대상은 {@code itemType='SET'}뿐이다. 부속(PART)은 부모와 대표품번이 겹쳐
 * 서로 다른 부속이 같은 사진을 달게 되므로 제외한다(G-A).
 *
 * <p>품번 비교는 두 갈래를 모두 본다(G-4).
 * <ul>
 *   <li><b>대표품번</b> — 양쪽 다 하이픈 앞을 자른다. 예전에는 <b>DB만</b> 잘라서,
 *       하이픈이 남은 사이트 품번은 하이픈 없는 대표품번과 구조적으로 만날 수 없었다</li>
 *   <li><b>원형</b> — 자르지 않은 품번끼리도 비교한다</li>
 * </ul>
 * 둘 다 "품번이 같다"는 정확 매칭이다. 뒤 몇 자가 다른 것을 같게 보는 완화 매칭은 하지 않는다 —
 * 색상·사양 변형인 다른 제품일 수 있어, 오매칭 사진을 넣는 것보다 안 붙이는 편이 싸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AstdProductSyncHandler implements ManufacturerProductSyncHandler {

    private static final String MAKER = "ASTD";
    private static final String ITEM_TYPE_SET = "SET";

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
     * SET 행을 1회만 읽어 매칭 인덱스를 만든다.
     *
     * <p>담는 것은 <b>id뿐이다.</b> 이 메서드는 트랜잭션 밖에서 돌아 엔티티가 detach 상태로
     * 나오므로 저장할 때 id로 다시 조회한다. 이미지 보유 여부만 이때 함께 적어 둔다 —
     * dry-run이 "충전 몇 건 / 교체 몇 건"을 세려면 필요한데 그것 때문에 다시 조회할 이유는 없다.
     */
    @Override
    public Object prepare(String vendorCode) {
        Map<String, List<Long>> idsByMasterCode = new HashMap<>();
        Map<String, List<Long>> idsByFullCode = new HashMap<>();
        Set<Long> idsWithImage = new HashSet<>();

        for (VendorProduct product :
                vendorProductRepository.findAllByVendor_VendorCodeAndItemType(vendorCode, ITEM_TYPE_SET)) {

            String fullCode = normalizeCode(product.getProductCode());
            if (fullCode == null || fullCode.isBlank()) {
                continue;   // 품번 없는 행은 매칭 키가 없다
            }

            idsByFullCode.computeIfAbsent(fullCode, k -> new ArrayList<>()).add(product.getId());

            // masterCode는 엑셀 적재가 신품번의 하이픈 앞으로 이미 채워 뒀다.
            // 비어 있는 옛 행이 있을 수 있어 그때는 품번에서 직접 뽑는다.
            String masterCode = normalizeCode(product.getMasterCode());
            if (masterCode == null || masterCode.isBlank()) {
                masterCode = baseCodeOf(fullCode);
            }
            if (masterCode != null && !masterCode.isBlank()) {
                idsByMasterCode.computeIfAbsent(masterCode, k -> new ArrayList<>()).add(product.getId());
            }

            if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
                idsWithImage.add(product.getId());
            }
        }

        log.info("[{}] SET 인덱스 준비 — 대표품번 {}종 / 원형 품번 {}종 (이미지 보유 {}행)",
                MAKER, idsByMasterCode.size(), idsByFullCode.size(), idsWithImage.size());

        return new MatchContext(idsByMasterCode, idsByFullCode, idsWithImage);
    }

    /**
     * 컨텍스트 없이 부를 수 없다. {@link ProductImageSyncService}는 언제나
     * {@link #prepare(String)} 결과를 함께 넘긴다.
     */
    @Override
    public void save(CrawledProduct crawled) {
        throw new IllegalStateException(
                "ASTD 저장은 prepare()가 만든 매칭 인덱스가 필요합니다. save(crawled, context)를 쓰십시오.");
    }

    /**
     * ⚠️ 인터페이스의 default 구현에 맡기면 프록시를 우회해 {@code @Transactional}이 걸리지 않는다.
     * 명시적으로 오버라이드한다.
     */
    @Override
    @Transactional
    public void save(CrawledProduct crawled, Object context) {
        MatchContext ctx = asContext(context);

        List<Long> matchedIds = match(crawled, ctx);
        if (matchedIds.isEmpty()) {
            return;
        }

        applyImage(crawled, matchedIds, ctx);
    }

    /** 매칭만 하고 아무것도 바꾸지 않는다. 내려받지도 저장하지도 않는다. */
    @Override
    public void inspect(CrawledProduct crawled, Object context) {
        MatchContext ctx = asContext(context);

        countRows(match(crawled, ctx), ctx);
    }

    private MatchContext asContext(Object context) {
        if (context instanceof MatchContext ctx) {
            return ctx;
        }
        throw new IllegalStateException("ASTD 처리에 필요한 매칭 인덱스가 없습니다: " + context);
    }

    /**
     * 제품 1건을 인덱스에 대조하고 집계를 올린다. 반영 여부와 무관한 공통 경로라
     * 실반영과 dry-run이 <b>같은 규칙으로 같은 숫자</b>를 낸다.
     *
     * @return 갱신 대상 VendorProduct id. 매칭이 없거나 처리할 수 없으면 빈 목록
     */
    private List<Long> match(CrawledProduct crawled, MatchContext ctx) {
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

        // 원형끼리 먼저 본다. 더 좁은 비교라 여기서 걸리면 그게 가장 확실한 짝이다.
        Set<Long> ids = new LinkedHashSet<>(ctx.idsByFullCode.getOrDefault(siteCode, List.of()));
        boolean byFull = !ids.isEmpty();

        // 그리고 양쪽 다 하이픈 앞을 잘라 다시 본다. 예전에 DB만 잘라 놓쳤던 경로다.
        List<Long> byBase = ctx.idsByMasterCode.getOrDefault(baseCodeOf(siteCode), List.of());
        boolean addedByBase = ids.addAll(byBase);

        if (ids.isEmpty()) {
            ctx.notInDb++;
            return List.of();
        }

        ctx.exactMatched++;
        if (byFull) {
            ctx.matchedByFullCode++;
        }
        if (addedByBase) {
            ctx.matchedByMasterCode++;
        }

        return new ArrayList<>(ids);
    }

    private void applyImage(CrawledProduct crawled, List<Long> matchedIds, MatchContext ctx) {
        // 확장자를 붙이지 않고 넘긴다 — 응답 Content-Type을 보고 ImageDownloadService가 정한다.
        // 사이트 이미지 URL에는 확장자가 없어서(img.do?v_product=N) .jpg를 붙이는 게
        // "우연히 맞는" 상태였다. PNG가 하나만 와도 엑셀 출력이 깨진다.
        String fileName = crawled.getVendorCode() + "_" + normalizeCode(crawled.getProductCode());

        ImageDownloadService.DownloadResult downloaded;
        try {
            downloaded = imageDownloadService.download(resolveSourceUrl(crawled), fileName);
        } catch (Exception e) {
            ctx.downloadFailed++;
            log.error("[{}] 이미지 내려받기 실패. code={}", MAKER, crawled.getProductCode(), e);
            return;
        }

        // 인덱스의 id는 detach 상태에서 나온 값이라 관리 상태로 다시 조회한다.
        for (VendorProduct product : vendorProductRepository.findAllById(matchedIds)) {
            // 이미지와 출처만 갱신한다. productName·collectionName은 단가표 쪽이 정본이다(G-B).
            product.setImageUrl(downloaded.relativePath());
            product.setDetailUrl(crawled.getProductUrl());
            product.setRawTagText(crawled.getRawTagText());

            vendorProductRepository.save(product);
        }

        countRows(matchedIds, ctx);
    }

    /**
     * 갱신될 제품 행을 센다.
     *
     * <p><b>한 번의 동기화에서 같은 행은 한 번만 센다.</b> 사이트 제품 여럿이 한 대표품번으로
     * 접히면 같은 행을 여러 번 건드리게 되는데, 그건 "반영 N행"이 아니라
     * <b>N번째 사진이 이긴 1행</b>이다. 그때는 어느 사진이 맞는지 알 수 없으므로 경고를 남긴다.
     */
    private void countRows(List<Long> matchedIds, MatchContext ctx) {
        for (Long id : matchedIds) {
            if (!ctx.countedIds.add(id)) {
                ctx.rowsContested++;
                log.warn("[{}] ⚠️ 같은 제품 행에 사이트 제품이 둘 이상 걸린다 — 뒤에 온 사진이 이긴다. id={}",
                        MAKER, id);
                continue;
            }

            ctx.rowsAffected++;
            if (ctx.idsWithImage.contains(id)) {
                ctx.rowsReplaced++;
            } else {
                ctx.rowsFilled++;
            }
        }
    }

    private String resolveSourceUrl(CrawledProduct crawled) {
        String url = crawled.getDownloadUrl() != null && !crawled.getDownloadUrl().isBlank()
                ? crawled.getDownloadUrl()
                : crawled.getImageUrl();

        return (url == null || url.isBlank()) ? null : url;
    }

    /**
     * 대표품번 = 하이픈 앞. 하이픈이 없으면 전체가 대표품번이다.
     *
     * <p><b>사이트 코드와 DB 코드에 똑같이 적용한다.</b> 예전에는 이름부터 {@code ...FromDb}로
     * DB 전용이었고 실제로 DB에만 적용됐다. 사이트 품번의 상당수가 하이픈을 포함하는데
     * 그쪽을 자르지 않으니 하이픈 없는 대표품번과 만날 길이 없었다.
     */
    private String baseCodeOf(String code) {
        String normalized = normalizeCode(code);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        int idx = normalized.indexOf('-');
        return idx >= 0 ? normalized.substring(0, idx) : normalized;
    }

    /** 대소문자 무시 + 영숫자와 하이픈 외 제거. 하이픈은 대표품번 경계라 남긴다. */
    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        return code.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9\\-]", "");
    }

    /**
     * {@link #prepare(String)}이 만든 매칭 인덱스와, 한 번의 동기화에서 모인 집계.
     *
     * <p>동기화는 관리자가 한 번에 하나씩 돌리는 단일 스레드 경로라 평범한 필드로 센다.
     *
     * <p>완화 매칭은 하지 않는다(G-4) — {@code relaxedOnly}는 늘 0이고 후보 목록도 비어 있다.
     * 있지도 않은 기능의 숫자를 지어내지 않는다.
     */
    static final class MatchContext implements SyncMatchCounters {

        private final Map<String, List<Long>> idsByMasterCode;
        private final Map<String, List<Long>> idsByFullCode;
        private final Set<Long> idsWithImage;

        /** 이번 실행에서 이미 센 제품 행. 같은 행을 두 번 세지 않기 위한 것이다. */
        private final Set<Long> countedIds = new HashSet<>();

        private int collected;
        private int exactMatched;
        private int notInDb;
        private int skippedNoCode;
        private int skippedNoImage;
        private int downloadFailed;
        private int rowsAffected;
        private int rowsFilled;
        private int rowsReplaced;

        /** 어느 갈래로 걸렸는가. 리포트에는 없고 축의 기여를 보기 위한 것이다(G-4). */
        private int matchedByFullCode;
        private int matchedByMasterCode;

        /** 사이트 제품 둘 이상이 같은 제품 행을 두고 다툰 횟수. 사진이 뒤엎인다. */
        private int rowsContested;

        private MatchContext(Map<String, List<Long>> idsByMasterCode,
                             Map<String, List<Long>> idsByFullCode,
                             Set<Long> idsWithImage) {
            this.idsByMasterCode = idsByMasterCode;
            this.idsByFullCode = idsByFullCode;
            this.idsWithImage = idsWithImage;
        }

        @Override public int indexedCodes()   { return idsByMasterCode.size(); }
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

        int matchedByFullCode()   { return matchedByFullCode; }
        int matchedByMasterCode() { return matchedByMasterCode; }
        int rowsContested()       { return rowsContested; }
    }
}
