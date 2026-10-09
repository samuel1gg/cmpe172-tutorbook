package edu.sjsu.cmpe172.tutorbook.exception;

/** The user is logged in but does not own the resource (e.g. someone else's appointment). Mapped to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
