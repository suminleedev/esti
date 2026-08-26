package com.example.esti.exception;

/** 요청 값 검증 실패 → HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
