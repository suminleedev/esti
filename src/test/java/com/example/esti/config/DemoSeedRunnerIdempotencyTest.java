package com.example.esti.config;

import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데모 프로파일로 실제로 띄워, 시드가 <b>몇 번 돌아도 한 벌</b>인지 본다 (G11-2).
 *
 * <p>데모 DB가 영속이 되면서 「기동 = 빈 DB」 전제가 없어졌다. 컨텍스트 기동 때 한 번 돈 시드를
 * 다시 돌리는 것으로 재시작을 흉내 낸다. {@code ESTI_TEST_DB=postgres}면 실제 데모와 같은 DB에서 돈다.
 */
@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:demoSeedIdempotency;create=true",
        // 데모 프로파일은 PostgreSQL 드라이버를 지정한다 — 인메모리 Derby로 돌 때는 되돌린다
        "spring.datasource.driver-class-name=org.apache.derby.jdbc.EmbeddedDriver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.crawler.image-dir=target/demo-seed-test-images"
})
class DemoSeedRunnerIdempotencyTest {

    @Autowired DemoSeedRunner runner;
    @Autowired VendorProductRepository productRepository;
    @Autowired VendorRepository vendorRepository;

    @Test
    void 다시_돌려도_한_벌이고_지워진_그림은_다시_놓인다() throws Exception {
        long products = productRepository.count();
        long vendors = vendorRepository.count();
        assertThat(products).as("기동 때 시드가 들어갔다").isPositive();
        assertThat(vendorRepository.findAll()).allSatisfy(v -> assertThat(v.getVendorName()).endsWith("사"));

        Path icon = Path.of("target/demo-seed-test-images/demo-product.png");
        Files.deleteIfExists(icon); // 볼륨 없는 재배포로 파일만 사라진 상황

        runner.run(null);

        assertThat(productRepository.count()).isEqualTo(products);
        assertThat(vendorRepository.count()).isEqualTo(vendors);
        assertThat(icon).exists();
    }
}
