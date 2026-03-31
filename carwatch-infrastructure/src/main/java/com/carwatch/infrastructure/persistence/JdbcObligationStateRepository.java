package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcObligationStateRepository extends CrudRepository<ObligationState, Long> {

    Optional<ObligationState> findByCarIdAndObligationType(Long carId, ObligationType obligationType);

    List<ObligationState> findByCarId(Long carId);

    @Override
    List<ObligationState> findAll();
}
