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
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorProductRelationRepository;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공급사 단가표 적재의 <b>트랜잭션 경계</b>. 파싱 결과를 VendorProduct/Relation/ItemPrice로 풀어 upsert한다.
 *
 * <p>이 클래스가 {@code CatalogImportAsyncService}에서 분리돼 있는 이유는 하나다(B-1):
 * <b>예외를 트랜잭션 밖에서 잡아야 롤백이 된다.</b> 예전에는 {@code @Async}·{@code @Transactional}이 같은
 * 메서드에 붙어 있고 그 안의 try/catch가 예외를 삼켜, 실패해도 롤백 마킹 없이 <b>부분 적재분이 커밋</b>됐다.
 * 진행률만 "실패"로 보이고 DB에는 중간까지 저장된 데이터가 남는 상태였다.
 *
 * <p>자기호출(self-invocation)은 프록시를 타지 않아 {@code @Transactional}이 무시되므로,
 * 어노테이션을 안쪽 메서드로 옮기는 것만으로는 해결되지 않는다. 별도 빈으로 분리해
 * 비동기 서비스가 <b>프록시를 경유해</b> 호출하도록 만든 것이 이 구조다.
 *
 * <p>진행률 갱신은 트랜잭션 커밋과 무관한 인메모리 저장소({@code ImportProgressStore})라
 * 롤백돼도 되돌아가지 않는다 — 실패 메시지는 호출자가 덮어쓴다.
 */
@Service
@RequiredArgsConstructor
public class VendorCatalogImporter {

    private static final Logger logger = LoggerFactory.getLogger(VendorCatalogImporter.class);

    private static final String ITEM_TYPE_SET = "SET";
    private static final String ITEM_TYPE_PART = "PART";

    private final VendorExcelParserFactory parserFactory;
    private final VendorRepository vendorRepository;
    private final VendorProductRepository vendorProductRepository;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    private final VendorProductRelationRepository vendorProductRelationRepository;
    private final ProposalLineRepository proposalLineRepository;
    private final ImportProgressStore progressStore;
    private final ExcelImageExtractor imageExtractor;
    private final ImageDownloadService imageDownloadService;

    /** 적재 결과 요약 — 총 세트 수와 대표품목 신규/갱신 수. */
    public record ImportResult(int total, int created, int updated, int removed) {}

    /**
     * 한 basis에서 <b>절반 넘게</b> 사라지면 지우지 않고 경고만 남긴다.
     *
     * <p>정상적인 재적재에서 절반이 없어질 리 없다. 그 정도면 파일을 잘못 골랐거나, basis 하나를
     * 두 파일이 나눠 갖고 있다는 뜻이다(실측으로는 그런 basis가 없지만, 파일이 늘면 생길 수 있다).
     * <b>가정이 틀렸을 때 데이터가 아니라 로그가 깨지게 한다.</b>
     */
    private static final double PURGE_RATIO_LIMIT = 0.5;

    /** upsertVendorProduct 결과 — 저장된 제품과 신규 생성 여부. */
    private record UpsertResult(VendorProduct product, boolean created) {}

    private static String resolveVendorName(String vendorCode) {
        return switch (vendorCode) {
            case "A" -> "아메리칸스탠다드";
            case "B" -> "이누스";
            default -> vendorCode + "사";
        };
    }

