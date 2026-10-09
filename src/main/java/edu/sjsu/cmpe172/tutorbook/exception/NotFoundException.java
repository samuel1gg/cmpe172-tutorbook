package edu.sjsu.cmpe172.tutorbook.exception;

/** A requested row does not exist. Mapped to 404 by the exception handlers. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
