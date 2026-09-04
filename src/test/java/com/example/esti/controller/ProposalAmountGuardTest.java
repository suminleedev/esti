package com.example.esti.controller;

import com.example.esti.service.ProposalExcelService;
import com.example.esti.service.ProposalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 금액이 음수가 되는 길을 막는다 (F-025).
 *
 * <p>서버가 단가·금액을 재계산하므로(`ProposalService`) 실제 입력은 <b>원가·마진율·수량</b> 셋이다.
 * 단가 = 원가 × (100 + 마진율) / 100, 금액 = 단가 × 수량 이라 셋 중 하나만 음수여도 총액이 음수가 된다.
 * QA에서는 셋 다 그대로 통과했다 — 수량 −3이 총액을 음수로 만들었고, 수량 2.5는 2로 조용히 잘렸다.
 */
@WebMvcTest(ProposalController.class)
class ProposalAmountGuardTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProposalService proposalService;
    @MockitoBean private ProposalExcelService proposalExcelService;

    /** 라인 하나짜리 요청. 값만 바꿔 가며 쓴다. */
    private static String bodyWithLine(String lineFields) {
        return "{\"projectName\":\"현장\",\"lines\":[{" + lineFields + "}]}";
    }

    private void expectBadRequest(String body, String messagePart) throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(messagePart)));
    }

    @Test
    @DisplayName("음수 수량은 거절한다 — 총액이 음수가 된다")
    void 음수_수량() throws Exception {
        expectBadRequest(bodyWithLine("\"qty\":-3"), "수량은 1 이상");
    }

    @Test
    @DisplayName("수량 0도 거절한다 — 화면도 qty > 0만 담게 한다")
    void 수량_0() throws Exception {
        expectBadRequest(bodyWithLine("\"qty\":0"), "수량은 1 이상");
    }

    @Test
    @DisplayName("소수 수량은 잘라 쓰지 않고 거절한다 — 2.5가 조용히 2가 됐었다")
    void 소수_수량() throws Exception {
        expectBadRequest(bodyWithLine("\"qty\":2.5"), "형식이 올바르지 않습니다");
    }

    @Test
    @DisplayName("음수 원가는 거절한다 — 단가·금액이 통째로 음수가 된다")
    void 음수_원가() throws Exception {
        expectBadRequest(bodyWithLine("\"qty\":1,\"catalogUnitPrice\":-1000"), "원가는 0 이상");
    }

    @Test
    @DisplayName("마진율 -100 미만은 거절한다 — 단가가 음수가 되는 지점이다")
    void 단가를_음수로_만드는_마진율() throws Exception {
        expectBadRequest(bodyWithLine("\"qty\":1,\"marginRate\":-150"), "마진율은 -100");
        expectBadRequest("{\"projectName\":\"현장\",\"globalMarginRate\":-150}", "일괄 마진율은 -100");
    }

    @Test
    @DisplayName("음수 세대수는 거절한다 — 총액 = 세대당 × 세대수")
    void 음수_세대수() throws Exception {
        expectBadRequest("{\"projectName\":\"현장\",\"households\":-5}", "세대수는 1 이상");
    }

    /**
     * 원가보다 싸게 파는 것(-100 ~ 0)은 막지 않는다.
     * 계산이 깨지는 값이 아니라 영업 판단이라, 여기서 정할 일이 아니다.
     */
    @Test
    @DisplayName("할인(음수 마진율)은 -100까지 허용한다 — 계산은 성립한다")
    void 할인은_막지_않는다() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithLine("\"qty\":1,\"catalogUnitPrice\":1000,\"marginRate\":-30")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("정상 값은 그대로 통과한다")
    void 정상_값() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"현장\",\"households\":100,\"globalMarginRate\":30,"
                                + "\"lines\":[{\"qty\":3,\"catalogUnitPrice\":50000,\"marginRate\":30}]}"))
                .andExpect(status().isOk());
    }
}
