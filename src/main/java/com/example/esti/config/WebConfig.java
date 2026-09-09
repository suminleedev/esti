package com.example.esti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }
}