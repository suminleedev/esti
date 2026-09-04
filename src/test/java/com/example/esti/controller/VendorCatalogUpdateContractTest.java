package com.example.esti.controller;

import com.example.esti.dto.VendorCatalogUpdateRequest;
import com.example.esti.dto.VendorCatalogView;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.progress.ImportProgressStore;
import com.example.esti.service.CatalogImportAsyncService;
import com.example.esti.service.VendorCatalogCommandService;
import com.example.esti.service.VendorCatalogQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카탈로그 수정 PUT의 계약: <b>전체 교체</b> (F-017).
 *
 * 예전에는 단가만 담아 보내도 200이 나가고 분류·제품명·품번·비고·이미지가 조용히 지워졌다.
 * 화면은 늘 전체를 보내 멀쩡했으므로 API를 직접 부를 때만 드러났다.
 */
@WebMvcTest(VendorCatalogController.class)
class VendorCatalogUpdateContractTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VendorCatalogQueryService queryService;
    @MockitoBean private VendorCatalogCommandService commandService;
    @MockitoBean private CatalogImportAsyncService importService;
    @MockitoBean private ImportProgressStore progressStore;
    @MockitoBean private VendorExcelParserFactory parserFactory;   // 업로드 가드가 쓴다(F-009)

    /** 화면이 실제로 보내는 모양 — 뷰 객체를 통째로 싣는다(관리 대상 9개 + 그 외). */
    private static final String FULL_BODY = """
            {
              "vendorItemPriceId": 1, "vendorProductId": 2, "vendorCode": "B", "vendorName": "공급사",
              "categoryLarge": "세면기", "categorySmall": "단독", "productName": "제품",
              "mainItemCode": "IL501", "oldItemCode": null, "vendorItemName": "제품",
              "remark": null, "unitPrice": 1000, "priceBasis": "세면기",
              "imageUrl": null, "description": null, "specs": null,
              "unit": "SET", "setSummary": null, "mainUnitPrice": 1000
            }
            """;

    @Test
    void 일부만_보내면_400이고_빠진_항목을_알려준다() throws Exception {
        mockMvc.perform(put("/api/vendor-catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":77777,\"unit\":\"EA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("전체 교체")))
                .andExpect(jsonPath("$.message").value(containsString("categoryLarge")))
                .andExpect(jsonPath("$.message").value(containsString("productName")));
    }

    @Test
    void 키가_다_있으면_통과하고_값이_그대로_전달된다() throws Exception {
        when(commandService.update(eq(1L), any())).thenReturn(null);

        mockMvc.perform(put("/api/vendor-catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<VendorCatalogUpdateRequest> captor =
                ArgumentCaptor.forClass(VendorCatalogUpdateRequest.class);
        verify(commandService).update(eq(1L), captor.capture());

        VendorCatalogUpdateRequest sent = captor.getValue();
        assertThat(sent.categoryLarge()).isEqualTo("세면기");
        assertThat(sent.productName()).isEqualTo("제품");
        assertThat(sent.mainItemCode()).isEqualTo("IL501");
        assertThat(sent.unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(sent.unit()).isEqualTo("SET");
    }

    /**
     * 값이 null인 키는 통과해야 한다 — 소분류·비고·설명·이미지는 원래 비어 있을 수 있고,
     * 화면도 그렇게 보낸다. «키가 있는지»를 보는 것이지 «값이 null이 아닌지»를 보는 게 아니다.
     */
    @Test
    void 값이_null이어도_키가_있으면_통과한다() throws Exception {
        when(commandService.update(eq(1L), any())).thenReturn((VendorCatalogView) null);

        mockMvc.perform(put("/api/vendor-catalog/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<VendorCatalogUpdateRequest> captor =
                ArgumentCaptor.forClass(VendorCatalogUpdateRequest.class);
        verify(commandService).update(eq(1L), captor.capture());
        assertThat(captor.getValue().remark()).isNull();
        assertThat(captor.getValue().imageUrl()).isNull();
    }

    @Test
    void 관리_대상_키_목록은_레코드_컴포넌트에서_나온다() {
        assertThat(VendorCatalogUpdateRequest.requiredKeys())
                .containsExactly("categoryLarge", "categorySmall", "productName", "mainItemCode",
                        "remark", "unitPrice", "description", "imageUrl", "unit");
    }
}
