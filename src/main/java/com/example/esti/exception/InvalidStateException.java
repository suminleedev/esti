package com.example.esti.exception;

/** 상태 규칙 위반(예: SENT 수정 시도) → HTTP 409. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) { super(message); }
}
