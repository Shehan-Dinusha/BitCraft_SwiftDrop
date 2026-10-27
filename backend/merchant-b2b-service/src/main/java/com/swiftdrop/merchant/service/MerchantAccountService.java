package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import com.swiftdrop.merchant.domain.repository.MerchantAccountRepository;
import com.swiftdrop.merchant.dto.CreateMerchantRequest;
import com.swiftdrop.merchant.dto.MerchantAccountResponse;
import com.swiftdrop.merchant.exception.MerchantNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAccountService {

    private final MerchantAccountRepository merchantAccountRepository;

    public static final UUID DEFAULT_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Transactional
    public MerchantAccountResponse createMerchant(CreateMerchantRequest request) {
        log.info("Creating new merchant account: {}", request.businessName());
        MerchantAccount account = MerchantAccount.builder()
                .businessName(request.businessName())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .pickupAddress(request.pickupAddress())
                .operatingDistrict(request.operatingDistrict())
                .status("ACTIVE")
                .build();

        MerchantAccount saved = merchantAccountRepository.save(account);
        return mapToResponse(saved);
    }

    @Transactional
    public MerchantAccountResponse getOrCreateMerchant(UUID merchantId, String fallbackName) {
        return merchantAccountRepository.findByMerchantId(merchantId)
                .map(this::mapToResponse)
                .orElseGet(() -> {
                    log.info("Provisioning default merchant account for ID: {}", merchantId);
                    MerchantAccount account = MerchantAccount.builder()
                            .merchantId(merchantId)
                            .businessName(fallbackName != null ? fallbackName : "SwiftDrop Merchant Hub")
                            .contactEmail("merchant-" + merchantId.toString().substring(0, 8) + "@swiftdrop.lk")
                            .contactPhone("+94 11 234 5678")
                            .pickupAddress("No. 128 Galle Road, Colombo 03")
                            .operatingDistrict(SriLankanDistrict.COLOMBO)
                            .status("ACTIVE")
                            .build();
                    MerchantAccount saved = merchantAccountRepository.save(account);
                    return mapToResponse(saved);
                });
    }

    @Transactional(readOnly = true)
    public MerchantAccountResponse getMerchantById(UUID merchantId) {
        MerchantAccount account = merchantAccountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
        return mapToResponse(account);
    }

    @Transactional(readOnly = true)
    public List<MerchantAccountResponse> getAllMerchants() {
        return merchantAccountRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private MerchantAccountResponse mapToResponse(MerchantAccount account) {
        return new MerchantAccountResponse(
                account.getMerchantId(),
                account.getBusinessName(),
                account.getContactEmail(),
                account.getContactPhone(),
                account.getPickupAddress(),
                account.getOperatingDistrict(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
