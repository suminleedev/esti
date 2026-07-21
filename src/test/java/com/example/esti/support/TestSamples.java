package com.example.esti.support;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 실데이터 샘플(gitignore된 {@code docs/samples/...}) 의존 테스트의 <b>부재 처리 정책</b>.
 *
 * <p>샘플이 없을 때 로컬에선 스킵(assumeTrue 관례)하되, <b>CI에선 스킵을 fail로 승격</b>한다
 * (§15: CI에서 스킵=거짓 초록 방지). CI 여부는 환경변수 {@code CI}(truthy) 또는 시스템 속성
 * {@code -Dsample.strict=true/false}로 판별하며, 명시 속성이 환경변수보다 우선한다.</p>
 *
 * <p>샘플 없이도 CI가 실제로 검증하는 최소 커버리지는 합성 fixture 테스트
 * ({@code SyntheticBCatalogParseTest})가 담당한다 — 그쪽은 런타임 생성이라 항상 실행된다.</p>
 */
public final class TestSamples {

    private TestSamples() {}

    /** 샘플이 있으면 진행, 없으면 로컬=스킵 / CI(strict)=fail. */
    public static void requireSample(Path sample) {
        if (Files.exists(sample)) return;
        String msg = "샘플/픽스처 없음: " + sample;
        if (strict()) {
            Assertions.fail(msg + " — CI(strict)에선 스킵을 fail로 승격(§15). "
                    + "실샘플을 CI에 제공하거나 -Dsample.strict=false로 로컬 스킵 유지.");
        }
        Assumptions.abort(msg + " (로컬 스킵)");
    }

    /** strict 모드 여부: -Dsample.strict가 있으면 그 값, 없으면 CI 환경변수 truthy. */
    public static boolean strict() {
        String prop = System.getProperty("sample.strict");
        if (prop != null && !prop.isBlank()) return Boolean.parseBoolean(prop.trim());
        String ci = System.getenv("CI");
        return ci != null && !ci.isBlank() && !ci.equalsIgnoreCase("false") && !ci.equals("0");
    }
}
