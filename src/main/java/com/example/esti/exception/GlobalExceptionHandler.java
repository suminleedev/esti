package com.example.esti.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /** 업로드 한도 — 프로파일마다 다르다(실사용 100MB / 데모 2MB). 거절 메시지에 그대로 적는다. */
    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private String maxFileSize;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidStateException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    /** 쿨다운에 걸렸다. «이미 실행 중»(409)과 달리 기다릴 일이 아니라 안 해도 되는 일이다. */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
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

    /**
     * 그런 경로가 없다 (F-020과 같은 갈래).
     *
     * <p>컨트롤러가 없는 URL은 정적 리소스 조회로 흘러가 {@link NoResourceFoundException}이 되는데,
     * 그게 아래 catch-all에 걸려 <b>500 «서버 내부 오류»</b>로 나갔다. 주소를 잘못 부른 것은
     * 클라이언트 실수라 404가 맞고, 서버 로그에 스택을 쌓을 일도 아니다.
     *
     * <p>{@code demo} 프로파일에서 크롤러 컨트롤러를 빼면(D-4) 그 경로가 바로 이 자리로 온다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("없는 경로: {}", e.getResourcePath());
        return body(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.");
    }

    /**
     * 파일이 한도보다 크다 (F-020과 같은 갈래).
     *
     * <p>멀티파트 해석 단계에서 던져지는 예외라 컨트롤러에 닿지도 않고 catch-all로 흘러
     * <b>500 «서버 내부 오류»</b>가 나갔다. 올린 쪽이 고칠 수 있는 일이므로 413으로 답하고,
     * <b>한도가 얼마인지</b> 알려 준다 — 데모(2MB)와 실사용(100MB)이 다르기 때문이다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("업로드 한도 초과 (한도 {})", maxFileSize);
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다. " + maxFileSize + "까지 올릴 수 있습니다.");
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
