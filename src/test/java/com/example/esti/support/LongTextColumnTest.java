package com.example.esti.support;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 긴 본문 컬럼이 두 DB에서 같은 의미로 동작하는지 지킨다 (G11-1).
 *
 * <p>{@code @Lob String}은 PostgreSQL에서 {@code oid}(대형 객체)가 된다 — 행을 지워도 본문이 고아로 남고,
 * 트랜잭션 밖에서 읽으면 실패한다. 그래서 {@code length = Length.LONG32}로 바꿨고, 그러면
 * PostgreSQL {@code text} / Derby {@code CLOB}이 된다. 누가 {@code @Lob}을 되살리면 여기서 깨진다.
 *
 * <p>값 바인딩도 본다 — Derby {@code VARCHAR} 한도(약 3만 2천 자)를 넘는 본문이 트랜잭션 밖 조회로 그대로 돌아와야 한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:longTextColumn;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class LongTextColumnTest {

    @Autowired DataSource dataSource;
    @Autowired VendorRepository vendorRepository;
    @Autowired VendorProductRepository productRepository;

    @Test
    void 긴_본문_컬럼은_대형_객체가_아니다() throws Exception {
        List<String> types = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean pg = "PostgreSQL".equals(conn.getMetaData().getDatabaseProductName());
            String sql = pg
                    ? "select data_type from information_schema.columns where table_schema = 'app' "
                            + "and ((table_name = 'vendor_product' and column_name = 'raw_tag_text') "
                            + "or (table_name = 'proposal_template_line' and column_name = 'description'))"
                    : "select cast(c.columndatatype as varchar(128)) from sys.syscolumns c "
                            + "join sys.systables t on t.tableid = c.referenceid "
                            + "where (t.tablename = 'VENDOR_PRODUCT' and c.columnname = 'RAW_TAG_TEXT') "
                            + "or (t.tablename = 'PROPOSAL_TEMPLATE_LINE' and c.columnname = 'DESCRIPTION')";
            try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
                while (rs.next()) types.add(rs.getString(1));
            }
            assertThat(types).hasSize(2).allSatisfy(t -> assertThat(t).isEqualTo(pg ? "text" : "CLOB(2147483647)"));
        }
    }

    @Test
    void Derby_VARCHAR_한도를_넘는_본문이_그대로_돌아온다() {
        Vendor vendor = vendorRepository.save(new Vendor(null, "LT", "긴본문"));
        String body = "가".repeat(40_000);
        Long id = productRepository.save(VendorProduct.builder().vendor(vendor).rawTagText(body).build()).getId();

        // 리포지토리 밖에서 트랜잭션 없이 읽는다 — oid였다면 PostgreSQL에서 여기서 실패한다
        assertThat(productRepository.findById(id).orElseThrow().getRawTagText()).isEqualTo(body);
    }
}
