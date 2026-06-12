package com.ragstudy.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null ? exception.getStatusCode().toString() : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不正确" : error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse("文件过大，请上传 50MB 以内的文件，或通过 MAX_UPLOAD_FILE_SIZE 调整限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        String message = buildErrorMessage(exception);

        if (message == null || message.isBlank()) {
            message = "服务器处理失败";
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(message));
    }

    private String buildErrorMessage(Exception exception) {
        String message = exception.getMessage();
        Throwable rootCause = exception;

        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause != exception && rootCause.getMessage() != null && !rootCause.getMessage().isBlank()) {
            return (message == null || message.isBlank()) ? rootCause.getMessage() : message + "：" + rootCause.getMessage();
        }

        return message;
    }
}
