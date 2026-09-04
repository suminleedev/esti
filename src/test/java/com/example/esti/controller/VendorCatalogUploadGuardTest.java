package com.example.esti.controller;

import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.VendorCatalogCommandService;
import com.example.esti.service.VendorCatalogQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드를 받기 전에 거른다 (F-009).
 *
 * <p>예전에는 무엇이 오든 {@code 200 + jobId}로 받고 임시파일까지 쓴 뒤 비동기 단계에서 실패했다.
 * 확장자 검사가 화면에만 있어 API를 직접 부르면 그대로 통과했고, 요청한 쪽은 진행률을
 * 물어봐야 실패를 알 수 있었다. 이제 <b>디스크에도 진행률 저장소에도 자국을 남기지 않고</b> 400이다.
 */
@WebMvcTest(VendorCatalogController.class)
class VendorCatalogUploadGuardTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VendorCatalogQueryService queryService;
    @MockitoBean private VendorCatalogCommandService commandService;
    @MockitoBean private CatalogImportAsyncService importService;
    @MockitoBean private ImportProgressStore progressStore;
    @MockitoBean private VendorExcelParserFactory parserFactory;

    @BeforeEach
    void 파서는_A와_B만_받는다() {
        when(parserFactory.supports(anyString())).thenReturn(false);
        when(parserFactory.supports("A")).thenReturn(true);
        when(parserFactory.supports("B")).thenReturn(true);
        when(progressStore.createJob()).thenReturn("job-1");
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }

    /** 거절된 요청은 job도 만들지 않아야 한다 — 만들면 아무도 안 보는 진행률이 쌓인다. */
    private void expectRejected(MockMultipartFile f, String vendorCode, String messagePart) throws Exception {
        mockMvc.perform(multipart("/api/vendor-catalog/upload-excel/" + vendorCode).file(f))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(messagePart)));

        verify(progressStore, never()).createJob();
        verify(importService, never()).importVendorCatalogAsync(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("엑셀이 아닌 확장자는 받지 않는다")
    void 엑셀이_아니면_거절() throws Exception {
        expectRejected(file("not-excel.txt", "hello".getBytes()), "A", "엑셀(.xlsx, .xls)");
    }

    @Test
    @DisplayName("확장자가 없어도 거절한다")
    void 확장자가_없으면_거절() throws Exception {
        expectRejected(file("noextension", "hello".getBytes()), "A", "엑셀(.xlsx, .xls)");
    }

    @Test
    @DisplayName("빈 파일은 열어 볼 것도 없다")
    void 빈_파일은_거절() throws Exception {
        expectRejected(file("empty.xlsx", new byte[0]), "A", "비어 있습니다");
    }

    @Test
    @DisplayName("파서가 없는 공급사 코드는 받기 전에 막는다")
    void 모르는_공급사_코드는_거절() throws Exception {
        expectRejected(file("book.xlsx", new byte[] {1}), "Z", "지원하지 않는 공급사 코드");
    }

    @Test
    @DisplayName("확장자는 대소문자를 가리지 않는다 — .XLSX도 받는다")
    void 대문자_확장자도_받는다() throws Exception {
        mockMvc.perform(multipart("/api/vendor-catalog/upload-excel/A")
                        .file(file("BOOK.XLSX", new byte[] {1, 2})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test
    @DisplayName("정상 요청은 그대로 통과해 적재가 시작된다")
    void 정상_업로드는_통과() throws Exception {
        mockMvc.perform(multipart("/api/vendor-catalog/upload-excel/B")
                        .file(file("단가표.xls", new byte[] {1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));

        verify(importService).importVendorCatalogAsync(anyString(), anyString(), any());
    }
}
