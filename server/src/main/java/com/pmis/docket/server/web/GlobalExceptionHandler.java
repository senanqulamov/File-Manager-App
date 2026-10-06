package com.pmis.docket.server.web;

import com.pmis.docket.server.web.Dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("This file is too large to upload."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse r) {
            // Request problems (wrong parameters, unsupported type …): say what was wrong instead of a generic error.
            log.warn("Request not accepted: {}", e.getMessage());
            String detail = r.getBody().getDetail();
            return ResponseEntity.status(r.getStatusCode())
                    .body(new ApiError(detail == null ? "The request was not accepted by the server." : detail));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Something went wrong on the server. Please try again or contact IT."));
    }
}