    /**
     * 파싱 + DB upsert를 한 트랜잭션으로 수행한다. 중간에 실패하면 그 실행분 전체가 롤백된다.
     * 재호출 시 코드 기준 upsert로 멱등(중복 행 없음).
     *
     * <p>예외는 삼키지 않고 그대로 던진다 — 롤백은 트랜잭션 경계를 예외가 빠져나가야 일어난다.
     * 진행률 표시·임시파일 정리는 호출자({@code CatalogImportAsyncService})의 몫이다.
     *
     * @param jobId 진행률 갱신용. 없으면(null) 진행률을 건드리지 않는다(테스트·동기 호출).
     */
    @Transactional
    public ImportResult importVendorCatalog(String vendorCode, Path savedPath, String jobId) {
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

        // 이번 실행에서 낡은 것을 이미 걷어낸 (대표품목, priceBasis). 같은 짝이 여러 세트에 걸릴 수 있으므로
        // "처음 만났을 때 한 번만" 지운다 — 매번 지우면 방금 넣은 앞 세트를 스스로 지운다. (G-1 / Task 3)
        Set<String> purged = new HashSet<>();

        // 이번 실행이 실제로 넣거나 갱신한 대표품목 가격행. 루프가 끝나면 "이 집합에 없는 것"이
        // 최신본에서 사라진 행이다(S2). 부속(PART)은 담지 않는다 — 공유 자원이라 정리 대상이 아니다.
        Set<Long> touched = new HashSet<>();

        // 이번 실행이 이미 쓴 «품번 없는 제품». 이름이 같은 다른 제품이 뒤따라와 같은 행을
        // 물어 하나로 접히는 것을 막는다 (matchCodelessProduct 참조).
        Set<Long> claimed = new HashSet<>();

        int done = 0;
        int created = 0;
        int updated = 0;
        for (VendorProductSet set : sets) {
            boolean mainCreated = saveSet(vendor, set, images, purged, touched, claimed);
            // 대표품목(세트) 단위 집계 — main 있는 세트만 카운트(빈 세트는 saveSet에서 null 처리)
            if (set.main() != null) {
                if (mainCreated) created++; else updated++;
            }
            done++;

            if (jobId != null) {
                int percent = 35 + (int) Math.floor(done * 64.0 / total);
                if (percent > 99) percent = 99;
                if (done % 10 == 0 || done == total) {
                    progressStore.update(jobId, percent, "DB 저장 중...");
                }
            }
        }
        if (jobId != null) progressStore.update(jobId, 99, "사라진 행 정리 중...");
        int removed = sweepVanishedRows(vendor, sets, touched);

        return new ImportResult(sets.size(), created, updated, removed);
    }

    /**
     * 최신본에서 <b>사라진</b> 대표품목 가격행과, 그 결과 남겨진 제품을 걷어낸다(S1·S3~S6).
     *
     * <p>임포트는 upsert라 갱신만 하고 삭제를 하지 않는다. 파일에서 빠진 행은 DB에 그대로 남아
     * 카탈로그에 현재 데이터인 얼굴로 선다. {@link #purgeStaleSetRows}는 <b>이번 파일에 여전히
     * 등장하는 제품</b>만 훑으므로 여기까지 닿지 않는다.
     *
     * <p><b>범위는 이번 업로드가 실제로 산출한 basis뿐이다.</b> 한 공급사를 여러 파일로 나눠 올리므로
     * 전체를 기준으로 지우면 이번에 올리지 않은 파일의 제품이 통째로 날아간다.
     *
     * <p>지우지 않는 것: 부속(PART) 가격행(공유 자원, D13), 제안서가 참조 중인 제품,
     * 그리고 {@link #PURGE_RATIO_LIMIT}를 넘는 basis 전체.
     *
     * @return 지운 대표품목 가격행 수
     */
    private int sweepVanishedRows(Vendor vendor, List<VendorProductSet> sets, Set<Long> touched) {
        // S1: 이번 업로드가 책임지는 범위
        Set<String> coveredBasis = sets.stream()
                .map(VendorProductSet::priceBasis)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 파싱이 아무것도 못 냈으면 손대지 않는다 — 빈 파일로 카탈로그를 비우는 사고를 막는다.
        if (coveredBasis.isEmpty()) return 0;

        List<VendorItemPrice> toRemove = new ArrayList<>();
        for (String basis : coveredBasis) {
            List<VendorItemPrice> inBasis = vendorItemPriceRepository
                    .findAllByVendorAndPriceTypeAndPriceBasis(vendor, ITEM_TYPE_SET, basis);

            List<VendorItemPrice> vanished = inBasis.stream()
                    .filter(p -> !touched.contains(p.getId()))
                    .toList();
            if (vanished.isEmpty()) continue;

            // S4: 비율 차단기
            if (vanished.size() > inBasis.size() * PURGE_RATIO_LIMIT) {
                logger.warn("[Import] basis '{}'에서 {}/{}건이 사라진 것으로 나온다 — 절반을 넘어 정리를 건너뛴다. "
                                + "파일을 잘못 골랐거나 이 basis가 여러 파일에 걸쳐 있는지 확인할 것.",
                        basis, vanished.size(), inBasis.size());
                continue;
            }
            toRemove.addAll(vanished);
        }
        if (toRemove.isEmpty()) return 0;

        // S5: 그 가격행이 걸고 있던 세트 관계부터 끊는다
        for (VendorItemPrice p : toRemove) {
            if (p.getSetHash() != null) {
                vendorProductRelationRepository
                        .deleteAllBySourceProductAndSetHash(p.getVendorProduct(), p.getSetHash());
            }
        }
        List<VendorProduct> affected = toRemove.stream()
                .map(VendorItemPrice::getVendorProduct)
                .distinct()
                .toList();
        vendorItemPriceRepository.deleteAll(toRemove);
        vendorItemPriceRepository.flush();

        // S6: 가격행이 하나도 안 남은 제품 정리. 제안서가 참조 중이면 남긴다 —
        //     productId에 외래키가 없어 지우면 끊어진 id만 조용히 남는다.
        Set<Long> referenced = proposalLineRepository.findReferencedProductIds();
        List<VendorProduct> orphans = affected.stream()
                .filter(p -> !referenced.contains(p.getId()))
                .filter(p -> vendorItemPriceRepository.findFirstByVendorAndVendorProduct(vendor, p).isEmpty())
                .toList();
        if (!orphans.isEmpty()) {
            orphans.forEach(vendorProductRelationRepository::deleteAllByProduct);
            vendorProductRepository.deleteAll(orphans);
        }

        logger.info("[Import] 최신본에서 사라진 대표품목 가격행 {}건 정리 (제품 {}건 삭제, basis {}종)",
                toRemove.size(), orphans.size(), coveredBasis.size());
        return toRemove.size();
    }

