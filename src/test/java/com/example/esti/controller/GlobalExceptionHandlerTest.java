package com.example.esti.controller;

import com.example.esti.exception.InvalidStateException;
import com.example.esti.exception.NotFoundException;
import com.example.esti.service.ProposalExcelService;
import com.example.esti.service.ProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.esti.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProposalController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProposalService proposalService;
    @MockitoBean private ProposalExcelService proposalExcelService;

    @Test
    void 존재하지_않는_제안서는_404() throws Exception {
        when(proposalService.get(anyLong())).thenThrow(new NotFoundException("Proposal not found"));
        mockMvc.perform(get("/api/proposals/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Proposal not found"));
    }

    @Test
    void 상태_규칙_위반은_409() throws Exception {
        when(proposalService.send(anyLong()))
                .thenThrow(new InvalidStateException("SUBMITTED 상태에서만 발송 확정할 수 있습니다."));
        mockMvc.perform(post("/api/proposals/1/send"))
                .andExpect(status().isConflict());
    }

    /* ===== 클라이언트 잘못은 400으로 나간다 (F-020) =====
     * 전에는 아래 네 가지가 전부 500 "서버 내부 오류"였다. */

    @Test
    void 경로변수_타입이_안_맞으면_400() throws Exception {
        mockMvc.perform(get("/api/proposals/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("id")));
    }

    @Test
    void 본문이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("본문")));
    }

    @Test
    void JSON이_깨졌으면_400() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("형식")));
    }

    @Test
    void DB_제약_위반은_400() throws Exception {
        when(proposalService.createDraft(any()))
                .thenThrow(new DataIntegrityViolationException("truncation error"));
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"현장\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("저장할 수 없는")));
    }

    /* ===== 길이·범위는 DB에 닿기 전에 걸린다 (F-024) =====
     * 어느 필드가 왜 걸렸는지 문구에 담긴다. */

    @Test
    void 현장명이_200자를_넘으면_400이고_문구가_필드를_짚는다() throws Exception {
        String tooLong = "가".repeat(201);
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현장명은 200자까지 입력할 수 있습니다."));
    }

    @Test
    void 일괄_마진율이_범위를_넘으면_400() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"현장\",\"globalMarginRate\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("마진율은")));
    }

    @Test
    void 라인_필드도_검증된다() throws Exception {
        String tooLong = "나".repeat(201);
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"현장\",\"lines\":[{\"productName\":\"" + tooLong + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("품목명은")));
    }

    /**
     * 없는 주소는 404다 — 예전엔 500이었다.
     *
     * <p>컨트롤러가 없는 경로는 정적 리소스 조회로 흘러가 {@code NoResourceFoundException}이 되고,
     * 그게 catch-all에 걸려 «서버 내부 오류»로 나갔다. demo 프로파일에서는 크롤러 컨트롤러가
     * 아예 뜨지 않으므로(D-4) 그런 요청이 실제로 이 자리로 온다.
     *
     * <p>관리자 경로로 확인하지 않는 이유 — 거기는 인증이 앞에 서서 401이 먼저 나간다(D-7).
     * 있는지 없는지 알려 주지 않는 편이 맞고, 그건 {@code AdminApiSecurityTest}가 본다.
     */
    @Test
    void 없는_경로는_404() throws Exception {
        mockMvc.perform(get("/api/such-endpoint-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("찾을 수 없습니다")));
    }

    /** 업로드 한도 초과도 500이 아니다 — 얼마까지 되는지 알려 준다(데모 2MB / 실사용 100MB). */
    @Test
    void 업로드_한도_초과는_413() throws Exception {
        when(proposalService.get(anyLong()))
                .thenThrow(new MaxUploadSizeExceededException(2L * 1024 * 1024));
        mockMvc.perform(get("/api/proposals/1"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(containsString("너무 큽니다")));
    }

    @Test
    void 한도_안의_값은_통과한다() throws Exception {
        mockMvc.perform(post("/api/proposals/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"" + "가".repeat(200) + "\",\"globalMarginRate\":999.99}"))
                .andExpect(status().isOk());
    }
}
