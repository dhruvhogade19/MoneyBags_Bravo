package com.moneybags.identity.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class StaleLogoutErrorController {
    private final Set<String> allowedPostLogoutUris;

    StaleLogoutErrorController(
            @Value("${moneybags.identity.clients.consumer.redirect-uri}") String consumerRedirect,
            @Value("${moneybags.identity.clients.admin.redirect-uri}") String adminRedirect) {
        this.allowedPostLogoutUris = Set.copyOf(new HashSet<>(
                List.of(rootOf(consumerRedirect), rootOf(adminRedirect))));
    }

    @RequestMapping(path = "/error", params = "post_logout_redirect_uri")
    ResponseEntity<Void> recoverStaleOidcLogout(
            HttpServletRequest request,
            @RequestParam("post_logout_redirect_uri") String postLogoutRedirectUri) {
        Object failedPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if ("/connect/logout".equals(failedPath) && allowedPostLogoutUris.contains(postLogoutRedirectUri)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, postLogoutRedirectUri)
                    .build();
        }
        return ResponseEntity.badRequest().build();
    }

    private static String rootOf(String redirectUri) {
        return URI.create(redirectUri).resolve("/").toString();
    }
}
