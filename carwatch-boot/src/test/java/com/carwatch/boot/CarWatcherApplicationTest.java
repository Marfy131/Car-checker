package com.carwatch.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.application.schedule.ScheduleDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CarWatcherApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void schedulerBeanIsDisabledInSmokeProfile() {
        assertThat(applicationContext.getBeansOfType(ScheduleDispatcher.class)).isEmpty();
    }
}
