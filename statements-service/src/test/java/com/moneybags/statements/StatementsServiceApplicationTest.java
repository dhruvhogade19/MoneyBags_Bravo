package com.moneybags.statements;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StatementsServiceApplicationTest {
    @Test
    void contextLoadsWithLiquibaseSchema() {
    }
}
