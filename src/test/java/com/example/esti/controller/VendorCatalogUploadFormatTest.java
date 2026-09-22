package com.example.esti.controller;

import com.example.esti.config.SecurityConfig;
import com.example.esti.dto.VendorOption;
import com.example.esti.excel.VendorAExcelParser;
import com.example.esti.excel.VendorBExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.VendorCatalogCommandService;
import com.example.esti.service.VendorCatalogQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 파일이 «선택한 공급사 양식»과 모순되면 jobId를 돌려주기 전에 400이다.
 *
 * <p>파서와 팩토리는 진짜를 쓴다 — 판정이 실제 파일에서 어떻게 나오는지가 검증 대상이다.
 * 파일은 커밋된 합성 샘플이라 실샘플 없이 CI에서 돈다.
 */
@WebMvcTest(VendorCatalogController.class)
@Import(SecurityConfig.class)
class VendorCatalogUploadFormatTest {

    /** 판정 대상은 진짜 파서다 — 목으로 두면 «실제 파일에서 어떻게 판정되는가»를 못 본다. */
    @TestConfiguration
    @Import({VendorExcelParserFactory.class, VendorAExcelParser.class, VendorBExcelParser.class})
    static class RealParsers {}

    private static final Path SAMPLES = Path.of("src/main/resources/static/samples");
    private static final Path TEMP_DIR = Path.of("uploads", "tmp");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VendorCatalogQueryService queryService;
    @MockitoBean private VendorCatalogCommandService commandService;
    @MockitoBean private CatalogImportAsyncService importService;
    @MockitoBean private ImportProgressStore progressStore;

    @BeforeEach
    void setUp() {
        when(progressStore.createJob()).thenReturn("job-1");
        when(queryService.getVendorOptions()).thenReturn(List.of(
                new VendorOption("A", "A사"), new VendorOption("B", "B사")));
    }

    private MockMultipartFile sample(String name) throws IOException {
        return new MockMultipartFile("file", name, "application/octet-stream", Files.readAllBytes(SAMPLES.resolve(name)));
    }

    private Set<Path> tempFiles() throws IOException {
        if (!Files.isDirectory(TEMP_DIR)) return Set.of();
        try (Stream<Path> s = Files.list(TEMP_DIR)) {
            return s.collect(Collectors.toSet());
        }
    }

    /** 거부는 job도 비동기 적재도 없고, 저장했던 임시파일도 남기지 않는다. */
    private void expectRejected(MockMultipartFile f, String vendorCode, String... messageParts) throws Exception {
        Set<Path> before = tempFiles();

        ResultActions result = mockMvc.perform(multipart("/api/vendor-catalog/upload-excel/" + vendorCode).file(f))
                .andExpect(status().isBadRequest());
        for (String part : messageParts) {
            result.andExpect(jsonPath("$.message").value(containsString(part)));
        }

        verify(progressStore, never()).createJob();
        verify(importService, never()).importVendorCatalogAsync(anyString(), anyString(), any());
        assertEquals(before, tempFiles(), "거부된 업로드의 임시파일이 남았다");
    }

    private void expectAccepted(MockMultipartFile f, String vendorCode) throws Exception {
        mockMvc.perform(multipart("/api/vendor-catalog/upload-excel/" + vendorCode).file(f))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));

        verify(importService).importVendorCatalogAsync(eq("job-1"), eq(vendorCode), any());
    }

    @Test
    void A_샘플을_A로_올리면_통과() throws Exception {
        expectAccepted(sample("vendor-a-sample.xlsx"), "A");
    }

    @Test
    void B_샘플을_B로_올리면_통과() throws Exception {
        expectAccepted(sample("vendor-b-sample.xlsx"), "B");
    }

    /** 사용자 보고의 원래 모양. 어느 공급사로 보이는지와 지금 무엇을 골랐는지를 함께 알려준다. */
    @Test
    void B_샘플을_A로_올리면_B사_양식이라고_거부() throws Exception {
        expectRejected(sample("vendor-b-sample.xlsx"), "A", "B사 양식으로 보입니다", "선택한 공급사: A사");
    }

    @Test
    void A_샘플을_B로_올리면_거부() throws Exception {
        expectRejected(sample("vendor-a-sample.xlsx"), "B", "B사 양식으로 보이지 않습니다");
    }

    /** 확장자만 엑셀인 깨진 파일 — 판정하려고 열다 실패해도 비동기로 넘기지 않고 여기서 400. */
    @Test
    void 열_수_없는_파일은_거부() throws Exception {
        expectRejected(new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", "not a workbook".getBytes()),
                "A", "엑셀 파일을 열 수 없습니다");
    }

    @Test
    void 엑셀이_아닌_파일은_기존_확장자_검사에_먼저_걸린다() throws Exception {
        expectRejected(new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes()),
                "A", "엑셀(.xlsx, .xls)");
    }
}
