package com.example.esti.service;

import com.example.esti.entity.Proposal;
import com.example.esti.repository.ProposalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/** 견적번호 채번 (O-9 ⓐ) — `syt-YYYYMMDDNN`, 제안서당 1회 부여 후 재사용. */
@SpringBootTest
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
    @DisplayName("이미 번호가 있으면 다시 부여하지 않는다 — 재출력해도 번호가 바뀌면 안 된다")
    void 재사용() {
        Proposal p = save();
        String assigned = quoteNumberService.assign(p);

        assertThat(quoteNumberService.assign(p)).isEqualTo(assigned);
        assertThat(quoteNumberService.assign(proposalRepository.findById(p.getId()).orElseThrow()))
                .isEqualTo(assigned);
    }

    private Proposal save() {
        Proposal p = new Proposal();
        p.setProjectName("채번 검증 현장");
        p.setStatus(Proposal.Status.DRAFT);
        return proposalRepository.save(p);
    }
}
