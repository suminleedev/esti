package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * 악세사리 단가표 전용 검증(§12 A1~A7): parseHeaderTotalSetSheet 고도화.
 *
 * <p>대분류=악세사리(C-1 '단가표' 제거, 이미지=시트명 imageKey D52), priceBasis=시트명.
 * 품명 정비(ditto·빈칸·숫자 오염 폴백), 규격=description, 비고(J)=remark 잠정(R7),
 * U접두 필터 3종은 수전부속 U9120(자동폽업) 충돌 회피로 {품번}-{전산코드} 결합.</p>
 * <p>시트별 픽스처(로컬 전용, git 미추적)가 없으면 스킵.</p>
 */
class VendorBAccessorySheetTest {

    private static final Path FIXTURE = Path.of("docs/samples/B사 test (악세사리 단가표).xlsx");
    private static final String BASIS = "악세사리 단가표";

    private final VendorBExcelParser parser = new VendorBExcelParser();

    private List<VendorProductSet> parseFixture() {
        requireSample(FIXTURE);
        return parser.parseSets(FIXTURE);
    }

    private VendorProductSet one(List<VendorProductSet> sets, String code) {
        return sets.stream().filter(s -> s.main() != null && code.equals(s.main().productCode()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " 미발견"));
    }

    private VendorParsedItem part(VendorProductSet s, String code) {
        return s.parts().stream().filter(p -> code.equals(p.productCode()))
                .findFirst().orElseThrow(() -> new AssertionError(code + " 부속 미발견"));
    }

    @Test
    void 대분류_악세사리_basis_시트명_이미지_시트명키() {
        List<VendorProductSet> sets = parseFixture();
        VendorProductSet ac8100 = one(sets, "AC8100");
        assertEquals("악세사리", ac8100.categoryLarge(), "C-1: '단가표' 제거");
        assertEquals(BASIS, ac8100.priceBasis(), "가격은 시트명 basis");
        assertEquals(BASIS, ac8100.sheetName(), "대분류≠시트명 → 이미지는 sheetName으로 매칭(§13)");
        assertEquals(0, new BigDecimal("60000").compareTo(ac8100.setPrice()));
        assertEquals(4, ac8100.parts().size());
    }

    @Test
    void 세트_대표행_AB빈칸도_소분류carry_비고보존() {
        List<VendorProductSet> sets = parseFixture();
        // AC8300G 대표행은 A/B 빈칸 → carry로 소분류 보완(A7)
        VendorProductSet g = one(sets, "AC8300G");
        assertEquals("4품 세트", g.categorySmall());
        assertEquals(5, g.parts().size());

        // 단종 예정 세트: 대표·부속 모두 비고 보존(A3, R7 잠정)
        VendorProductSet ac5200 = one(sets, "AC5200");
        assertEquals("단종 예정", ac5200.main().remark());
        assertEquals("단종 예정", part(ac5200, "AC5200_AC5201").remark());
        // 옵션 부속(옷걸이, 가격 없음)도 관계 유지 + 비고 '옵션'
        assertEquals("옵션", part(one(sets, "AC5400"), "AC5400_AC5405").remark());
    }

    @Test
    void 품명_숫자오염과_빈칸은_분류규격_또는_직전품명으로_폴백() {
        List<VendorProductSet> sets = parseFixture();
        // F=5000(숫자 오염): AN0521은 B 빈칸 → 직전 실품명, AN0731은 B=잡지꽃이 → 분류+규격(A5)
        assertEquals("노출형 휴지걸이", one(sets, "AN0521").main().productName());
        assertEquals("잡지꽃이 (일반형)", one(sets, "AN0731").main().productName());
        // F 빈칸(장애우손잡이 분리형 변형) → 직전 실품명 승계(A6)
        assertEquals("세면기 손잡이 (연결형)", one(sets, "AG0122").main().productName());
        // ditto(") 품명 승계는 종전대로
        assertEquals("세면기 손잡이", one(sets, "AG0213").main().productName());
    }

    @Test
    void 규격은_description으로_보존되어_동명이규격_구분() {
        List<VendorProductSet> sets = parseFixture();
        // 페이퍼타올 4종: 품명 동일, 규격(300*750~1400)만 다름 → description으로 구분(A2)
        assertEquals("300*750", one(sets, "AP0512H").main().description());
        assertEquals("300*1400", one(sets, "AP0515H").main().description());
        // 품명과 동일한 규격(수건걸이/수건걸이)은 생략 — 단일품 AT1322S(2단 수건선반)로 확인
        assertNull(one(sets, "AT1322S").main().description());
    }

    @Test
    void U접두_필터는_품번_전산코드_결합으로_수전부속과_분리() {
        List<VendorProductSet> sets = parseFixture();
        // U9120(수전부속=자동폽업)과 다른 실물(핸드스프레이 필터) → {품번}-{전산코드}(A1, 사용자 결정)
        assertEquals(0, new BigDecimal("6500").compareTo(one(sets, "U9120-43u0120").setPrice()));
        assertEquals(0, new BigDecimal("5300").compareTo(one(sets, "U9120B-43u0120b").setPrice()));
        assertEquals(0, new BigDecimal("3000").compareTo(one(sets, "U9120SF-43u0120sf").setPrice()));
        assertTrue(sets.stream().noneMatch(s -> "U9120".equals(s.main().productCode())),
                "악세사리 시트에서 맨 품번 U9120 미생성(충돌 차단)");
    }
}
