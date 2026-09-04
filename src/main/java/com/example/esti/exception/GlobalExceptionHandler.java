package com.example.esti.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 예외 → HTTP 상태 매핑.
 *
 * 아래 «클라이언트 잘못» 묶음이 따로 있는 이유는 하나다(F-020): 예전에는 도메인 예외 셋만
 * 매핑하고 나머지를 전부 {@code Exception} catch-all로 흘려보냈다. 그래서 경로변수 타입이
 * 안 맞거나, 본문이 없거나, JSON이 깨졌을 때 <b>500 «서버 내부 오류»</b>가 나갔고
 * 서버 로그에는 «처리되지 않은 예외»로 스택트레이스가 통째로 쌓였다.
 * 클라이언트가 고칠 수 있는 실수는 400으로 돌려주고, 로그도 스택 없이 한 줄만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(int status, String message) {}

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidStateException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * DTO 검증 실패 — 어느 필드가 왜 걸렸는지 그대로 돌려준다.
     * 여러 건이면 «·»로 이어 붙인다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" · "));
        if (message.isBlank()) message = "입력값을 확인해 주세요.";
        log.warn("요청 검증 실패: {}", message);
        return body(HttpStatus.BAD_REQUEST, message);
    }

    /** 본문이 없거나 JSON이 깨졌다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽을 수 없음: {}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "요청 본문이 없거나 형식이 올바르지 않습니다.");
    }

    /** 경로변수·쿼리 파라미터의 타입이 안 맞는다 (예: /api/proposals/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("파라미터 타입 불일치: {}={}", e.getName(), e.getValue());
        return body(HttpStatus.BAD_REQUEST,
                "'" + e.getName() + "' 값이 올바르지 않습니다: " + e.getValue());
    }

    /** 필수 쿼리 파라미터가 빠졌다 (예: /api/master-codes에 type 없음). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("필수 파라미터 누락: {}", e.getParameterName());
        return body(HttpStatus.BAD_REQUEST, "'" + e.getParameterName() + "' 값이 필요합니다.");
    }

    /**
     * DTO 검증을 빠져나간 값이 DB 제약에 걸렸을 때의 최후 방어선.
     *
     * 여기까지 왔다는 건 <b>검증이 못 잡은 경로가 있다</b>는 뜻이라 원인을 로그에 남긴다.
     * 다만 사용자에게 500을 주는 것보다는 400이 맞다 — 저장할 수 없는 값을 보낸 쪽은 요청이다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반 — 요청 단계 검증이 놓친 값이다", e);
        return body(HttpStatus.BAD_REQUEST, "저장할 수 없는 값이 있습니다. 입력 길이와 범위를 확인해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
