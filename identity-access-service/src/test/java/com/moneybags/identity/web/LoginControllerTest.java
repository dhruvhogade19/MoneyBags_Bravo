package com.moneybags.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class LoginControllerTest {

    @Test
    void exposesFrontendUrlAndRendersLoginView() {
        LoginController controller = new LoginController("http://localhost:8000");
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.login(model)).isEqualTo("login");
        assertThat(model.getAttribute("frontendUrl")).isEqualTo("http://localhost:8000");
    }
}
