package com.example.esti.exception;

/**
 * 너무 이른 재실행 — 쿨다운에 걸렸다 → HTTP 429.
 *
 * <p>«이미 실행 중»(409, {@link InvalidStateException})과 구분한다. 같은 거절이라도
 * 사용자가 할 일이 다르다 — 409는 <b>끝나기를 기다리는 것</b>이고, 429는 <b>지금 안 해도 되는 것</b>이다.
 */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String message) { super(message); }
}
