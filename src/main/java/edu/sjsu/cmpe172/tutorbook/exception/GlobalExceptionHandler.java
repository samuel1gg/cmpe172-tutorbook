package edu.sjsu.cmpe172.tutorbook.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/**
 * Global exception handling for the HTML pages: every exception becomes the
 * error.html page with the right HTTP status (404, 409, 403, 400, 500) and a
 * friendly message. The stack trace is logged on the server, never sent.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handle(Exception ex) {
        HttpStatusCode status = ErrorMessages.status(ex);
        if (status.is5xxServerError()) {
            log.error("Unexpected error", ex);
        } else {
            log.info("{} -> {}: {}", ex.getClass().getSimpleName(), status.value(), ex.getMessage());
        }
        ModelAndView mav = new ModelAndView("error", status);
        mav.addObject("status", status.value());
        mav.addObject("message", ErrorMessages.message(ex, status));
        mav.addObject("currentUser", currentUser()); // @ModelAttribute advice doesn't run for error views
        return mav;
    }

    private static AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AppUser user) ? user : null;
    }
}