    /**
     * VendorProductSet 한 건을 대표품목 + 부속 + 관계 + 가격으로 적재.
     * @return 대표품목이 신규 생성됐으면 true(기존 갱신이면 false). 빈 세트(main 없음)는 false.
     */
    private boolean saveSet(Vendor vendor, VendorProductSet set,
                            Map<String, Map<Integer, ExtractedImage>> images,
                            Set<String> purged, Set<Long> touched, Set<Long> claimed) {
        VendorParsedItem mainItem = set.main();
        if (mainItem == null) return false;

        // 대표품목
        UpsertResult mainRes = upsertVendorProduct(
                vendor, mainItem.productCode(), mainItem.productName(),
                set.categoryLarge(), set.categorySmall(), ITEM_TYPE_SET, mainItem.description(), mainItem.specs(),
                mainItem.unit(), claimed);
        VendorProduct mainProduct = mainRes.product();

        // 이 (제품, priceBasis)의 낡은 대표품목 가격행·관계를 처음 만났을 때 한 번만 걷어낸다.
        // 임포터에 delete가 없어 세트 구성이 바뀌면 낡은 부속 연결이 남는다(Task 3).
        purgeStaleSetRows(vendor, mainProduct, set.priceBasis(), purged);

        // 임베디드 이미지 연결 (D15) — 대표품목 행에 앵커된 그림
        applyImage(mainProduct, set, images);

        // 대표품목 가격: 세트가 우선, 없으면 본품 단가
        BigDecimal mainPrice = set.setPrice() != null ? set.setPrice() : mainItem.unitPrice();
        String mainRemark = mainItem.remark();
        if (set.needsReview()) {
            mainRemark = appendRemark(mainRemark, "검수필요");
        }
        // 대표품목 가격은 price_basis별로 분리 보존 — 같은 품번이 시트마다 다른 가격일 때 충돌 방지.
        // priceBasis 기본값=categoryLarge(하위호환). 수전금구 3-시트처럼 대분류를 통합(=수전금구)하고
        // 가격만 시트별로 나눌 때는 파서가 priceBasis를 시트명으로 지정한다(§10 S2·S3).
        //
        // 여기에 세트 해시가 더 붙는다(G-1) — priceBasis만으로는 같은 품번의 여러 세트가 한 행으로
        // 접혀 세트가가 하나만 남는다. A사에서 19종의 서로 다른 세트가 24개가 그렇게 덮였다.
        // 본품 단가는 세트가와 별개로 남긴다(G-2) — A사는 세트가 = 본품 + 부속합이라
        // 이게 없으면 화면이 그 등식으로 대조할 수 없다. B사는 본품이 부속 목록 안이라 null.
        BigDecimal ownPrice = set.setPrice() != null ? mainItem.unitPrice() : null;
        // 이번 실행이 살린 행으로 표시한다 — 루프가 끝나면 표시되지 않은 것이 사라진 행이다(S2).
        touched.add(upsertPrice(vendor, mainProduct, mainItem, mainPrice, mainRemark, ITEM_TYPE_SET,
                set.priceBasis(), set.setHash(), set.partsSummary(), ownPrice).getId());

        // 부속품 + 관계
        //
        // 원본은 같은 부속이 2개 들어갈 때 행을 두 번 적는다(§8 잔여 ②). 관계 유일키가
        // (source, target, type)이라 그대로 돌면 한 건으로 접혀 부속 합계가 세트가에 못 미친다.
        // 먼저 같은 (부속, 슬롯)의 등장 횟수를 세고, 첫 등장에서 그 횟수를 수량으로 넘긴다.
        // LinkedHashMap이라 첫 등장 순서 = 엑셀 순서가 그대로 유지된다(관계 id 순 정렬이 이 순서에 기댄다).
        Map<String, Integer> partCounts = new LinkedHashMap<>();
        for (VendorParsedItem part : set.parts()) {
            partCounts.merge(partKey(part), 1, Integer::sum);
        }

        Set<String> handled = new HashSet<>();
        for (VendorParsedItem part : set.parts()) {
            if (!handled.add(partKey(part))) continue; // 2회차 이후는 수량으로 이미 반영됨

            // 부속 전용 소분류가 있으면 그것으로(§10 S4: 국산/OEM 출처). 없으면 세트 소분류.
            String partCategorySmall = part.categorySmall() != null ? part.categorySmall() : set.categorySmall();
            VendorProduct partProduct = upsertVendorProduct(
                    vendor, part.productCode(), part.productName(),
                    set.categoryLarge(), partCategorySmall, ITEM_TYPE_PART, part.description(), part.specs(),
                    part.unit(), claimed).product();

            // 공유 부속 단가는 코드당 1건 유지(D13) → priceBasis=null, setHash=null
            upsertPrice(vendor, partProduct, part, part.unitPrice(), part.remark(), ITEM_TYPE_PART,
                    null, null, null, null);
            upsertRelation(mainProduct, partProduct, part.relationType(),
                    partCounts.get(partKey(part)), set.setHash(), part.productName());
        }
        return mainRes.created();
    }

