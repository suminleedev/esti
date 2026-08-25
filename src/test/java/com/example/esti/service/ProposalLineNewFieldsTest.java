package com.example.esti.service;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.entity.VendorProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 검증: 제안서 라인에 새로 붙인 <b>단위·평형·건물구분</b>이 저장·조회·복제를 거쳐 살아남는지 본다.
 *
 * <p>세 필드는 Phase 6 출력물(제안서 카드 / 견적서 표)이 읽을 값이라, 어느 한 경로에서
 * 조용히 누락되면 P2·P3에서 빈칸으로 나타난다. 경로별로 못을 박아 둔다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:proposallinefields;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class ProposalLineNewFieldsTest {

    @Autowired
    private ProposalService proposalService;

    @Test
    @DisplayName("단위·평형·건물구분이 저장 후 조회에서 그대로 돌아온다")
    void 신규_필드_왕복() throws Exception {
        ProposalResponse saved = proposalService.createDraft(
                request(line("59㎡", "본세대", "EA"), line("84㎡", "부속동", "SET")));

        ProposalResponse loaded = proposalService.get(saved.getId());

        assertThat(loaded.getLines()).hasSize(2);
        assertThat(loaded.getLines())
                .extracting(ProposalResponse.Line::getApartmentType,
                        ProposalResponse.Line::getBuildingType,
                        ProposalResponse.Line::getUnit)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("59㎡", "본세대", "EA"),
                        org.assertj.core.groups.Tuple.tuple("84㎡", "부속동", "SET"));
    }

    @Test
    @DisplayName("단위가 비어 오면 기본값 SET으로 접힌다 (견적서 C열이 빈칸이 되면 안 된다)")
    void 단위_기본값_폴백() throws Exception {
        ProposalRequest.Line nullUnit = line("59㎡", "본세대", null);
        ProposalRequest.Line blankUnit = line("59㎡", "본세대", "   ");

        ProposalResponse saved = proposalService.createDraft(request(nullUnit, blankUnit));
        ProposalResponse loaded = proposalService.get(saved.getId());

        assertThat(loaded.getLines())
                .extracting(ProposalResponse.Line::getUnit)
                .containsExactly(VendorProduct.UNIT_DEFAULT, VendorProduct.UNIT_DEFAULT);
    }

    @Test
    @DisplayName("제안서를 복제해도 세 필드가 따라온다")
    void 복제시_필드_유지() throws Exception {
        ProposalResponse origin = proposalService.createDraft(
                request(line("74㎡", "상가", "EA")));

        ProposalResponse copy = proposalService.copyToDraft(origin.getId());

        assertThat(copy.getLines()).hasSize(1);
        ProposalResponse.Line copied = copy.getLines().get(0);
        assertThat(copied.getApartmentType()).isEqualTo("74㎡");
        assertThat(copied.getBuildingType()).isEqualTo("상가");
        assertThat(copied.getUnit()).isEqualTo("EA");
    }

    @Test
    @DisplayName("unitOrDefault는 null·공백만 기본값으로 접고 나머지는 그대로 둔다")
    void 단위_폴백_규칙() {
        assertThat(VendorProduct.unitOrDefault(null)).isEqualTo("SET");
        assertThat(VendorProduct.unitOrDefault("")).isEqualTo("SET");
        assertThat(VendorProduct.unitOrDefault("  ")).isEqualTo("SET");
        assertThat(VendorProduct.unitOrDefault("EA")).isEqualTo("EA");
        assertThat(VendorProduct.unitOrDefault("조")).isEqualTo("조");
    }

    private ProposalRequest request(ProposalRequest.Line... lines) {
        ProposalRequest req = new ProposalRequest();
        req.setProjectName("P1 필드 검증 현장");
        req.setGlobalMarginRate(new BigDecimal("10"));
        req.setLines(List.of(lines));
        return req;
    }

    private ProposalRequest.Line line(String apartmentType, String buildingType, String unit) {
        ProposalRequest.Line l = new ProposalRequest.Line();
        l.setProductId(1L);
        l.setProductName("검증용 품목");
        l.setCatalogUnitPrice(new BigDecimal("<PRICE>"));
        l.setQty(1);
        l.setArea("욕실1");
        l.setCategory("양변기");
        l.setApartmentType(apartmentType);
        l.setBuildingType(buildingType);
        l.setUnit(unit);
        return l;
    }
}
