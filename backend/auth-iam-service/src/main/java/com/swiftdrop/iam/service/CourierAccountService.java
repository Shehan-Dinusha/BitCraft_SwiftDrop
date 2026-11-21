package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.dto.CreateCourierRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Courier accounts as managed by dispatchers. A dispatcher registers, lists, re-invites, suspends and
 * reactivates the couriers of their own district only; the district always comes from the
 * dispatcher's own account, never from the request. A SYSTEM_ADMIN can manage couriers in any district.
 *
 * <p>A courier of another district answers 403, as cross-district access does everywhere else.
 */
@Service
public class CourierAccountService {

    private final UserAccountService userAccountService;

    public CourierAccountService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Transactional(readOnly = true)
    public PagedResult<UserProfileResponse> list(UUID callerId, SriLankanDistrict requestedDistrict,
                                                 UserStatus status, String search, int page, int size) {
        SriLankanDistrict district = scopeDistrict(callerId, requestedDistrict);
        return userAccountService.list(UserRole.COURIER, status, district, search, page, size);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse get(UUID callerId, UUID courierId) {
        return AuthMapper.toProfile(requireCourierInScope(callerId, courierId));
    }

    @Transactional
    public UserProfileResponse register(UUID callerId, CreateCourierRequest request) {
        SriLankanDistrict district = scopeDistrict(callerId, request.district());
        if (district == null) {
            throw new IllegalArgumentException("District is required");
        }
        UserAccount courier = userAccountService.createInvited(request.username(), request.email(),
                request.phone(), request.firstName(), request.lastName(), UserRole.COURIER, district);
        return AuthMapper.toProfile(courier);
    }

    @Transactional
    public UserProfileResponse resendInvite(UUID callerId, UUID courierId) {
        requireCourierInScope(callerId, courierId);
        return userAccountService.resendInvite(courierId);
    }

    @Transactional
    public UserProfileResponse updateStatus(UUID callerId, UUID courierId, UserStatus status) {
        requireCourierInScope(callerId, courierId);
        return userAccountService.updateStatus(courierId, status);
    }

    /**
     * The district the caller may act in: a dispatcher's own district (whatever was requested), or
     * for a SYSTEM_ADMIN the requested one (null meaning every district).
     */
    private SriLankanDistrict scopeDistrict(UUID callerId, SriLankanDistrict requested) {
        UserAccount caller = userAccountService.requireUser(callerId);
        return switch (caller.getRole()) {
            case SYSTEM_ADMIN -> requested;
            case DISPATCHER -> {
                if (caller.getDistrict() == null) {
                    throw new AuthException(HttpStatus.FORBIDDEN, "Your dispatcher account has no district");
                }
                yield caller.getDistrict();
            }
            default -> throw new AuthException(HttpStatus.FORBIDDEN, "Only dispatchers can manage couriers");
        };
    }

    private UserAccount requireCourierInScope(UUID callerId, UUID courierId) {
        SriLankanDistrict scope = scopeDistrict(callerId, null);
        UserAccount courier = userAccountService.requireUser(courierId);
        if (courier.getRole() != UserRole.COURIER) {
            throw new ResourceNotFoundException("Courier not found");
        }
        if (scope != null && scope != courier.getDistrict()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "This courier belongs to another district");
        }
        return courier;
    }
}
