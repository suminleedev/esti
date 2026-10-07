package com.example.esti.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트가 실제로 어느 DB에 붙었는지 확인한다 (G11-1).
 *
 * <p>{@link PostgresTestDbCustomizerFactory}가 조용히 무시되면 「PostgreSQL에서도 초록」이 거짓이 된다 —
 * 스위치를 켰는데 Derby로 돌았다면 여기서 깨진다. 스위치가 없으면 지금처럼 Derby여야 한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:activeTestDb;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/test-product-images"
})
class ActiveTestDbTest {

    @Autowired
    DataSource dataSource;

    @Test
    void 스위치대로_DB에_붙는다() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            String expected = PostgresTestDbCustomizerFactory.enabled() ? "PostgreSQL" : "Apache Derby";
            assertThat(product).as("ESTI_TEST_DB=%s", System.getenv("ESTI_TEST_DB")).isEqualTo(expected);
        }
    }
}
