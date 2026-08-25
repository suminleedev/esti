package com.example.esti.service;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task 1 — 소프트 삭제된 제안서의 상태 변경 차단.
 *
 * <p>조회 계열({@code list}/{@code get})만 {@code deletedAt}을 거르고 상태 변경 계열은 {@code findById}를 써서,
 * <b>삭제된 SUBMITTED 제안서를 {@code send()}로 SENT 확정하는 우회</b>가 가능했다.
 * 명령 경로 전부를 같은 필터로 통일했는지 확인한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:proposalSoftDeleteTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class ProposalServiceSoftDeleteTest {

    @Autowired private ProposalService service;

    @Test
    @DisplayName("소프트 삭제된 제안서는 발송·제출·수정·복사·재삭제가 모두 거부된다")
    void 소프트_삭제된_제안서는_상태_변경이_모두_거부된다() throws Exception {
        ProposalRequest req = request("소프트삭제-검증현장");
        ProposalResponse draft = service.createDraft(req);
        Long id = draft.getId();

        service.submit(id, req);   // DRAFT → SUBMITTED
        service.delete(id);        // SUBMITTED → 소프트 삭제(deletedAt)

        assertThrows(RuntimeException.class, () -> service.send(id));
        assertThrows(RuntimeException.class, () -> service.submit(id, req));
        assertThrows(RuntimeException.class, () -> service.updateDraft(id, req));
        assertThrows(RuntimeException.class, () -> service.copyToDraft(id));
        assertThrows(RuntimeException.class, () -> service.delete(id));
    }

    @Test
    @DisplayName("삭제되지 않은 제안서는 정상 발송된다 (가드가 정상 경로를 막지 않는다)")
    void 삭제되지_않은_제안서는_정상_발송된다() throws Exception {
        ProposalRequest req = request("정상발송-검증현장");
        ProposalResponse draft = service.createDraft(req);
        service.submit(draft.getId(), req);

        ProposalResponse sent = service.send(draft.getId());
        assertEquals("SENT", sent.getStatus());
    }

    private ProposalRequest request(String projectName) {
        ProposalRequest req = new ProposalRequest();
        req.setProjectName(projectName);
        req.setAreas(List.of("욕실1"));
        req.setRequiredCategories(List.of("양변기"));

        ProposalRequest.Line line = new ProposalRequest.Line();
        line.setProductId(1L);
        line.setProductName("테스트제품");
        line.setCatalogUnitPrice(BigDecimal.valueOf(0));
        line.setQty(1);
        req.setLines(List.of(line));
        return req;
    }
}
