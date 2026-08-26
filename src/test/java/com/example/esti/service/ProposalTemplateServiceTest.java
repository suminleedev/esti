package com.example.esti.service;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalTemplateRequest;
import com.example.esti.dto.ProposalTemplateResponse;
import com.example.esti.exception.InvalidStateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:templateSnapshotTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ProposalTemplateServiceTest {

    @Autowired private ProposalTemplateService templateService;
    @Autowired private ProposalService proposalService;

    private ProposalTemplateRequest templateRequest(String name) {
        ProposalTemplateRequest req = new ProposalTemplateRequest();
        req.setTemplateName(name);
        req.setApartmentType("84A");
        req.setAreas(List.of("욕실1"));
        req.setRequiredCategories(List.of("양변기"));
        ProposalTemplateRequest.Line line = new ProposalTemplateRequest.Line();
        line.setProductId(77L); // ProductCatalog에 존재하지 않는 임의 id — 조회 없이 스냅샷 저장돼야 함
        line.setVendorItemName("스냅샷 품목");
        line.setMainItemCode("SNAP-001");
        line.setUnitPrice(BigDecimal.valueOf(50000));
        line.setArea("욕실1");
        line.setCategory("양변기");
        line.setDefaultQty(1);
        req.setLines(List.of(line));
        return req;
    }

    @Test
    void 카탈로그_없이_템플릿을_생성하고_라인_스냅샷이_보존된다() throws Exception {
        ProposalTemplateResponse res = templateService.create(templateRequest("스냅샷 템플릿"));

        assertThat(res.getLines()).hasSize(1);
        ProposalTemplateResponse.Line line = res.getLines().get(0);
        assertThat(line.getProductId()).isEqualTo(77L);
        assertThat(line.getVendorItemName()).isEqualTo("스냅샷 품목");
        assertThat(line.getMainItemCode()).isEqualTo("SNAP-001");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("50000");
    }

    @Test
    void 제안서가_참조하는_템플릿은_삭제할_수_없다() throws Exception {
        ProposalTemplateResponse tpl = templateService.create(templateRequest("참조 템플릿"));

        ProposalRequest preq = new ProposalRequest();
        preq.setProjectName("템플릿 참조 제안서");
        preq.setTemplateId(tpl.getId());
        preq.setAreas(List.of());
        preq.setRequiredCategories(List.of());
        proposalService.createDraft(preq);

        assertThrows(InvalidStateException.class, () -> templateService.delete(tpl.getId()));
    }

    @Test
    void 참조가_없는_템플릿은_삭제된다() throws Exception {
        ProposalTemplateResponse tpl = templateService.create(templateRequest("고아 템플릿"));
        assertDoesNotThrow(() -> templateService.delete(tpl.getId()));
    }
}
