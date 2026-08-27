package com.example.esti.service;

import com.example.esti.exception.InvalidStateException;
import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.dto.QuoteTargetView;
import com.example.esti.output.QuoteTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 출력 진입점 검증 (P4) — 대상 목록, 파일명, 상태 가드.
 *
 * <p>양식 자체는 `ProposalCardExcelWriterTest`·`QuoteExcelWriterTest`가 본다.
 * 여기서는 <b>어떤 대상을 낼 수 있고 파일이 어떤 이름으로 나가는지</b>를 확인한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:excelservice;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images",
        "app.company.ceo=홍 길 동"
})
class ProposalExcelServiceTest {

    @Autowired private ProposalService proposalService;
    @Autowired private ProposalExcelService excelService;

    @Test
    @DisplayName("대상 목록 — 본세대 한 부, 부속동·상가는 합쳐 한 부")
    void 대상_목록() throws Exception {
        Long id = sentProposal();

        List<QuoteTargetView> targets = excelService.listQuoteTargets(id);

        // 한 제안서 = 한 평형이라(2026-08-27) 본세대는 언제나 한 부다.
        // 평형 축 자체는 QuoteTarget에 남아 있고 QuoteTargetTest가 따로 검증한다.
        assertThat(targets).extracting(QuoteTargetView::label)
                .containsExactly("59㎡", "부속동·상가");
        assertThat(targets).extracting(QuoteTargetView::kind)
                .containsExactly("MAIN", "ANNEX");
        assertThat(targets.get(0).lineCount()).isEqualTo(3);   // 본세대 3건
        // 부속동 1건 + 상가 1건이 한 대상으로 합쳐진다
        assertThat(targets.get(1).lineCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("품목이 없는 대상은 목록에 넣지 않는다 — 빈 견적서를 받게 되면 안 된다")
    void 빈_대상_제외() throws Exception {
        Long id = sentProposal(line("본세대", "양변기", 100_000));

        assertThat(excelService.listQuoteTargets(id))
                .extracting(QuoteTargetView::label)
                .containsExactly("59㎡");   // 부속동 대상 없음
    }

    @Test
    @DisplayName("파일명은 서버가 정한다 — 양식·현장명·대상·날짜가 들어간다")
    void 파일명() throws Exception {
        Long id = sentProposal();

        assertThat(excelService.exportProposal(id).fileName())
                .isEqualTo("제안서_햇살아파트_260825.xlsx");
        assertThat(excelService.exportQuote(id, QuoteTarget.main("59㎡")).fileName())
                .isEqualTo("견적서_햇살아파트_59㎡_260825.xlsx");
        assertThat(excelService.exportQuote(id, QuoteTarget.annex()).fileName())
                .isEqualTo("견적서_햇살아파트_부속동상가_260825.xlsx");
    }

    @Test
    @DisplayName("발송완료가 아니면 출력할 수 없다")
    void 상태_가드() throws Exception {
        ProposalResponse draft = proposalService.createDraft(request(line("본세대", "양변기", 100_000)));

        assertThatThrownBy(() -> excelService.exportProposal(draft.getId()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("발송완료");
        assertThatThrownBy(() -> excelService.listQuoteTargets(draft.getId()))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("두 양식 모두 실제로 열리는 xlsx를 낸다")
    void 파일_생성() throws Exception {
        Long id = sentProposal();

        assertThat(excelService.exportProposal(id).content()).isNotEmpty();
        assertThat(excelService.exportQuote(id, QuoteTarget.main("59㎡")).content()).isNotEmpty();
        assertThat(excelService.exportQuote(id, QuoteTarget.annex()).content()).isNotEmpty();
    }

    /* ===================== 픽스처 ===================== */

    private Long sentProposal() throws Exception {
        return sentProposal(
                line("본세대", "양변기", 152_000),
                line("본세대", "세면기", 69_000),
                line("본세대", "양변기", 216_000),
                line("부속동", "양변기", 145_000),
                line("상가", "악세사리", 12_000));
    }

    private Long sentProposal(ProposalRequest.Line... lines) throws Exception {
        ProposalResponse draft = proposalService.createDraft(request(lines));
        proposalService.submit(draft.getId(), request(lines));
        proposalService.send(draft.getId());
        return draft.getId();
    }

    private ProposalRequest request(ProposalRequest.Line... lines) {
        ProposalRequest req = new ProposalRequest();
        req.setProjectName("햇살아파트");
        req.setClientName("[대우건설]");
        req.setApartmentType("59㎡");
        req.setHouseholds(523);
        req.setDate("2026-08-25");
        req.setGlobalMarginRate(new BigDecimal("10"));
        req.setLines(List.of(lines));
        return req;
    }

    // 평형은 인자로 받지 않는다 — 서버가 제안서 평형(59㎡)으로 채운다
    private ProposalRequest.Line line(String buildingType, String category, int price) {
        ProposalRequest.Line l = new ProposalRequest.Line();
        l.setProductId(1L);
        l.setProductName(category);
        l.setCategory(category);
        l.setCategorySmall(category);
        l.setArea("공용욕실");
        l.setBuildingType(buildingType);
        l.setCatalogUnitPrice(BigDecimal.valueOf(price));
        l.setQty(1);
        return l;
    }
}
