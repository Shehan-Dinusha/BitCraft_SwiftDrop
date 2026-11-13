package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import com.swiftdrop.pricing.domain.repository.TariffRuleRepository;
import com.swiftdrop.pricing.dto.TariffRuleRequest;
import com.swiftdrop.pricing.dto.TariffRuleResponse;
import com.swiftdrop.pricing.exception.TariffConflictException;
import com.swiftdrop.pricing.exception.TariffNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffAdminService {

    private static final String DEFAULT_ROUTE = "ALL";

    private final TariffRuleRepository tariffRuleRepository;

    @Transactional(readOnly = true)
    public List<TariffRuleResponse> listRules() {
        return tariffRuleRepository.findAllByOrderByIdDesc().stream()
                .map(TariffRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TariffRuleResponse getActiveRule() {
        return tariffRuleRepository.findActiveRules(DEFAULT_ROUTE, DEFAULT_ROUTE).stream()
                .findFirst()
                .map(TariffRuleResponse::from)
                .orElseThrow(() -> new TariffNotFoundException("No active default tariff rule is configured"));
    }

    /**
     * Publishes a new version of the default tariff. Existing versions are never edited,
     * so orders keep the price rule version they were quoted with.
     */
    @Transactional
    public TariffRuleResponse publishNewVersion(TariffRuleRequest request) {
        String versionId = resolveVersionId(request.ruleVersionId());
        if (tariffRuleRepository.existsByRuleVersionId(versionId)) {
            throw new TariffConflictException("Tariff rule version already exists: " + versionId);
        }

        List<TariffRule> activeRules = tariffRuleRepository.findActiveRules(DEFAULT_ROUTE, DEFAULT_ROUTE);
        Instant now = Instant.now();
        activeRules.forEach(rule -> {
            rule.setIsActive(false);
            rule.setUpdatedAt(now);
        });
        tariffRuleRepository.saveAll(activeRules);

        TariffRule newRule = TariffRule.builder()
                .ruleVersionId(versionId)
                .fromDistrict(DEFAULT_ROUTE)
                .toDistrict(DEFAULT_ROUTE)
                .basePrice(request.basePrice())
                .weightStepKg(request.weightStepKg())
                .pricePerStep(request.pricePerStep())
                .volumetricDivisor(request.volumetricDivisor())
                .isActive(true)
                .build();
        return TariffRuleResponse.from(tariffRuleRepository.save(newRule));
    }

    private String resolveVersionId(String requestedId) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId.trim();
        }
        long existing = tariffRuleRepository.countByFromDistrictAndToDistrict(DEFAULT_ROUTE, DEFAULT_ROUTE);
        return "PRICING-" + Year.now().getValue() + "-V" + (existing + 1);
    }
}
