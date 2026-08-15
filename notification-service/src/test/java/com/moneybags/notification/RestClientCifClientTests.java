package com.moneybags.notification;

import com.moneybags.notification.notification.integration.CustomerProfile;
import com.moneybags.notification.notification.integration.RestClientCifClient;
import com.moneybags.notification.common.config.RestClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientCifClientTests {

    @Test
    void keepsEurekaTransportOnTheNonLoadBalancedPrimaryBuilder()
            throws NoSuchMethodException {
        var method = RestClientConfig.class.getDeclaredMethod("restClientBuilder");

        assertThat(method.isAnnotationPresent(Primary.class)).isTrue();
    }

    @Test
    void loadsCustomerContactDetailsFromCifService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientCifClient client = new RestClientCifClient(
                builder.baseUrl("http://CIF-SERVICE").build()
        );

        server.expect(requestTo(
                        "http://CIF-SERVICE/api/v1/cifs/101/customer-contact-details"
                ))
                .andRespond(withSuccess("""
                        {
                          "cifId": 101,
                          "firstName": "Asha",
                          "lastName": "Rao",
                          "email": "asha@example.test",
                          "number": "9999999999",
                          "address": "Mumbai"
                        }
                        """, MediaType.APPLICATION_JSON));

        CustomerProfile customer = client.getCustomer(101L);

        assertThat(customer).isEqualTo(new CustomerProfile(
                101L,
                "Asha",
                "Rao",
                "asha@example.test"
        ));
        server.verify();
    }
}
