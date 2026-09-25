package edu.sjsu.cmpe172.tutorbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import edu.sjsu.cmpe172.tutorbook.service.CatalogService;

/** Page Controller for the home page: one controller handles one page. */
@Controller
public class HomeController {

    private final CatalogService catalogService;

    public HomeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("home", catalogService.getHome());
        return "home";
    }
}
