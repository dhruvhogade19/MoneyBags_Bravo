package com.moneybags.notification.notification.integration;

import com.moneybags.notification.common.exception.CifUnavailableException;
import com.moneybags.notification.common.exception.CustomerNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!mock-cif")
public class RestClientCifClient implements CifClient {

    private final RestClient cifRestClient;

    public RestClientCifClient(RestClient cifRestClient) {
        this.cifRestClient = cifRestClient;
    }

    @Override
    public CustomerProfile getCustomer(Long cifId) {
        try {
            CustomerProfile customer = cifRestClient.get()
                    .uri("/api/v1/cifs/{cifId}/customer-contact-details", cifId)
                    .retrieve().body(CustomerProfile.class);
            if (customer == null) {
                throw new CustomerNotFoundException(cifId);
            }
            return customer;
        } catch (CustomerNotFoundException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                throw new CustomerNotFoundException(cifId);
            }
            throw new CifUnavailableException(cifId, exception);
        } catch (RestClientException exception) {
            throw new CifUnavailableException(cifId, exception);
        }
    }
}
