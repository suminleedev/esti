package com.example.esti.service;

import com.example.esti.dto.ProposalRequest;
import com.example.esti.dto.ProposalResponse;
import com.example.esti.exception.BadRequestException;
import com.example.esti.exception.InvalidStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제안서 낙관적 잠금 (F-026).
 *
 * <p>QA에서 재현된 것은 «탭 두 개»다 — 같은 제안서를 한 번 읽어 그 스냅샷을 두 편집본의
 * 출발점으로 삼고, A로 저장한 뒤 <b>A의 결과를 모른 채</b> B로 저장하면
 * 둘 다 200이고 A가 쓴 내용은 흔적 없이 사라졌다. 경고도 충돌 표시도 없었다.
 *
 * <p><b>혼자 써도 일어난다.</b> 창 두 개면 충분하다. 그래서 «단일 사용자 전제»로 닫을 수 없다.
 *
 * <p>이 파일이 지키는 것:
 * <ol>
 *   <li>낡은 버전으로 저장하면 <b>거절</b>된다 (조용히 덮지 않는다)</li>
 *   <li>버전을 안 보내도 거절된다 (검사가 있다고 적어 두고 안 하는 상태를 만들지 않는다)</li>
 *   <li><b>줄만 바꿔도</b> 버전이 오른다 — 이 화면에서 가장 흔한 편집이 검사에서 빠지면 안 된다</li>
 *   <li>응답에 실린 버전으로 <b>바로 이어서</b> 저장할 수 있다 (헛충돌이 안 난다)</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:proposalOptimisticLockTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
@Transactional
class ProposalOptimisticLockTest {

    @Autowired
    private ProposalService service;

    private ProposalResponse draft;

    @BeforeEach
    void setUp() throws Exception {
        draft = service.createDraft(request("최초 현장", BigDecimal.valueOf(10), "양변기", 1));
    }

    /** 최소 구성의 제안서 요청. 금액은 전부 합성값이다. */
    private static ProposalRequest request(String projectName, BigDecimal margin,
                                           String category, int qty) {
        ProposalRequest req = new ProposalRequest();
        req.setProjectName(projectName);
        req.setManager("담당자");
        req.setGlobalMarginRate(margin);
        req.setAreas(List.of("욕실1"));
        req.setRequiredCategories(List.of(category));

        ProposalRequest.Line line = new ProposalRequest.Line();
        line.setProductId(1L);
        line.setProductName("합성 품목");
        line.setCategory(category);
        line.setArea("욕실1");
        line.setQty(qty);
        line.setCatalogUnitPrice(BigDecimal.valueOf(10_000));
        line.setUnitPrice(BigDecimal.valueOf(11_000));
        line.setAmount(BigDecimal.valueOf(11_000L * qty));
        req.setLines(List.of(line));

        return req;
    }

    private ProposalRequest at(Long version, String projectName, int qty) {
        ProposalRequest req = request(projectName, BigDecimal.valueOf(10), "양변기", qty);
        req.setVersion(version);
        return req;
    }

    @Test
    void 새로_만든_제안서에도_버전이_실린다() {
        assertThat(draft.getVersion()).isNotNull();
    }

    @Test
    void 최신_버전으로_저장하면_통과한다() throws Exception {
        ProposalResponse saved = service.updateDraft(draft.getId(), at(draft.getVersion(), "고친 현장", 1));

        assertThat(saved.getProjectName()).isEqualTo("고친 현장");
    }

    @Test
    void 저장할_때마다_버전이_오른다() throws Exception {
        ProposalResponse first = service.updateDraft(draft.getId(), at(draft.getVersion(), "1차", 1));
        ProposalResponse second = service.updateDraft(first.getId(), at(first.getVersion(), "2차", 1));

        assertThat(first.getVersion()).isGreaterThan(draft.getVersion());
        assertThat(second.getVersion()).isGreaterThan(first.getVersion());
    }

    @Test
    void 응답에_실린_버전으로_바로_이어서_저장할_수_있다() throws Exception {
        // 응답이 옛 버전을 실어 보내면 화면이 그걸 들고 다음 저장을 하다 헛충돌이 난다.
        ProposalResponse saved = service.updateDraft(draft.getId(), at(draft.getVersion(), "1차", 1));

        assertThat(service.updateDraft(saved.getId(), at(saved.getVersion(), "2차", 1)))
                .isNotNull();
    }

    @Test
    void 줄만_바꿔도_버전이_오른다() throws Exception {
        // 이 화면의 대부분의 수정은 줄만 바꾼다. proposal 행이 그대로면 Hibernate가
        // UPDATE를 안 내 버전도 안 오르는데, 그러면 가장 흔한 편집이 검사에서 빠진다.
        ProposalResponse saved = service.updateDraft(draft.getId(), at(draft.getVersion(), "최초 현장", 7));

        assertThat(saved.getVersion()).isGreaterThan(draft.getVersion());
    }

    @Test
    void 탭_두_개_시나리오_나중_저장이_거절된다() throws Exception {
        // 같은 스냅샷을 두 편집본의 출발점으로 삼는다 — QA 재현 절차 그대로다.
        Long snapshot = draft.getVersion();

        service.updateDraft(draft.getId(), at(snapshot, "탭A수정", 1));

        assertThatThrownBy(() -> service.updateDraft(draft.getId(), at(snapshot, "탭B수정", 1)))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("다른 곳에서 이미 수정");
    }

    @Test
    void 거절된_뒤에도_앞_사람의_내용이_남아_있다() throws Exception {
        // 「거절했다」보다 중요한 것은 「A가 쓴 것이 살아 있다」는 쪽이다.
        Long snapshot = draft.getVersion();
        service.updateDraft(draft.getId(), at(snapshot, "탭A수정", 1));

        assertThatThrownBy(() -> service.updateDraft(draft.getId(), at(snapshot, "탭B수정", 1)))
                .isInstanceOf(InvalidStateException.class);

        assertThat(service.get(draft.getId()).getProjectName()).isEqualTo("탭A수정");
    }

    @Test
    void 버전을_안_보내면_거절된다() {
        // 통과시키면 «검사가 있다»고 적어 두고 실제로는 안 하는 상태가 된다.
        assertThatThrownBy(() -> service.updateDraft(draft.getId(), at(null, "버전없음", 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("version");
    }

    @Test
    void 제출_경로도_같은_검사를_받는다() throws Exception {
        Long snapshot = draft.getVersion();
        service.updateDraft(draft.getId(), at(snapshot, "탭A수정", 1));

        assertThatThrownBy(() -> service.submit(draft.getId(), at(snapshot, "탭B제출", 1)))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("다른 곳에서 이미 수정");
    }

    @Test
    void 제출은_최신_버전이면_통과한다() throws Exception {
        ProposalResponse saved = service.updateDraft(draft.getId(), at(draft.getVersion(), "제출할 현장", 1));

        ProposalResponse submitted = service.submit(saved.getId(), at(saved.getVersion(), "제출할 현장", 1));

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void 조회는_언제나_최신_버전을_준다() throws Exception {
        service.updateDraft(draft.getId(), at(draft.getVersion(), "고친 현장", 1));

        ProposalResponse fetched = service.get(draft.getId());

        assertThat(fetched.getVersion()).isGreaterThan(draft.getVersion());
        assertThat(service.updateDraft(fetched.getId(), at(fetched.getVersion(), "또 고침", 1)))
                .isNotNull();
    }
}
