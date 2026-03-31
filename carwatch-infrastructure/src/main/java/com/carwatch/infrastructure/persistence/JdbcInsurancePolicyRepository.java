package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.insurance.InsurancePolicy;
import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcInsurancePolicyRepository extends CrudRepository<InsurancePolicy, Long> {

    List<InsurancePolicy> findByCarId(Long carId);

    @Override
    List<InsurancePolicy> findAll();
}
