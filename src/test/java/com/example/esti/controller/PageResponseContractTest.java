package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogView;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.VendorCatalogCommandService;
import com.example.esti.service.VendorCatalogQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 페이지 응답의 <b>모양</b>을 고정한다 (F-014 잔여).
 *
 * <p>«Serializing PageImpl instances as-is is not supported» 경고를 없애려고
 * {@code spring.data.web.pageable.serialization-mode=via-dto}로 바꿨다. 그러면 메타가
 * 최상위에 평평하게 깔리지 않고 {@code page} 아래로 모인다:
 *
 * <pre>
 *   { "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }
 * </pre>
 *
 * <p><b>이건 설정 한 줄로 되돌아가는 계약이다.</b> 프론트는 이 모양을 읽으므로,
 * 설정이 사라지거나 direct로 되돌아가면 목록이 조용히 «0건»으로 보인다 — 요청은 200이고
 * {@code content}는 그대로라서 화면이 비지도 않는다. 그래서 여기서 못 박는다.
 */
@WebMvcTest(VendorCatalogController.class)
class PageResponseContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VendorCatalogQueryService queryService;
    @MockitoBean private VendorCatalogCommandService commandService;
    @MockitoBean private CatalogImportAsyncService importService;
    @MockitoBean private ImportProgressStore progressStore;
    @MockitoBean private VendorExcelParserFactory parserFactory;

    private static final VendorCatalogView ROW = new VendorCatalogView(
            1L, 10L, "A", "A사", "양변기", "투피스양변기", "양변기 세트",
            "T-1", null, "양변기 세트", null, BigDecimal.ONE, "양변기",
            null, null, null, "SET", null, null, null);   // 끝: collectionName(시리즈명)·setSummary·mainUnitPrice

    @Test
    void 페이지_메타는_page_아래로_모인다() throws Exception {
        when(queryService.getVendorCatalogPage(eq("A"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ROW), PageRequest.of(1, 20), 45));

        mockMvc.perform(get("/api/vendor-catalog/page/A").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].mainItemCode").value("T-1"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(45))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void 최상위에_평평한_메타를_두지_않는다() throws Exception {
        // direct 모드로 되돌아가면 여기에 값이 실린다. 그 회귀를 잡는 자리다.
        when(queryService.getVendorCatalogPage(eq("A"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ROW), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/vendor-catalog/page/A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @Test
    void 공급사_전체_경로도_같은_모양이다() throws Exception {
        // 페이지 크기(5)가 총계(7)를 넘지 않게 잡는다 — PageImpl은 "마지막 페이지가 덜 찼으면
        // 총계 쪽이 틀렸다"고 보고 offset+content로 되고쳐 버린다. 앞뒤가 맞는 값이어야 한다.
        when(queryService.getVendorCatalogPageAll(any(), any()))
                .thenReturn(new PageImpl<>(List.of(ROW), PageRequest.of(0, 5), 7));

        mockMvc.perform(get("/api/vendor-catalog/page/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(7))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }
}
