package com.swiftdrop.pricing.domain.repository;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffRuleRepository extends JpaRepository<TariffRule, Long> {
}
