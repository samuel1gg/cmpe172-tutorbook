package edu.sjsu.cmpe172.tutorbook.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import edu.sjsu.cmpe172.tutorbook.controller.CatalogApiController;
import edu.sjsu.cmpe172.tutorbook.controller.HealthController;
import edu.sjsu.cmpe172.tutorbook.mocknotification.MockNotificationController;

/**
 * Same mapping for the JSON endpoints, but the body is an RFC 9457 ProblemDetail:
 * {"status":409,"title":"Conflict","detail":"..."} with no stack trace.
 */
@RestControllerAdvice(assignableTypes = {CatalogApiController.class, HealthController.class,
        MockNotificationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception ex) {
        HttpStatusCode status = ErrorMessages.status(ex);
        return ProblemDetail.forStatusAndDetail(status, ErrorMessages.message(ex, status));
    }
}
