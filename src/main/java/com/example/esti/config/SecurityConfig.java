package com.example.esti.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

/**
 * 관리자 API만 잠근다 (D-7 / 분석서 Task 10).
 *
 * <p><b>전면 로그인 화면이 아니다.</b> {@code /api/admin/**}에만 HTTP Basic을 걸고 나머지는
 * 그대로 연다. 프론트가 이 경로를 부르지 않음이 확인돼 있어 화면이 깨지지 않는다.
 * 전면 보호는 프론트 작업이 함께 필요해 별건이다.
 *
 * <p>지금 이 경로에 있는 것은 이미지 크롤러 실행뿐이고, 데모에서는 그 컨트롤러 자체가
 * 뜨지 않는다(D-4). 그래서 이 설정은 <b>앞으로 늘어날 관리자 기능</b>을 위한 자리다 —
 * 「무인증」이 기본값이던 상태를 뒤집어, 관리자 경로에 뭘 더하면 <b>기본이 잠김</b>이 되게 한다.
 *
 * <p><b>{@code local}에서는 끈다</b>({@code app.admin.auth-enabled=false}). 실사용은 루프백에만
 * 열려 있는 단일 사용자 실행이라(D-10) 자물쇠가 막는 상대가 자기 자신뿐이고, 크롤러는 사람이
 * 손으로 돌리는 명령이다. <b>기본값은 켜짐</b>이므로 프로파일을 안 주고 띄우는 경우가 아니면
 * 잠긴 채로 남는다 — 끄는 쪽이 명시적인 선택이다.
 *
 * <p>CSRF는 {@code /api/**}에서 끈다. 브라우저 폼이 아니라 API 클라이언트가 부르는 자리다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 관리자 경로를 잠글 것인가. 기본은 <b>켜짐</b>이고 {@code local}만 끈다.
     *
     * <p>프로파일 이름이 아니라 프로퍼티로 가르는 이유 — 끄는 근거가 「local이라서」가 아니라
     * 「루프백에만 열린 단일 사용자 실행이라서」이기 때문이다. 그 전제가 깨지면 여기만 되돌리면 된다.
     */
    @Value("${app.admin.auth-enabled:true}")
    private boolean authEnabled;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    /** 비워 두면 기동할 때마다 새로 만들어 로그에 남긴다 (Boot의 기본 동작과 같은 방식). */
    @Value("${app.admin.password:}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> {
                    if (authEnabled) auth.requestMatchers("/api/admin/**").authenticated();
                    auth.anyRequest().permitAll();
                })
                .httpBasic(Customizer.withDefaults())
                // 세션을 만들지 않는다 — Basic은 요청마다 자격을 들고 온다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * 관리자 계정.
     *
     * <p>잠금을 꺼도 빈은 만든다 — 없으면 Boot가 자기 기본 계정을 만들어 기동할 때마다
     * 비밀번호를 로그에 찍는다. 쓰지도 않는 값이 매번 로그에 남을 이유가 없다.
     */
    @Bean
    public UserDetailsService adminUser(PasswordEncoder encoder) {
        String password = adminPassword;
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
            // 잠가 두지 않았으면 아무도 쓰지 않을 값이라 알릴 것도 없다.
            if (authEnabled) {
                log.warn("""
                        관리자 비밀번호가 설정돼 있지 않아 임시로 만들었다 (기동할 때마다 바뀐다):
                          계정 {} / 비밀번호 {}
                        고정하려면 환경변수 ADMIN_PASSWORD(또는 app.admin.password)를 준다.""",
                        adminUsername, password);
            }
        }
        return new InMemoryUserDetailsManager(User.withUsername(adminUsername)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
