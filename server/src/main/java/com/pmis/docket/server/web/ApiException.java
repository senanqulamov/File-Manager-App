package com.pmis.docket.server.web;

import org.springframework.http.HttpStatus;

/** An error whose message is safe to show to the user. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND, what + " was not found. It may have been moved or deleted."); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}
