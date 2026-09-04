package com.example.esti.output;

import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제안서 카드 그리드 출력 검증 (P2).
 *
 * <p>기대값은 `docs/samples/제안서_sample.xlsx` 실측에서 왔다 — 병합 범위, 8행 라벨 순서,
 * 머리글 3줄(총액·세대당·열별 소계)의 좌표가 그것이다.
 *
 * <p>가장 중요한 검증은 <b>사입가·마진율이 파일에 없다는 것</b>이다. 제안서는 고객 제출용이라
 * 그 두 값이 새면 사고다(O-6).
 */
class ProposalCardExcelWriterTest {

    /** 이미지 삽입 검증용. `uploads/`를 건드리지 않도록 target 아래 상대경로를 쓴다. */
    private static final Path IMAGE = Path.of("target", "test-card-images", "card.png");

    @BeforeAll
    static void 임시_이미지_생성() throws Exception {
        Files.createDirectories(IMAGE.getParent());
        BufferedImage img = new BufferedImage(300, 600, BufferedImage.TYPE_INT_RGB); // 세로로 긴 제품
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 300, 600);
        g.dispose();
        ImageIO.write(img, "png", IMAGE.toFile());
    }

    @Test
    @DisplayName("머리글 3줄 — 총액 = 세대당 × 세대수, 열별 소계는 그 열 카드의 (단가×수량) 합")
    void 머리글_집계() throws Exception {
        Workbook wb = render(sampleProposal(), sampleLines());
        Sheet sheet = wb.getSheetAt(0);

        // 1열 = 양변기 152,000×1 + 세면기 69,000×2 = 290,000
        assertThat(numeric(sheet, 3, 2)).isEqualByComparingTo("290000");
        // 2열 = 씽크수전 57,000×1
        assertThat(numeric(sheet, 3, 5)).isEqualByComparingTo("57000");
        // 3열(유상옵션)은 소계 칸을 비운다 — 계약금액이 아니라 별도 청구분이다
        assertThat(text(sheet, 3, 8)).isEmpty();
        // 4열 = 수건걸이 14,000×2
        assertThat(numeric(sheet, 3, 11)).isEqualByComparingTo("28000");

        // 세대당 = 옵션 열을 뺀 합. 비데 143,000은 카드에는 보이되 합계에는 안 들어간다
        BigDecimal perHousehold = new BigDecimal("375000");   // 290,000 + 57,000 + 28,000
        assertThat(text(sheet, 2, 9)).isEqualTo("세대당");
        assertThat(numeric(sheet, 2, 10)).isEqualByComparingTo(perHousehold);
        assertThat(numeric(sheet, 1, 10)).isEqualByComparingTo(perHousehold.multiply(BigDecimal.valueOf(523)));

        // R4 라벨 자리에는 제안서 기준 평형
        assertThat(text(sheet, 3, 1)).isEqualTo("59㎡");
    }

    @Test
    @DisplayName("유상옵션 카드는 금액이 보이되 세대당 합계에는 빠진다")
    void 옵션은_합계에서_제외() throws Exception {
        List<ProposalLine> withOption = sampleLines();
        List<ProposalLine> withoutOption = new ArrayList<>(withOption);
        withoutOption.removeIf(l -> Boolean.TRUE.equals(l.getOptional()));

        Sheet a = render(sampleProposal(), withOption).getSheetAt(0);
        Sheet b = render(sampleProposal(), withoutOption).getSheetAt(0);

        // 옵션이 있든 없든 세대당·총액이 같아야 한다
        assertThat(numeric(a, 2, 10)).isEqualByComparingTo(numeric(b, 2, 10));
        assertThat(numeric(a, 1, 10)).isEqualByComparingTo(numeric(b, 1, 10));

        // 그래도 옵션 카드의 금액 자체는 찍힌다 (3열 첫 카드의 금액 행)
        assertThat(numeric(a, 8, 8)).isEqualByComparingTo("143000");
    }

    @Test
    @DisplayName("카드 8행 — 라벨 순서와 값 매핑이 샘플과 같다")
    void 카드_8행_구조() throws Exception {
        Workbook wb = render(sampleProposal(), sampleLines());
        Sheet sheet = wb.getSheetAt(0);

        int top = 4;          // R5
        int label = 1, value = 2;   // B, C

        assertThat(text(sheet, top, label)).isEqualTo("평형");
        assertThat(text(sheet, top, value)).isEqualTo("59㎡");
        assertThat(text(sheet, top + 1, label)).isEqualTo("부위");
        assertThat(text(sheet, top + 1, value)).isEqualTo("공용욕실");

        // 3번째 행은 라벨 없이 병합 — 품번만 들어간다
        assertThat(text(sheet, top + 2, label)).isEqualTo("IC702E");
        assertThat(merged(sheet, new CellRangeAddress(top + 2, top + 2, label, value))).isTrue();

        assertThat(text(sheet, top + 3, label)).isEqualTo("사양");
        assertThat(text(sheet, top + 3, value)).isEqualTo("투피스양변기");
        assertThat(text(sheet, top + 4, label)).isEqualTo("금액");
        assertThat(numeric(sheet, top + 4, value)).isEqualByComparingTo("152000");
        assertThat(text(sheet, top + 5, label)).isEqualTo("업체");
        assertThat(text(sheet, top + 5, value)).isEqualTo("이누스 주식회사");

        // 원산지는 라벨만 남기고 값은 비운다 (O-1 — 자리 유지 + 공란)
        assertThat(text(sheet, top + 6, label)).isEqualTo("원산지");
        assertThat(text(sheet, top + 6, value)).isEmpty();

        assertThat(text(sheet, top + 7, label)).isEqualTo("비고");
        assertThat(text(sheet, top + 7, value)).isEqualTo("앵글밸브포함");

        // 이미지 칸은 8행 세로 병합
        assertThat(merged(sheet, new CellRangeAddress(top, top + 7, 0, 0))).isTrue();
    }

    @Test
    @DisplayName("블록은 상한 없이 아래로 늘어난다 (O-2 ⓑ)")
    void 블록_무제한_확장() throws Exception {
        List<ProposalLine> many = new ArrayList<>();
        for (int i = 0; i < 9; i++) {                       // 한 열에 9장 → 6블록을 넘어선다
            many.add(line("욕실1", "양변기", "M" + i, "투피스양변기", 100_000, 1, false));
        }

        Sheet sheet = render(sampleProposal(), many).getSheetAt(0);

        // 9번째 카드는 R5 + 8블록 × 8행 = 행 인덱스 68에서 시작한다
        int ninth = 4 + 8 * 8;
        assertThat(text(sheet, ninth + 2, 1)).isEqualTo("M8");
        assertThat(sheet.getLastRowNum()).isEqualTo(ninth + 7);
    }

    @Test
    @DisplayName("사입가·마진율은 파일 어디에도 없다 (고객 제출용)")
    void 사입가_마진_미노출() throws Exception {
        List<ProposalLine> lines = sampleLines();
        // 사입가 99,999 / 마진율 33 — 새어 나오면 아래 스캔에 걸린다
        lines.get(0).setCatalogUnitPrice(new BigDecimal("99999"));
        lines.get(0).setMarginRate(new BigDecimal("33"));

        Sheet sheet = render(sampleProposal(), lines).getSheetAt(0);

        DataFormatter fmt = new DataFormatter();
        List<String> all = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell cell : row) all.add(fmt.formatCellValue(cell));
        }
        assertThat(all).noneMatch(v -> v.contains("99,999") || v.contains("99999"));
        assertThat(all).noneMatch(v -> v.contains("마진") || v.contains("사입"));
    }

    @Test
    @DisplayName("이미지가 있으면 그림으로 삽입되고, 비율을 지켜 칸 안에 들어간다")
    void 이미지_삽입() throws Exception {
        List<ProposalLine> lines = sampleLines();
        lines.get(0).setImageUrl(IMAGE.toString());

        XSSFSheet sheet = (XSSFSheet) render(sampleProposal(), lines).getSheetAt(0);
        XSSFDrawing drawing = sheet.getDrawingPatriarch();

        assertThat(drawing).isNotNull();
        assertThat(drawing.getShapes()).hasSize(1);

        ClientAnchor anchor = ((Picture) drawing.getShapes().get(0)).getClientAnchor();
        assertThat(anchor.getCol1()).isZero();                    // A열
        assertThat(anchor.getRow1()).isGreaterThanOrEqualTo(4);   // 첫 블록 안
        assertThat(anchor.getRow2()).isLessThanOrEqualTo(11);     // 블록(R5~R12)을 벗어나지 않는다
    }

    @Test
    @DisplayName("이미지 파일이 없거나 EMF면 조용히 건너뛴다 — 출력 자체는 성공한다")
    void 이미지_없어도_출력된다() throws Exception {
        List<ProposalLine> lines = sampleLines();
        lines.get(0).setImageUrl("target/test-card-images/없는파일.png");
        lines.get(1).setImageUrl("target/test-card-images/legacy.emf");

        XSSFSheet sheet = (XSSFSheet) render(sampleProposal(), lines).getSheetAt(0);

        assertThat(sheet.getDrawingPatriarch() == null
                || sheet.getDrawingPatriarch().getShapes().isEmpty()).isTrue();
        assertThat(text(sheet, 6, 1)).isEqualTo("IC702E");   // 카드는 정상적으로 그려졌다
    }

    @Test
    @DisplayName("품목이 없어도 머리글만 있는 파일이 나온다")
    void 빈_제안서() throws Exception {
        Sheet sheet = render(sampleProposal(), List.of()).getSheetAt(0);

        assertThat(text(sheet, 0, 0)).contains("위생기구류 계약 내역");
        assertThat(numeric(sheet, 2, 10)).isEqualByComparingTo("0");
        assertThat(sheet.getLastRowNum()).isEqualTo(3);   // R1~R4만
    }

    /* ===================== 픽스처 ===================== */

    private Workbook render(Proposal proposal, List<ProposalLine> lines) throws Exception {
        byte[] bytes = ProposalCardExcelWriter.write(proposal, lines);
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }

    private Proposal sampleProposal() {
        Proposal p = new Proposal();
        p.setProjectName("햇살아파트");
        p.setApartmentType("59㎡");
        p.setHouseholds(523);
        p.setDate("2026-08-25");
        return p;
    }

    /** 샘플 양식의 4개 열을 각각 하나씩 대표하는 최소 구성. */
    private List<ProposalLine> sampleLines() {
        List<ProposalLine> lines = new ArrayList<>();
        lines.add(line("공용욕실", "양변기", "IC702E", "투피스양변기", 152_000, 1, false,
                "이누스 주식회사", "앵글밸브포함"));                       // 1열
        lines.add(line("욕실 공통", "세면기", "L631E", "반다리세면기", 69_000, 2, false));   // 1열
        lines.add(line("주방", "씽크수전", "G-0820", "씽크수전", 57_000, 1, false));         // 2열
        lines.add(line("욕실1", "비데", "IST-N52E", "비데", 143_000, 1, true));             // 3열 (유상옵션)
        lines.add(line("욕실 공통", "악세사리", "NU-015L-1", "수건걸이", 14_000, 2, false)); // 4열
        return lines;
    }

    private ProposalLine line(String area, String category, String code, String categorySmall,
                              int unitPrice, int qty, boolean optional) {
        return line(area, category, code, categorySmall, unitPrice, qty, optional, "이누스 주식회사", "");
    }

    private ProposalLine line(String area, String category, String code, String categorySmall,
                              int unitPrice, int qty, boolean optional, String vendorName, String note) {
        ProposalLine l = new ProposalLine();
        l.setArea(area);
        l.setCategory(category);
        l.setCategorySmall(categorySmall);
        l.setMainItemCode(code);
        l.setApartmentType("59㎡");
        l.setUnitPrice(BigDecimal.valueOf(unitPrice));
        l.setQty(qty);
        l.setAmount(BigDecimal.valueOf((long) unitPrice * qty));
        l.setOptional(optional);
        l.setVendorName(vendorName);
        l.setNote(note);
        return l;
    }

    /* ===================== 페이지 나누기 (F-023) ===================== */

    /**
     * 카드가 페이지 경계에 걸치지 않는다.
     *
     * <p>블록(8행)이 카드 한 줄이라 페이지는 블록 경계에서만 넘어가야 한다.
     * 예전에는 나누기를 하나도 두지 않아 엑셀이 알아서 잘랐고, 그러면 카드가 반으로 갈렸다.
     */
    @Test
    @DisplayName("페이지 나누기가 모두 블록 경계에 있다")
    void 페이지_나누기는_블록_경계에만_있다() throws Exception {
        // 한 열에 카드를 여러 장 쌓아 블록이 여러 개 생기게 한다
        List<ProposalLine> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add(line("욕실1", "양변기", "CODE-" + i, "양변기", 100_000, 1, false));
        }

        try (Workbook wb = open(ProposalCardExcelWriter.write(sampleProposal(), lines))) {
            Sheet sheet = wb.getSheetAt(0);
            int[] breaks = sheet.getRowBreaks();

            assertThat(breaks).as("10블록이면 나누기가 생겨야 한다").isNotEmpty();
            for (int rowBreak : breaks) {
                // setRowBreak(n)은 n 다음에서 끊는다 → 다음 페이지 첫 행은 n+1이고
                // 그 행이 블록의 첫 행(= 머리글 4행 뒤로 8행 배수)이어야 한다.
                int firstRowOfNextPage = rowBreak + 1;
                assertThat((firstRowOfNextPage - 4) % 8)
                        .as("행 %d에서 끊겼다 — 블록 한가운데라 카드가 갈린다", firstRowOfNextPage)
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("몇 장짜리인지 못 박는다 — 안 그러면 엑셀이 사이에 자동 나누기를 끼운다")
    void 페이지_수를_지정한다() throws Exception {
        List<ProposalLine> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add(line("욕실1", "양변기", "CODE-" + i, "양변기", 100_000, 1, false));
        }

        try (Workbook wb = open(ProposalCardExcelWriter.write(sampleProposal(), lines))) {
            PrintSetup print = wb.getSheetAt(0).getPrintSetup();
            assertThat(print.getFitHeight()).as("10블록 / 4 = 3장").isEqualTo((short) 3);
            assertThat(print.getFitWidth()).as("가로는 한 장 유지").isEqualTo((short) 1);
        }
    }

    @Test
    @DisplayName("카드가 한 장뿐이면 나눌 것도 없다")
    void 카드가_적으면_나누기가_없다() throws Exception {
        List<ProposalLine> lines = List.of(line("욕실1", "양변기", "CODE-1", "양변기", 100_000, 1, false));

        try (Workbook wb = open(ProposalCardExcelWriter.write(sampleProposal(), lines))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRowBreaks()).isEmpty();
            assertThat(sheet.getPrintSetup().getFitHeight()).isEqualTo((short) 1);
        }
    }

    private Workbook open(byte[] bytes) throws Exception {
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }

    /* ===================== 셀 읽기 ===================== */

    private String text(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) return "";
        Cell c = r.getCell(col);
        return c == null ? "" : c.getStringCellValue();
    }

    private BigDecimal numeric(Sheet sheet, int row, int col) {
        return BigDecimal.valueOf(sheet.getRow(row).getCell(col).getNumericCellValue());
    }

    private boolean merged(Sheet sheet, CellRangeAddress expected) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).formatAsString().equals(expected.formatAsString())) return true;
        }
        return false;
    }
}
