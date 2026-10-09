package edu.sjsu.cmpe172.tutorbook.exception;

/** Input passed annotation validation but breaks a business rule (e.g. a start time in the past). Mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
