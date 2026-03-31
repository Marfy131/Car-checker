package com.carwatch.domain.insurance;

import java.util.List;
import java.util.Optional;

public interface InsurancePolicyRepository {

    InsurancePolicy save(InsurancePolicy policy);

    Optional<InsurancePolicy> findById(Long id);

    List<InsurancePolicy> findByCarId(Long carId);

    List<InsurancePolicy> findAll();
}
