package com.example.esti.service;

import com.example.esti.entity.Proposal;
import com.example.esti.repository.ProposalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 견적번호 채번 (O-9 ⓐ) — `syt-YYYYMMDDNN`, 제안서당 1회 부여 후 재사용.
 *
 * <p>서비스가 실제로 커밋하는 통합 테스트라 한 클래스 안에서 번호가 누적된다.
 * 두 자리 범위를 보는 검증을 먼저, <b>세 자리로 넘기는 검증을 뒤로</b> 고정한다
 * (MasterCodeServiceTest와 같은 이유).
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:quoteno;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class QuoteNumberServiceTest {

    @Autowired private QuoteNumberService quoteNumberService;
    @Autowired private ProposalRepository proposalRepository;

    private String todayPrefix() {
        return "syt-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @Order(1)
    @DisplayName("당일 일련번호가 1부터 순서대로 붙는다")
    void 일련번호_증가() {
        String first = quoteNumberService.assign(save());
        String second = quoteNumberService.assign(save());

        assertThat(first).startsWith(todayPrefix());
        assertThat(first).hasSize(todayPrefix().length() + 2);   // NN 2자리
        assertThat(Integer.parseInt(second.substring(todayPrefix().length())))
                .isEqualTo(Integer.parseInt(first.substring(todayPrefix().length())) + 1);
    }

    @Test
    @Order(2)
    @DisplayName("이미 번호가 있으면 다시 부여하지 않는다 — 재출력해도 번호가 바뀌면 안 된다")
    void 재사용() {
        Proposal p = save();
        String assigned = quoteNumberService.assign(p);

        assertThat(quoteNumberService.assign(p)).isEqualTo(assigned);
        assertThat(quoteNumberService.assign(proposalRepository.findById(p.getId()).orElseThrow()))
                .isEqualTo(assigned);
    }

    @Test
    @Order(4)
    @DisplayName("100번을 넘겨도 번호가 되풀이되지 않는다 — 최댓값은 숫자로 고른다")
    void 세자리_일련번호_뒤에도_증가한다() {
        // 예전에는 사전순 최대를 집어서 "99" > "100"이 되는 바람에
        // 최댓값이 계속 ...99로 잡히고 100번이 매번 다시 발급됐다(F-001).
        // 100건을 만들 필요 없이, 그 함정이 생기는 상태를 바로 만들어 본다.
        saveWithQuoteNo(todayPrefix() + "99");
        saveWithQuoteNo(todayPrefix() + "100");

        String next = quoteNumberService.assign(save());

        assertThat(next).isEqualTo(todayPrefix() + "101");
    }

    @Test
    @Order(3)
    @DisplayName("99번 다음은 100번이다 — 두 자리를 넘어설 때 잘리지 않는다")
    void 두자리를_넘어설_때_잘리지_않는다() {
        saveWithQuoteNo(todayPrefix() + "99");

        assertThat(quoteNumberService.assign(save())).isEqualTo(todayPrefix() + "100");
    }

    private Proposal save() {
        Proposal p = new Proposal();
        p.setProjectName("채번 검증 현장");
        p.setStatus(Proposal.Status.DRAFT);
        return proposalRepository.save(p);
    }

    /** 이미 그 번호가 나가 있는 상태를 만든다. */
    private void saveWithQuoteNo(String quoteNo) {
        Proposal p = save();
        p.setQuoteNo(quoteNo);
        proposalRepository.save(p);
    }
}
