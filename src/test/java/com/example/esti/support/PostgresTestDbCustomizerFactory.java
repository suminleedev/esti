package com.example.esti.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code ESTI_TEST_DB=postgres}일 때 모든 {@code @SpringBootTest}를 PostgreSQL 컨테이너로 돌린다 (G11-1, D-8).
 *
 * <p>로컬은 Derby가 기준이고 데모는 PostgreSQL로 간다. 방언 차이를 배포 뒤가 아니라 테스트에서 잡으려고
 * 같은 테스트를 두 DB로 돌린다. 스위치가 없으면 아무것도 하지 않는다 — 평소 {@code ./mvnw test}는
 * Docker 없이 지금과 똑같이 Derby로 돈다.
 *
 * <p><b>왜 프로파일이 아니라 이 방식인가</b> — 각 테스트 클래스가 {@code @TestPropertySource}로 Derby
 * 인메모리 URL을 직접 적는다({@link TestsDoNotTouchRealDbTest}가 지키는 관례). 그 값은 프로파일·시스템
 * 프로퍼티보다 우선해서 밖에서 덮을 수 없다. 여기서는 {@code @DynamicPropertySource}와 같은 원리로
 * 환경 맨 앞에 프로퍼티 소스를 끼워 23개 클래스를 고치지 않고 한 곳에서 바꾼다.
 *
 * <p><b>왜 환경변수인가</b> — Maven CLI의 {@code -D}는 surefire가 포크한 테스트 JVM에 넘어가지 않는다
 * (deploy.yml 주석, 실측). 환경변수는 포크에 상속된다.
 *
 * <p><b>컨테이너는 하나, DB는 컨텍스트마다 새로</b> — Derby에서 클래스마다 인메모리 DB 이름이 달라 격리돼
 * 있던 것을 그대로 옮긴다. 컨텍스트 캐시에는 여러 컨텍스트가 동시에 살아 있어서, DB 하나를 같이 쓰면
 * 한 컨텍스트의 {@code create-drop}이 다른 컨텍스트의 테이블을 지운다.
 */
public class PostgresTestDbCustomizerFactory implements ContextCustomizerFactory {

    static final String SWITCH = "ESTI_TEST_DB";

    /** 데모 EC2의 compose와 같은 메이저로 맞춘다 (G11-4에서 재확인). */
    static final String IMAGE = "postgres:16-alpine";

    public static boolean enabled() {
        return "postgres".equalsIgnoreCase(System.getenv(SWITCH));
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        if (!enabled()) return null;
        // 슬라이스 테스트(@WebMvcTest)는 DB가 없다 — 쓸데없이 DB를 만들지 않는다. 추상 베이스에 붙은 것도 본다.
        boolean bootTest = MergedAnnotations.from(testClass, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                .isPresent(SpringBootTest.class);
        return bootTest ? new PostgresCustomizer() : null;
    }

    /** 컨텍스트 캐시 키에 들어간다 — 모두 같아야 캐시가 지금처럼 동작한다. */
    private static final class PostgresCustomizer implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
            String url = Container.newDatabase();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("estiPostgresTestDb", Map.of(
                    "spring.datasource.url", url,
                    "spring.datasource.username", Container.INSTANCE.getUsername(),
                    "spring.datasource.password", Container.INSTANCE.getPassword(),
                    "spring.datasource.driver-class-name", "org.postgresql.Driver",
                    // 컨텍스트 캐시에 컨텍스트가 20개 넘게 살아 있고 각자 풀을 든다. 기본 10개씩이면
                    // PostgreSQL 기본 한도(100)를 넘어 "too many clients"로 뒤쪽 클래스가 전부 못 뜬다
                    "spring.datasource.hikari.maximum-pool-size", "4",
                    "spring.datasource.hikari.minimum-idle", "1")));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PostgresCustomizer;
        }

        @Override
        public int hashCode() {
            return PostgresCustomizer.class.hashCode();
        }
    }

    /** JVM당 한 번만 띄운다. 종료는 Testcontainers(Ryuk)가 맡는다. */
    private static final class Container {

        static final PostgreSQLContainer<?> INSTANCE = start();
        private static final AtomicInteger SEQ = new AtomicInteger();

        private static PostgreSQLContainer<?> start() {
            PostgreSQLContainer<?> c = new PostgreSQLContainer<>(IMAGE)
                    // 컨텍스트 수 × 풀 크기에 여유를 둔다 (위 hikari 설정과 짝)
                    .withCommand("postgres", "-c", "max_connections=300");
            c.start();
            return c;
        }

        static String newDatabase() {
            String name = "t" + SEQ.incrementAndGet();
            try (Connection conn = DriverManager.getConnection(
                         INSTANCE.getJdbcUrl(), INSTANCE.getUsername(), INSTANCE.getPassword());
                 Statement st = conn.createStatement()) {
                st.execute("create database " + name);
            } catch (SQLException e) {
                throw new IllegalStateException("테스트 DB를 만들지 못했다: " + name, e);
            }
            String url = "jdbc:postgresql://" + INSTANCE.getHost() + ":"
                    + INSTANCE.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + name;
            // Derby는 @Table(schema="APP")의 스키마를 알아서 만들지만 PostgreSQL은 없으면 실패한다.
            // hibernate.hbm2ddl.create_namespaces로 맡기면 default_schema(APP)와 @Table(schema)를
            // 대소문자가 다른 두 스키마로 보고 drop/create를 두 번씩 시도해 경고가 쏟아진다 — 미리 만든다.
            try (Connection conn = DriverManager.getConnection(url, INSTANCE.getUsername(), INSTANCE.getPassword());
                 Statement st = conn.createStatement()) {
                st.execute("create schema app");
            } catch (SQLException e) {
                throw new IllegalStateException("테스트 스키마를 만들지 못했다: " + name, e);
            }
            return url;
        }
    }
}
