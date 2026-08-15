package com.moneybags.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@ActiveProfiles("test")
class NotificationSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseCreatesAndSeedsNotificationTemplates() {
        Integer templateCount = jdbcTemplate.queryForObject(
                "select count(*) from notification_template", Integer.class);

        assertThat(templateCount).isEqualTo(11);
    }
}
