package com.pmis.docket.desktop.api;

/** A failure whose message can be shown to the user as-is. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP status, or 0 if the server could not be reached. */
    public int status() { return status; }

    public boolean isUnauthorized() { return status == 401; }

    public boolean isForbidden() { return status == 403; }

    public static String messageOf(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof ApiException a) return a.getMessage();
            c = c.getCause();
        }
        return "Something went wrong. Please try again.";
    }
}
