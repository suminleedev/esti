package com.example.esti.crawler.service;

import com.example.esti.crawler.common.ProductImageCrawler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제조사 동기화 진입점의 <b>배선</b>을 확인한다.
 *
 * <p>원래 이 테스트는 "INUS는 미구현이라 빈이 없다"를 못박고 있었다. 파서·크롤러를 구현하면서
 * ({@code plan-inus-crawler.md} I-2·I-3) 그 전제가 바뀌었으므로, 이제는 <b>INUS가 실제로 등록됐는지</b>와
 * 모르는 제조사가 여전히 명시적 오류로 거부되는지를 본다. 지키려는 것은 처음과 같다 —
 * <b>아무것도 안 하고 "완료"를 돌려주는 거짓 신호를 막는 것.</b>
 *
 * <p>⚠️ {@code syncByMaker("INUS")}를 호출하지 않는다. 크롤러가 등록된 지금은 그 한 줄이
 * <b>실제 사이트로 요청을 보내고 이미지를 내려받는다.</b> 배선 확인에 네트워크는 필요 없다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:crawlerWiringTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class ProductImageSyncServiceTest {

    @Autowired private ProductImageSyncService service;
    @Autowired private List<ProductImageCrawler> crawlers;

    @Test
    @DisplayName("INUS 크롤러가 등록돼 있다 — 더 이상 '지원하지 않는 제조사'로 떨어지지 않는다")
    void INUS_크롤러가_등록돼_있다() {
        assertThat(crawlers)
                .filteredOn(c -> "INUS".equalsIgnoreCase(c.maker()))
                .singleElement()
                .satisfies(c -> assertThat(c.vendorCode()).isEqualTo("B"));
    }

    @Test
    @DisplayName("모르는 제조사는 조용한 no-op이 아니라 명시적 오류로 거부된다")
    void 모르는_제조사는_명시적_오류로_거부된다() {
        assertThatThrownBy(() -> service.syncByMaker("NO_SUCH_MAKER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 제조사");
    }
}
