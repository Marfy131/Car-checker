package com.carwatch.domain.obligation;

import java.util.List;
import java.util.Optional;

public interface ObligationStateRepository {

    ObligationState save(ObligationState state);

    Optional<ObligationState> findByCarIdAndObligationType(Long carId, ObligationType type);

    List<ObligationState> findByCarId(Long carId);

    List<ObligationState> findAll();
}
