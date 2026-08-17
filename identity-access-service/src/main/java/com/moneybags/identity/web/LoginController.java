package com.moneybags.identity.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private final String frontendUrl;

    public LoginController(
            @Value("${moneybags.identity.frontend-url:http://localhost:8000}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("frontendUrl", frontendUrl);
        return "login";
    }
}
