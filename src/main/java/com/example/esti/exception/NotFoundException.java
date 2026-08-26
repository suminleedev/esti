package com.example.esti.exception;

/** 요청한 리소스가 없음 → HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
