package com.example.esti;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 컨텍스트가 뜨는지만 본다.
 *
 * <p>인메모리 DB로 돌리는 이유(F-006) — 예전에는 이 클래스만 오버라이드가 없어
 * {@code application.properties}의 파일 DB({@code ./data/estimateDB})를 그대로 열었다.
 * 그래서 앱을 켜 둔 채 {@code ./mvnw test}를 돌리면 Derby 락이 부딪혀 여기서 빌드가 깨졌고,
 * {@code ddl-auto=update}라 실DB 스키마에 손을 댈 여지도 있었다.
 * 다른 {@code @SpringBootTest} 클래스들과 같은 방식으로 맞춘다.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:derby:memory:contextLoads;create=true",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"app.crawler.image-dir=target/test-product-images"
})
class EstiApplicationTests {

	@Test
	void contextLoads() {
	}

}
