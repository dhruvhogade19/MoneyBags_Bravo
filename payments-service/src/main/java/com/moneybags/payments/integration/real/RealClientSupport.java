package com.moneybags.payments.integration.real;

import com.moneybags.payments.exception.PeerServiceException;
import org.springframework.web.client.RestClient;

final class RealClientSupport {
  private RealClientSupport() { }

  static RestClient.ResponseSpec errors(RestClient.ResponseSpec spec, String service) {
    return spec.onStatus(status -> status.isError(), (request, response) -> {
      throw new PeerServiceException(service, response.getStatusCode().value(),
          "PEER_HTTP_" + response.getStatusCode().value(),
          service + " returned " + response.getStatusCode().value());
    });
  }
}
