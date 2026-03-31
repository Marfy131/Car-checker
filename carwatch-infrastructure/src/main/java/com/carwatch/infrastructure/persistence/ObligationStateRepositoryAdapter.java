package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ObligationStateRepositoryAdapter implements ObligationStateRepository {

    private final JdbcObligationStateRepository jdbc;

    public ObligationStateRepositoryAdapter(JdbcObligationStateRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ObligationState save(ObligationState state) {
        return jdbc.save(state);
    }

    @Override
    public Optional<ObligationState> findByCarIdAndObligationType(Long carId, ObligationType type) {
        return jdbc.findByCarIdAndObligationType(carId, type);
    }

    @Override
    public List<ObligationState> findByCarId(Long carId) {
        return jdbc.findByCarId(carId);
    }

    @Override
    public List<ObligationState> findAll() {
        return jdbc.findAll();
    }
}
