package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ImageDownloadService;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이누스 매칭 규칙 ({@code plan-inus-crawler.md} I-9).
 *
 * <p>합성 픽스처만 쓴다 — 실단가·전산코드는 저장소에 두지 않는다(CLAUDE.md).
 * 품번은 카탈로그에 공개되는 식별자라 그대로 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InusProductSyncHandlerTest {

    private static final String VENDOR_CODE = "B";

    @Mock private ImageDownloadService imageDownloadService;
    @Mock private VendorProductRepository vendorProductRepository;
    @InjectMocks private InusProductSyncHandler handler;

    private final List<VendorProduct> rows = new ArrayList<>();
    private long nextId = 1L;

    @BeforeEach
    void stubDownloadAndLookup() throws Exception {
        when(imageDownloadService.download(anyString(), anyString()))
                .thenReturn(new ImageDownloadService.DownloadResult(
                        "/abs/x.png", "/uploads/product-images/x.png"));

        when(vendorProductRepository.findAllById(any())).thenAnswer(inv -> {
            List<Long> ids = new ArrayList<Long>();
            for (Long id : (Iterable<Long>) inv.getArgument(0)) {
                ids.add(id);
            }
            return rows.stream().filter(r -> ids.contains(r.getId())).toList();
        });
    }

    private VendorProduct row(String productCode, String itemType, String imageUrl) {
        VendorProduct p = VendorProduct.builder()
                .vendor(new Vendor(1L, VENDOR_CODE, "이누스"))
                .productCode(productCode)
                .productName("단가표 이름")
                .itemType(itemType)
                .imageUrl(imageUrl)
                .build();
        p.setId(nextId++);
        rows.add(p);
        return p;
    }

    /** 인덱스는 SET 행만 담는다 — 리포지토리도 SET만 돌려주도록 흉내 낸다. */
    private Object prepare() {
        when(vendorProductRepository.findAllByVendor_VendorCodeAndItemType(VENDOR_CODE, "SET"))
                .thenReturn(rows.stream().filter(r -> "SET".equals(r.getItemType())).toList());
        return handler.prepare(VENDOR_CODE);
    }

    private CrawledProduct site(String productCode, String aliasCode) {
        return CrawledProduct.builder()
                .maker("INUS").vendorCode(VENDOR_CODE)
                .productCode(productCode)
                .productName("사이트 품목명")
                .rawTagText(aliasCode)
                .imageUrl("http://site/upload/a.png")
                .build();
    }

    private SyncMatchCounters counters(Object ctx) {
        return (SyncMatchCounters) ctx;
    }

    @Test
    @DisplayName("정규화가 같으면 매칭된다 — 영숫자 외 문자와 대소문자는 무시한다")
    void matchesAfterNormalization() {
        VendorProduct target = row("C853-2", "SET", null);
        Object ctx = prepare();

        handler.save(site("c8532", null), ctx);

        assertThat(target.getImageUrl()).isEqualTo("/uploads/product-images/x.png");
        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("별칭 품번으로도 매칭된다 — 구형 품번이 병기된 제품이 있다")
    void matchesByAliasCode() {
        VendorProduct target = row("L610", "SET", null);
        Object ctx = prepare();

        // 사이트 품번은 DB에 없고 별칭만 있는 경우
        handler.save(site("IBL1681", "L610"), ctx);

        assertThat(target.getImageUrl()).isNotNull();
        assertThat(counters(ctx).exactMatched()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB에 없는 사이트 코드는 행을 만들지 않는다 — 단가 없는 제품을 카탈로그에 넣지 않는다")
    void neverCreatesRowsForUnknownCodes() throws Exception {
        row("IL401", "SET", null);
        Object ctx = prepare();

        handler.save(site("NOTINDB999", null), ctx);

        verify(vendorProductRepository, never()).save(any());
        verify(imageDownloadService, never()).download(anyString(), anyString());
        assertThat(counters(ctx).notInDb()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isZero();
    }

    @Test
    @DisplayName("PART는 인덱스에 없어 대상에서 빠진다 — 부속끼리 같은 사진을 달게 된다")
    void excludesParts() {
        VendorProduct part = row("IL401", "PART", null);
        Object ctx = prepare();

        handler.save(site("IL401", null), ctx);

        assertThat(part.getImageUrl()).isNull();
        assertThat(counters(ctx).notInDb()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 이미지가 있어도 덮어쓴다 — 사이트 공식 이미지로 교체한다")
    void overwritesExistingImage() {
        VendorProduct target = row("IL401", "SET", "/uploads/product-images/old.jpg");
        Object ctx = prepare();

        handler.save(site("IL401", null), ctx);

        assertThat(target.getImageUrl()).isEqualTo("/uploads/product-images/x.png");
        assertThat(counters(ctx).rowsReplaced()).isEqualTo(1);
        assertThat(counters(ctx).rowsFilled()).isZero();
    }

    @Test
    @DisplayName("제품명은 덮지 않는다 — 단가표 쪽이 정본이다")
    void keepsProductNameFromPriceList() {
        VendorProduct target = row("IL401", "SET", null);
        Object ctx = prepare();

        handler.save(site("IL401", null), ctx);

        assertThat(target.getProductName()).isEqualTo("단가표 이름");
    }

    @Test
    @DisplayName("한 품번이 여러 행에 걸리면 전부 갱신한다")
    void updatesEveryMatchingRow() {
        VendorProduct a = row("IL401", "SET", null);
        VendorProduct b = row("IL-401", "SET", null);   // 정규화하면 같은 품번
        Object ctx = prepare();

        handler.save(site("IL401", null), ctx);

        assertThat(a.getImageUrl()).isNotNull();
        assertThat(b.getImageUrl()).isNotNull();
        assertThat(counters(ctx).rowsAffected()).isEqualTo(2);
        assertThat(counters(ctx).rowsFilled()).isEqualTo(2);
    }

    @Test
    @DisplayName("완화 후보는 목록에만 담고 반영하지 않는다 (G-1 ⓒ)")
    void reportsRelaxedCandidatesWithoutApplyingThem() throws Exception {
        VendorProduct target = row("IC858RP", "SET", null);
        Object ctx = prepare();

        // 사이트 코드가 3자 더 길다 — 색상·사양 변형인 다른 제품일 수 있다
        handler.save(site("IC858RPG1", null), ctx);

        assertThat(target.getImageUrl()).isNull();
        verify(imageDownloadService, never()).download(anyString(), anyString());
        assertThat(counters(ctx).relaxedOnly()).isEqualTo(1);
        assertThat(counters(ctx).relaxedCandidates()).containsExactly("IC858RPG1 ↔ IC858RP");
    }

    @Test
    @DisplayName("4자 이상 차이는 완화 후보로도 보지 않는다")
    void ignoresCandidatesBeyondTheLengthLimit() {
        row("IC858", "SET", null);
        Object ctx = prepare();

        handler.save(site("IC858ABCD", null), ctx);

        assertThat(counters(ctx).relaxedOnly()).isZero();
        assertThat(counters(ctx).notInDb()).isEqualTo(1);
    }

    @Test
    @DisplayName("dry-run은 아무것도 바꾸지 않으면서 실반영과 같은 숫자를 낸다")
    void inspectCountsWithoutChangingAnything() throws Exception {
        VendorProduct missing = row("IL401", "SET", null);
        VendorProduct existing = row("IL672", "SET", "/uploads/product-images/old.jpg");
        Object ctx = prepare();

        handler.inspect(site("IL401", null), ctx);
        handler.inspect(site("IL672", null), ctx);

        assertThat(missing.getImageUrl()).isNull();
        assertThat(existing.getImageUrl()).isEqualTo("/uploads/product-images/old.jpg");
        verify(imageDownloadService, never()).download(anyString(), anyString());
        verify(vendorProductRepository, never()).save(any());

        assertThat(counters(ctx).exactMatched()).isEqualTo(2);
        assertThat(counters(ctx).rowsAffected()).isEqualTo(2);
        assertThat(counters(ctx).rowsFilled()).isEqualTo(1);      // 결손 충전 예정
        assertThat(counters(ctx).rowsReplaced()).isEqualTo(1);    // 교체 예정
    }

    @Test
    @DisplayName("이미지가 없는 수집 결과는 건너뛴다")
    void skipsProductsWithoutImage() {
        row("IL401", "SET", null);
        Object ctx = prepare();

        handler.save(CrawledProduct.builder()
                .maker("INUS").vendorCode(VENDOR_CODE).productCode("IL401").build(), ctx);

        assertThat(counters(ctx).skippedNoImage()).isEqualTo(1);
        assertThat(counters(ctx).rowsAffected()).isZero();
    }

    @Test
    @DisplayName("컨텍스트 없이 저장하려 하면 조용히 넘어가지 않고 막는다")
    void refusesToSaveWithoutIndex() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> handler.save(site("IL401", null))))
                .isInstanceOf(IllegalStateException.class);
    }
}
