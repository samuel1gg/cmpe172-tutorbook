package edu.sjsu.cmpe172.tutorbook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps an exception to an HTTP status and a safe, user-facing message (never a stack trace). */
final class ErrorMessages {

    private ErrorMessages() {
    }

    static HttpStatusCode status(Exception ex) {
        if (ex instanceof NotFoundException) return HttpStatus.NOT_FOUND;
        if (ex instanceof SlotConflictException) return HttpStatus.CONFLICT;
        if (ex instanceof ForbiddenException) return HttpStatus.FORBIDDEN;
        if (ex instanceof BadRequestException
                || ex instanceof MethodArgumentTypeMismatchException
                || ex instanceof MissingServletRequestParameterException
                || ex instanceof HandlerMethodValidationException) return HttpStatus.BAD_REQUEST;
        if (ex instanceof ErrorResponse er) return er.getStatusCode(); // Spring's own (405, 404 for static files, ...)
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static String message(Exception ex, HttpStatusCode status) {
        if (ex instanceof NotFoundException || ex instanceof SlotConflictException
                || ex instanceof ForbiddenException || ex instanceof BadRequestException) {
            return ex.getMessage(); // our own messages are written for users
        }
        if (status.value() == 400) return "The request has an invalid value.";
        if (status.value() == 404) return "Page not found.";
        if (status.is5xxServerError()) return "Something went wrong on our side. Please try again.";
        return "The request could not be completed.";
    }
}
