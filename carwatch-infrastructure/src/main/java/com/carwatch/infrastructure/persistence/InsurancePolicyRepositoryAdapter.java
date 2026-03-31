package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.insurance.InsurancePolicy;
import com.carwatch.domain.insurance.InsurancePolicyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InsurancePolicyRepositoryAdapter implements InsurancePolicyRepository {

    private final JdbcInsurancePolicyRepository jdbc;

    public InsurancePolicyRepositoryAdapter(JdbcInsurancePolicyRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InsurancePolicy save(InsurancePolicy policy) {
        return jdbc.save(policy);
    }

    @Override
    public Optional<InsurancePolicy> findById(Long id) {
        return jdbc.findById(id);
    }

    @Override
    public List<InsurancePolicy> findByCarId(Long carId) {
        return jdbc.findByCarId(carId);
    }

    @Override
    public List<InsurancePolicy> findAll() {
        return jdbc.findAll();
    }
}
