package com.example.esti.excel;

import com.example.esti.excel.VendorExcelParserFactory.FormatMismatch;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 업로드 파일이 선택한 공급사 양식과 모순되는지의 판정 규칙.
 * 커밋된 합성 샘플만 쓰므로 실샘플 없이 CI에서 돈다.
 */
class VendorExcelParserFactoryTest {

    private static final Path SAMPLE_A = Path.of("src/main/resources/static/samples/vendor-a-sample.xlsx");
    private static final Path SAMPLE_B = Path.of("src/main/resources/static/samples/vendor-b-sample.xlsx");

    private final VendorExcelParserFactory factory =
            new VendorExcelParserFactory(List.of(new VendorAExcelParser(), new VendorBExcelParser()));

    @Test
    void 제_양식을_제_공급사로_올리면_통과() {
        assertEquals(Optional.empty(), factory.findFormatMismatch("A", SAMPLE_A));
        assertEquals(Optional.empty(), factory.findFormatMismatch("B", SAMPLE_B));
    }

    @Test
    void 코드_대소문자는_가리지_않는다() {
        assertEquals(Optional.empty(), factory.findFormatMismatch("b", SAMPLE_B));
    }

    /** 사용자 보고의 원래 모양 — B 샘플을 받아 공급사를 A로 둔 채 올린다. 알아본 공급사를 알려준다. */
    @Test
    void B_양식을_A로_올리면_B로_보인다고_알려준다() {
        assertEquals(Optional.of(new FormatMismatch("A", "B")), factory.findFormatMismatch("A", SAMPLE_B));
    }

    /** A는 양성 판별 근거가 없어 «A로 보인다»고는 못 하고, B가 «아니다»라고만 한다. */
    @Test
    void A_양식을_B로_올리면_모순이지만_알아본_공급사는_없다() {
        assertEquals(Optional.of(new FormatMismatch("B", null)), factory.findFormatMismatch("B", SAMPLE_A));
    }

    /** ①이 ②보다 먼저다 — 선택한 쪽이 NO이고 다른 쪽이 YES면, 알아본 공급사를 알려주는 쪽이 더 쓸모 있다. */
    @Test
    void 다른_공급사가_알아보면_선택한_쪽_판정보다_먼저_알려준다() {
        VendorExcelParserFactory f = new VendorExcelParserFactory(List.of(
                stub("X", VendorExcelParser.Recognition.NO),
                stub("Y", VendorExcelParser.Recognition.YES)));

        assertEquals(Optional.of(new FormatMismatch("X", "Y")), f.findFormatMismatch("X", SAMPLE_A));
    }

    @Test
    void 모두_판정_근거가_없으면_사용자_선언을_따른다() {
        VendorExcelParserFactory f = new VendorExcelParserFactory(List.of(
                stub("X", VendorExcelParser.Recognition.UNKNOWN),
                stub("Y", VendorExcelParser.Recognition.UNKNOWN)));

        assertEquals(Optional.empty(), f.findFormatMismatch("X", SAMPLE_A));
    }

    @Test
    void 파서_없는_코드는_기존대로_거부() {
        assertThrows(IllegalArgumentException.class, () -> factory.findFormatMismatch("Z", SAMPLE_A));
    }

    private static VendorExcelParser stub(String code, VendorExcelParser.Recognition verdict) {
        return new VendorExcelParser() {
            @Override public String getVendorCode() { return code; }
            @Override public List<VendorProductSet> parseSets(Path path) { return List.of(); }
            @Override public Recognition recognize(Path path) { return verdict; }
        };
    }
}
