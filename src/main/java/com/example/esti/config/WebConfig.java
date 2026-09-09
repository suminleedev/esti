package com.example.esti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 허용 오리진 — 프로파일이 정한다(D-6). 쉼표로 여럿을 줄 수 있고,
     * 환경변수 {@code APP_CORS_ALLOWED_ORIGINS}로도 덮인다(배포 도메인이 정해지면 그쪽).
     *
     * <p>하드코딩돼 있던 값이다. 배포본은 오리진이 다른데 코드를 고쳐야 바뀌는 상태였다.
     */
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 엑셀 다운로드 파일명을 프론트가 읽으려면 이 헤더가 노출돼야 한다.
                // dev는 Vite 프록시라 same-origin이지만, :8080을 직접 부르는 경우를 위해 명시한다.
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION)
                .allowCredentials(true);
    }

    /** 프론트 진입점. 빌드된 프론트가 jar에 들어 있을 때만 존재한다(D-8). */
    private static final Resource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");

        // 프론트를 같은 jar에 담으면(D-8) 주소창에 직접 친 /proposal/list 같은 경로가 서버로 온다.
        // 라우팅은 브라우저에서 일어나므로 서버는 그런 파일을 갖고 있지 않다 —
        // 없는 정적 파일이면 index.html을 돌려주고 나머지 판단을 프론트에 넘긴다.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) return requested;

                        // API는 대신 답하지 않는다 — 없는 API는 404여야 한다.
                        // 확장자가 있으면 진짜 파일을 찾는 요청이므로 그것도 404로 둔다.
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) return null;

                        // 프론트를 담지 않은 빌드(개발 중 백엔드만 띄운 경우)에서는 index.html이 없다.
                        return INDEX.exists() ? INDEX : null;
                    }
                });
    }
}