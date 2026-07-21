package com.example.esti.support;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TestSamples}의 부재 처리 정책 검증(§15): 로컬=스킵 / CI(strict)=fail 승격.
 */
class TestSamplesTest {

    private static final Path MISSING = Path.of("no-such-sample-xyz.xlsx");

    @Test
    void 샘플이_있으면_예외없이_통과() {
        assertDoesNotThrow(() -> TestSamples.requireSample(Path.of("pom.xml")));
    }

    @Test
    void strict면_없는샘플은_fail로_승격() {
        System.setProperty("sample.strict", "true");
        try {
            assertThrows(AssertionFailedError.class, () -> TestSamples.requireSample(MISSING));
        } finally {
            System.clearProperty("sample.strict");
        }
    }

    @Test
    void 비strict면_없는샘플은_스킵_abort() {
        System.setProperty("sample.strict", "false");
        try {
            assertThrows(TestAbortedException.class, () -> TestSamples.requireSample(MISSING));
        } finally {
            System.clearProperty("sample.strict");
        }
    }
}
