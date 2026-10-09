package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The login page (Spring Security handles POST /login itself) and the 403 page. */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Spring Security forwards here when a logged-in user has the wrong role. */
    @GetMapping("/403")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("message", "Your account does not have access to that page.");
        return "error";
    }
}
