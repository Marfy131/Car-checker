package com.carwatch.application.history;

import com.carwatch.domain.schedule.CheckRunLog;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistoryQueryService {

    private final CheckRunLogRepository checkRunLogRepository;
    private final int recentRunLimit;

    @Autowired
    public HistoryQueryService(
        CheckRunLogRepository checkRunLogRepository,
        @Value("${carwatch.history.page-size:100}") int recentRunLimit
    ) {
        this.checkRunLogRepository = checkRunLogRepository;
        this.recentRunLimit = recentRunLimit;
    }

    HistoryQueryService(CheckRunLogRepository checkRunLogRepository) {
        this(checkRunLogRepository, 100);
    }

    public List<CheckRunLog> findRecentRuns() {
        return checkRunLogRepository.findRecentRuns(recentRunLimit);
    }
}
