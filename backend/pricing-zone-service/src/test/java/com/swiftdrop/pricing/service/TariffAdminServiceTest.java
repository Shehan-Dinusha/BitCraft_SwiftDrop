package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import com.swiftdrop.pricing.domain.repository.TariffRuleRepository;
import com.swiftdrop.pricing.dto.TariffRuleRequest;
import com.swiftdrop.pricing.dto.TariffRuleResponse;
import com.swiftdrop.pricing.exception.TariffConflictException;
import com.swiftdrop.pricing.exception.TariffNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TariffAdminServiceTest {

    @Mock
    private TariffRuleRepository tariffRuleRepository;

    @InjectMocks
    private TariffAdminService service;

    private TariffRuleRequest request(String versionId) {
        return new TariffRuleRequest(versionId, new BigDecimal("600.00"), new BigDecimal("1.00"),
                new BigDecimal("130.00"), new BigDecimal("5000.00"));
    }

    private TariffRule activeRule(String versionId) {
        return TariffRule.builder()
                .ruleVersionId(versionId)
                .fromDistrict("ALL")
                .toDistrict("ALL")
                .basePrice(new BigDecimal("550.00"))
                .build();
    }

    @Test
    void publishNewVersion_deactivatesPreviousAndActivatesNew() {
        TariffRule old = activeRule("PRICING-2026-V1");
        when(tariffRuleRepository.existsByRuleVersionId("PRICING-2026-V2")).thenReturn(false);
        when(tariffRuleRepository.findActiveRules("ALL", "ALL")).thenReturn(List.of(old));
        when(tariffRuleRepository.save(any(TariffRule.class))).thenAnswer(inv -> inv.getArgument(0));

        TariffRuleResponse response = service.publishNewVersion(request("PRICING-2026-V2"));

        assertFalse(old.getIsActive());
        assertTrue(response.active());
        assertEquals("PRICING-2026-V2", response.ruleVersionId());
        assertEquals(new BigDecimal("600.00"), response.basePrice());
        verify(tariffRuleRepository).saveAll(List.of(old));
    }

    @Test
    void publishNewVersion_rejectsExistingVersionId() {
        when(tariffRuleRepository.existsByRuleVersionId("PRICING-2026-V1")).thenReturn(true);

        assertThrows(TariffConflictException.class, () -> service.publishNewVersion(request("PRICING-2026-V1")));
        verify(tariffRuleRepository, never()).save(any());
    }

    @Test
    void publishNewVersion_generatesVersionIdWhenBlank() {
        when(tariffRuleRepository.countByFromDistrictAndToDistrict("ALL", "ALL")).thenReturn(1L);
        when(tariffRuleRepository.existsByRuleVersionId(anyString())).thenReturn(false);
        when(tariffRuleRepository.findActiveRules("ALL", "ALL")).thenReturn(List.of());
        when(tariffRuleRepository.save(any(TariffRule.class))).thenAnswer(inv -> inv.getArgument(0));

        TariffRuleResponse response = service.publishNewVersion(request(null));

        assertEquals("PRICING-" + Year.now().getValue() + "-V2", response.ruleVersionId());
    }

    @Test
    void getActiveRule_failsWhenNoneActive() {
        when(tariffRuleRepository.findActiveRules("ALL", "ALL")).thenReturn(List.of());

        assertThrows(TariffNotFoundException.class, () -> service.getActiveRule());
    }
}
