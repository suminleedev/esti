package com.example.esti.service;

import com.example.esti.dto.VendorOption;
import com.example.esti.entity.Vendor;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면의 «공급사» 선택지 (D-12).
 *
 * <p>목록과 이름의 출처가 다르다 — 목록은 <b>파서가 있는 코드</b>, 이름은 <b>DB 행</b>이다.
 * 그렇게 나눈 이유가 이 테스트의 내용이다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:vendoroptions;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class VendorOptionsTest {

    @Autowired private VendorCatalogQueryService queryService;
    @Autowired private VendorRepository vendorRepository;

    @Test
    @DisplayName("공급사 행이 하나도 없어도 선택지는 비지 않는다")
    void 빈_DB에서도_선택지가_있다() {
        vendorRepository.deleteAll();

        List<VendorOption> options = queryService.getVendorOptions();

        // 공급사 행은 «첫 적재 때» 생긴다. DB만 보고 목록을 만들면 아무것도 올리지 않은 상태에서
        // 선택지가 비어 업로드 자체를 시작할 수 없다 — 닭이 먼저냐 달걀이 먼저냐가 된다.
        assertThat(options).extracting(VendorOption::vendorCode).containsExactly("A", "B");
        assertThat(options).extracting(VendorOption::vendorName).containsExactly("A사", "B사");
    }

    @Test
    @DisplayName("공급사 행이 있으면 그 이름을 쓴다")
    void DB에_있으면_DB_이름을_쓴다() {
        vendorRepository.deleteAll();
        Vendor vendor = new Vendor();
        vendor.setVendorCode("A");
        vendor.setVendorName("가나 위생도기");
        vendorRepository.save(vendor);

        // 데모 배포본이 이 경로로 가칭을 보여준다 — 이름을 화면에 박아 두면 그게 안 된다.
        assertThat(queryService.getVendorOptions())
                .containsExactly(new VendorOption("A", "가나 위생도기"), new VendorOption("B", "B사"));
    }
}
