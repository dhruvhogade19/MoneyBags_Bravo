package com.moneybags.eod;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoEodAutoResumeTest {

    @Test
    void configuredRunIsNotResumedWhenWildcardLacksTheDemoProfile() {
        EodOrchestrationService service = mock(EodOrchestrationService.class);
        DemoStepPolicy policy = new DemoStepPolicy(true, "*",
                new MockEnvironment().withProperty("spring.profiles.active", "test"));

        new DemoEodAutoResume(service, policy, "existing-run").resumeConfiguredDemoRun();

        verifyNoInteractions(service);
    }

    @Test
    void configuredRunIsNotResumedWhenDemoProfileLacksTheWildcard() {
        EodOrchestrationService service = mock(EodOrchestrationService.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        DemoStepPolicy policy = new DemoStepPolicy(true, "STATEMENTS_GENERATE", environment);

        new DemoEodAutoResume(service, policy, "existing-run").resumeConfiguredDemoRun();

        verifyNoInteractions(service);
    }
}
