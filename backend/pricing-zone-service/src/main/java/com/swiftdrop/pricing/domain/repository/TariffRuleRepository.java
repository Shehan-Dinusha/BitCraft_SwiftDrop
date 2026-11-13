package com.swiftdrop.pricing.domain.repository;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TariffRuleRepository extends JpaRepository<TariffRule, Long> {

    @Query("select t from TariffRule t where t.fromDistrict = :from and t.toDistrict = :to "
            + "and t.isActive = true order by t.id desc")
    List<TariffRule> findActiveRules(@Param("from") String from, @Param("to") String to);
}
