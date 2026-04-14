package com.carwatch.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.application.schedule.ScheduleClaimService;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ScheduleClaimServiceContentionIT {

    @Autowired
    private CheckScheduleRepository checkScheduleRepository;

    @Autowired
    private ScheduleClaimService scheduleClaimService;

    @Test
    void concurrentClaimsOnSameScheduleHaveExactlyOneWinner() throws Exception {
        CheckSchedule saved = checkScheduleRepository.save(newDueWorkflowSchedule());

        CheckSchedule firstView = checkScheduleRepository.findById(saved.getId()).orElseThrow();
        CheckSchedule secondView = checkScheduleRepository.findById(saved.getId()).orElseThrow();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> firstClaim = executor.submit(() -> claimWhenReleased(firstView, ready, start));
            Future<Boolean> secondClaim = executor.submit(() -> claimWhenReleased(secondView, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(Stream.of(firstClaim.get(10, TimeUnit.SECONDS), secondClaim.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        CheckSchedule reloaded = checkScheduleRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLockOwner()).isNotBlank();
        assertThat(reloaded.getLockUntil()).isNotNull();
        assertThat(Stream.of(firstView, secondView)
            .map(CheckSchedule::getLockOwner)
            .filter(Objects::nonNull))
            .containsExactly(reloaded.getLockOwner());
    }

    private Boolean claimWhenReleased(CheckSchedule schedule, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return scheduleClaimService.claim(schedule);
    }

    private CheckSchedule newDueWorkflowSchedule() {
        LocalDateTime now = LocalDateTime.now();

        CheckSchedule schedule = new CheckSchedule();
        schedule.setCheckType(CheckType.DAILY_REMINDER_SCAN);
        schedule.setCronExpression("0 0 6 * * *");
        schedule.setZoneId("Europe/Bratislava");
        schedule.setEnabled(true);
        schedule.setWarningDaysBefore(0);
        schedule.setNextRunAt(now.minusMinutes(1));
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        return schedule;
    }
}
