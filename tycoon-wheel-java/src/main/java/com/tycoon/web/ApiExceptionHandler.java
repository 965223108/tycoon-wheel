package com.tycoon.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Dto.ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new Dto.ApiError(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Dto.ApiError> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new Dto.ApiError("BAD_REQUEST", "请求体格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Dto.ApiError> handleAny(Exception e) {
        log.error("未处理的服务端异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Dto.ApiError("INTERNAL", "服务器开小差了，请稍后再试"));
    }
}
