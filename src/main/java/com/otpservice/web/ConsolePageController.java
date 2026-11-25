package com.otpservice.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * {@code /console} → the console's page. A redirect rather than a forward, so the browser's address ends in
 * {@code /console/index.html} and the page's relative links to its script and stylesheet resolve. The page
 * itself is public; everything it reads is under {@code /admin} and needs the token.
 */
@Controller
class ConsolePageController {

    @GetMapping({"/console", "/console/"})
    String console() {
        return "redirect:/console/index.html";
    }
}