    /**
     * 재적재 시 이 대표품목의 <b>낡은</b> 대표품목 가격행과 관계를 걷어낸다.
     * 이번 실행에서 처음 만난 제품에 대해서만 한 번 수행한다.
     *
     * <p><b>매번 지우면 안 된다</b> — 같은 제품이 여러 세트의 대표품목이면 방금 넣은 앞 세트를
     * 스스로 지운다. A사에는 대표품목 하나에 세트가 최대 13개인 것도 있다.
     *
     * <p>부속(PART) 가격행은 건드리지 않는다 — 공유 자원이라 코드당 1건을 유지한다(D13).
     * 다른 세트가 여전히 그 부속을 참조한다.
     */
    private void purgeStaleSetRows(Vendor vendor, VendorProduct mainProduct,
                                   String priceBasis, Set<String> purged) {
        if (mainProduct.getId() == null || priceBasis == null) return;
        if (!purged.add(mainProduct.getId() + "\u0000" + priceBasis)) return;

        List<VendorItemPrice> stale = vendorItemPriceRepository
                .findAllByVendorAndVendorProductAndPriceTypeAndPriceBasis(
                        vendor, mainProduct, ITEM_TYPE_SET, priceBasis);

        // 그 가격행들이 가리키던 세트의 관계만 지운다. 다른 basis(=다른 파일)의 세트는 그대로 둔다.
        stale.stream()
                .map(VendorItemPrice::getSetHash)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(h -> vendorProductRelationRepository.deleteAllBySourceProductAndSetHash(mainProduct, h));

        // 세트 축 도입 전에 쌓인 관계(setHash=null)는 재적재 한 번으로 정리된다.
        vendorProductRelationRepository.deleteAllBySourceProductAndSetHashIsNull(mainProduct);

        if (!stale.isEmpty()) vendorItemPriceRepository.deleteAll(stale);
    }

