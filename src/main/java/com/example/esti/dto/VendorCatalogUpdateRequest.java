package com.example.esti.dto;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 카탈로그 행(가격 라인) 인라인 수정 요청.
 * 브랜드(Vendor)는 여러 상품이 공유하므로 이 화면에서 수정하지 않는다.
 *
 * <p><b>이 PUT은 전체 교체다.</b> 서비스가 여기 담긴 값을 그대로 덮어쓰므로,
 * 일부만 담아 보내면 나머지가 전부 {@code null}이 된다. 예전에는 그걸 그대로 받아들여
 * 단가만 보내도 분류·제품명·품번·비고·이미지가 한꺼번에 지워졌다(F-017).
 * 이제 컨트롤러가 {@link #requiredKeys()}로 <b>본문에 키가 다 있는지</b> 먼저 확인한다.
 *
 * <p>«키가 있는지»를 보는 것이지 «값이 null이 아닌지»를 보는 게 아니다 —
 * 소분류·비고·설명·이미지는 원래 비어 있을 수 있고, 화면도 그 값을 null로 실어 보낸다.
 * 그래서 {@code @NotNull}로는 «빠뜨림»과 «비우려는 의도»를 가릴 수 없다.
 */
public record VendorCatalogUpdateRequest(
        String categoryLarge,
        String categorySmall,
        String productName,
        String mainItemCode,
        String remark,
        BigDecimal unitPrice,
        String description,
        String imageUrl,
        String unit          // 단위(SET/EA 등). 견적서 C열에 쓴다 (O-1b)
) {

    /**
     * 전체 교체이므로 본문에 있어야 하는 키들.
     * 레코드 컴포넌트에서 뽑으므로 <b>필드를 늘리면 자동으로 따라온다</b> — 목록을 따로 적어 두면 어긋난다.
     */
    public static List<String> requiredKeys() {
        return Arrays.stream(VendorCatalogUpdateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
