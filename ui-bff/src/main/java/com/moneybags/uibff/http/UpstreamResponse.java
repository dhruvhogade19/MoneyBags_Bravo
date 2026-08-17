package com.moneybags.uibff.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record UpstreamResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {
}
