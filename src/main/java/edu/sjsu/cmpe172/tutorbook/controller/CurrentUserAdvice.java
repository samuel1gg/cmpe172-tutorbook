package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/** Makes the logged-in user (or null) available to every page as ${currentUser} for the nav bar. */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    public AppUser currentUser(@AuthenticationPrincipal AppUser user) {
        return user;
    }
}
