package com.example.esti.controller;

import com.example.esti.exception.InvalidStateException;
import com.example.esti.exception.NotFoundException;
import com.example.esti.service.ProposalExcelService;
import com.example.esti.service.ProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProposalController.class)
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
}