    /**
     * 부속 동일성 키 — 관계 유일키 {@code (target, relationType)}와 같은 축.
     * 코드가 없는 부속(A사 신품번 없음 등)은 이름으로 대신한다.
     */
    private static String partKey(VendorParsedItem part) {
        String id = part.productCode() != null ? part.productCode() : part.productName();
        return id + "\u0000" + part.relationType();
    }

    /**
     * 대표품목 행에 앵커된 임베디드 이미지를 저장하고 imageUrl을 연결(D15). 없으면 무시.
     *
     * <p><b>이미 이미지가 있으면 건드리지 않는다</b> — 이미지의 정본은 크롤러다.
     * 크롤러 핸들러들이 "이미지와 출처만 갱신한다. productName·collectionName은 단가표 쪽이 정본"이라고
     * 소유권을 나눠 놓았는데, 여기서 무조건 덮으면 단가표를 다시 올리는 것만으로
     * 크롤링 이미지가 저해상 임베디드 썸네일로 되돌아간다(F-010).
     * 비어 있는 제품만 채워 <b>단가표는 빈칸을 메우고, 갱신은 크롤러가 한다</b>는 관계를 지킨다.
     *
     * <p>저장한 이미지 파일 자체는 트랜잭션 대상이 아니다 — 적재가 롤백돼도 파일은 디스크에 남는다.
     * 다음 업로드에서 같은 이름으로 덮어쓰이므로 누적 위험은 없다.
     */
    private void applyImage(VendorProduct mainProduct, VendorProductSet set,
                            Map<String, Map<Integer, ExtractedImage>> images) {
        if (set.imageKey() == null || images == null || images.isEmpty()) return;

        // 이미 붙어 있는 이미지는 지키고 디스크 쓰기도 건너뛴다(F-010).
        String existing = mainProduct.getImageUrl();
        if (existing != null && !existing.isBlank()) return;

        // 이미지 맵은 시트명 키(ExcelImageExtractor). 대분류를 시트명에서 분리·정제하는 시트(비데/기타·갈라시아 등)도
        // set.sheetName()으로 원본 시트명을 보존하므로 categoryLarge와 무관하게 시트명 기준으로 조회한다(§13 sheetName 분리).
        Map<Integer, ExtractedImage> byRow = images.get(set.sheetName());
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

    /**
     * 품번 없는 제품을 이름으로 되찾는다 — <b>분류가 바뀌어도</b> 같은 제품으로 알아본다.
     *
     * <p>예전에는 {@code 이름 + 대분류 + 소분류}로만 찾았다. 그래서 <b>분류를 재편하는 재적재에서
     * 매칭이 통째로 빗나가 중복이 생겼다</b>(2026-09-02에 6건). 소분류가 없는 구간(A사 액세서리 등)에서는
     * 아예 조회를 건너뛰어 매번 새로 만들기까지 했다.
     *
     * <p>그렇다고 이름만으로 묶으면 반대로 <b>서로 다른 제품이 한 행으로 병합된다</b> — 이름이 같고
     * 분류가 다른 품번 없는 항목이 한 파일에 둘 있을 수 있다. 그래서 순서를 둔다:
     *
     * <ol>
     *   <li>이름 + 분류가 그대로 맞는 것이 있으면 그것 (종전 동작)</li>
     *   <li>없으면, 이름이 같은 품번 없는 제품이 <b>딱 하나</b>이고 <b>이번 실행이 아직 쓰지 않았다면</b>
     *       그것 — 분류만 바뀐 같은 제품으로 본다</li>
     *   <li>여럿이면 가리지 않고 새로 만든다 (종전과 같은 결과. 잘못 합치느니 늘어나는 편이 낫다)</li>
     * </ol>
     *
     * <p>{@code claimed}가 2)의 안전장치다. 이게 없으면 이름이 같은 두 제품을 한 파일에서 읽을 때
     * <b>두 번째가 첫 번째를 물어 하나로 접힌다.</b> 이미 이번에 쓴 행은 후보에서 뺀다.
     */
    private VendorProduct matchCodelessProduct(Vendor vendor, String productName,
                                               String categoryLarge, String categorySmall,
                                               Set<Long> claimed) {
        List<VendorProduct> sameName =
                vendorProductRepository.findAllByVendorAndProductCodeIsNullAndProductName(vendor, productName);
        if (sameName.isEmpty()) return null;

        // 1) 분류까지 그대로 맞는 것
        VendorProduct exact = sameName.stream()
                .filter(p -> !claimed.contains(p.getId()))
                .filter(p -> Objects.equals(p.getCategoryLarge(), categoryLarge)
                        && Objects.equals(p.getCategorySmall(), categorySmall))
                .findFirst()
                .orElse(null);
        if (exact != null) return exact;

        // 2) 분류가 달라졌을 뿐인 같은 제품 — 후보가 하나로 좁혀질 때만
        List<VendorProduct> free = sameName.stream()
                .filter(p -> !claimed.contains(p.getId()))
                .toList();
        if (free.size() == 1) {
            VendorProduct only = free.get(0);
            logger.info("[Import] 품번 없는 제품 '{}'의 분류가 바뀐 것으로 보고 같은 행에 잇는다: {}/{} → {}/{}",
                    productName, only.getCategoryLarge(), only.getCategorySmall(), categoryLarge, categorySmall);
            return only;
        }

        // 3) 모호하면 새로 만든다
        return null;
    }

    private UpsertResult upsertVendorProduct(Vendor vendor, String productCode, String productName,
                                             String categoryLarge, String categorySmall, String itemType,
                                             String description, String specs, String unit,
                                             Set<Long> claimed) {
        VendorProduct product = null;

        // 1) 코드(품번)가 있으면 코드로만 식별 — 공급사 범위 내.
        //    (이름이 같은 부속(예: "시트","도기")이 코드만 다른 경우 2)로 넘어가면 한 행으로 잘못 병합되므로
        //     코드가 있으면 이름 fallback을 타지 않는다.)
        if (productCode != null) {
            product = vendorProductRepository.findByVendorAndProductCode(vendor, productCode).orElse(null);
        }
        // 2) 코드가 아예 없는 항목(A사 신품번 없음 등)은 이름으로 멱등 매칭
        else if (productName != null) {
            product = matchCodelessProduct(vendor, productName, categoryLarge, categorySmall, claimed);
        }

        // 3) 신규
        boolean created = false;
        if (product == null) {
            product = new VendorProduct();
            product.setProductCode(productCode);
            created = true;
        }

        product.setVendor(vendor);
        product.setProductName(productName);
        product.setCategoryLarge(categoryLarge);
        product.setCategorySmall(categorySmall);
        product.setItemType(itemType);
        if (description != null && !description.isBlank()) product.setDescription(description);
        if (specs != null && !specs.isBlank()) product.setSpecs(specs);
        // 단위는 원본에 단위 컬럼이 있는 시트만 채워 온다(현재 B사 부속류). null이면 손대지 않아
        // 엔티티 기본값(SET)이 그대로 남는다 — 다른 시트의 기존 값이 바뀌지 않는다.
        if (unit != null && !unit.isBlank()) product.setUnit(unit);

        // A사 masterCode/detailCode 분리 (신품번 '-' 기준)
        if ("A".equals(vendor.getVendorCode()) && productCode != null) {
            String[] codes = productCode.split("-", 2);
            product.setMasterCode(codes[0].trim());
            product.setDetailCode(codes.length > 1 && !codes[1].isBlank() ? codes[1].trim() : null);
        }
        // B사 masterCode/detailCode 분리 (대표품번_부속코드 '_' 기준). 대표품목은 detail 없음.
        else if ("B".equals(vendor.getVendorCode()) && productCode != null) {
            int u = productCode.indexOf('_');
            if (u > 0) {
                product.setMasterCode(productCode.substring(0, u).trim());
                String detail = productCode.substring(u + 1).trim();
                product.setDetailCode(detail.isBlank() ? null : detail);
            } else {
                product.setMasterCode(productCode);
                product.setDetailCode(null);
            }
        }

        if (isBlank(product.getProductCode()) && productCode != null) {
            product.setProductCode(productCode);
        }

        VendorProduct saved = vendorProductRepository.save(product);
        // 품번 없는 제품만 이름으로 되찾으므로, 이번 실행이 쓴 것을 적어 둔다.
        // 이름이 같은 다른 제품이 뒤따라와 이 행을 물지 않게 한다.
        if (saved.getProductCode() == null) claimed.add(saved.getId());
        return new UpsertResult(saved, created);
    }

    /**
     * 가격 upsert. {@code priceBasis}(출처 시트)가 있으면 (vendor,product,proposalCode,basis) 기준으로
     * 분리 저장 — 같은 품번이 시트별로 다른 가격(대표품목)일 때 충돌 방지. basis=null이면 코드당 1건(D13).
     */
    /** @return 저장된 가격행. 호출부가 "이번 실행이 살린 행"으로 표시하는 데 쓴다. */
    private VendorItemPrice upsertPrice(Vendor vendor, VendorProduct product, VendorParsedItem item,
                            BigDecimal price, String remark, String priceType, String priceBasis,
                            String setHash, String setSummary, BigDecimal mainUnitPrice) {
        String proposalCode = item.productCode();

        VendorItemPrice vip;
        if (proposalCode != null && priceBasis != null && setHash != null) {
            // 대표품목(SET) — 세트별로 행이 갈린다 (G-1)
            vip = vendorItemPriceRepository
                    .findByVendorAndVendorProductAndProposalItemCodeAndPriceBasisAndSetHash(
                            vendor, product, proposalCode, priceBasis, setHash)
                    .orElse(null);
        } else if (proposalCode != null && priceBasis != null) {
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
        vip.setSetHash(setHash);
        vip.setSetSummary(setSummary);
        vip.setMainUnitPrice(mainUnitPrice);
        vip.setCurrency("KRW");

        return vendorItemPriceRepository.save(vip);
    }

    private void upsertRelation(VendorProduct source, VendorProduct target, String relationType,
                                int quantity, String setHash, String partName) {
        if (source.getId() != null && source.getId().equals(target.getId())) return; // 자기 참조 방지

        String rel = (relationType != null && !relationType.isBlank())
                ? relationType
                : VendorParsedItem.RELATION_ACCESSORY;
        if (rel.length() > 50) rel = rel.substring(0, 50); // relation_type 컬럼 길이 방어

        int qty = Math.max(1, quantity);

        // 재적재 멱등: 이미 있으면 수량만 맞춘다. 원본에서 개수가 바뀌면 그 값이 반영돼야 한다.
        // 유일키에 setHash가 들어간다(G-1) — 같은 대표품목의 다른 세트가 같은 부속을 써도 별개 관계다.
        VendorProductRelation existing = vendorProductRelationRepository
                .findBySourceProductAndTargetProductAndRelationTypeAndSetHash(source, target, rel, setHash)
                .orElse(null);
        if (existing != null) {
            boolean dirty = false;
            if (!Integer.valueOf(qty).equals(existing.getQuantity())) { existing.setQuantity(qty); dirty = true; }
            if (partName != null && !partName.equals(existing.getPartName())) {
                existing.setPartName(partName); dirty = true;
            }
            if (dirty) vendorProductRelationRepository.save(existing);
            return;
        }

        vendorProductRelationRepository.save(
                VendorProductRelation.builder()
                        .sourceProduct(source)
                        .targetProduct(target)
                        .relationType(rel)
                        .quantity(qty)
                        .setHash(setHash)
                        .partName(partName)
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
