package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.CreateCourierRequest;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.UpdateCourierRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import com.swiftdrop.iam.exception.UserAlreadyExistsException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class CourierAccountService {
    private final UserAccountRepository repository;
    private final UserAccountService users;
    private final RefreshTokenService refreshTokens;
    private final TokenIssuanceService tokens;

    public CourierAccountService(UserAccountRepository repository, UserAccountService users,
                                 RefreshTokenService refreshTokens, TokenIssuanceService tokens) {
        this.repository = repository;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public PagedResult<UserProfileResponse> list(UUID actorId, UserStatus status, String search, int page, int size) {
        CallerContext caller = requireOperator(actorId);
        Specification<UserAccount> filter = (root, query, cb) -> cb.equal(root.get("role"), UserRole.COURIER);
        if (caller.role() == UserRole.DISPATCHER) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("district"), caller.districtId()));
        }
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like)));
        }
        var result = repository.findAll(filter, PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PagedResult.of(result.getContent().stream().map(AuthMapper::toProfile).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse get(UUID actorId, UUID courierId) {
        return AuthMapper.toProfile(requireCourier(requireOperator(actorId), courierId));
    }

    @Transactional
    public UserProfileResponse create(UUID actorId, CreateCourierRequest request) {
        repository.lockAccountAdministration();
        CallerContext caller = requireOperator(actorId);
        caller.requireDistrict(request.district(), UserRole.SYSTEM_ADMIN);
        return users.create(new CreateUserRequest(request.username(), request.email(), request.phone(),
                request.firstName(), request.lastName(), request.password(), UserRole.COURIER, request.district(), null));
    }

    @Transactional
    public UserProfileResponse update(UUID actorId, UUID courierId, UpdateCourierRequest request) {
        repository.lockAccountAdministration();
        UserAccount courier = requireCourier(requireOperator(actorId), courierId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (repository.existsByEmailIgnoreCaseAndPublicIdNot(email, courierId)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }
        if (phone != null && repository.existsByPhoneAndPublicIdNot(phone, courierId)) {
            throw new UserAlreadyExistsException("Phone number is already registered");
        }
        boolean emailChanged = !Objects.equals(email, courier.getEmail());
        courier.setEmail(email);
        courier.setPhone(phone);
        courier.setFirstName(request.firstName().trim());
        courier.setLastName(request.lastName().trim());
        repository.save(courier);
        if (emailChanged) {
            refreshTokens.revokeAll(courier);
            tokens.blacklistUser(courierId);
        }
        return AuthMapper.toProfile(courier);
    }

    @Transactional
    public UserProfileResponse updateStatus(UUID actorId, UUID courierId, UserStatus status) {
        repository.lockAccountAdministration();
        requireCourier(requireOperator(actorId), courierId);
        return users.updateStatus(courierId, status);
    }

    private CallerContext requireOperator(UUID actorId) {
        // The JWT authenticates the ID. Current IAM data supplies authorization, never client headers.
        UserAccount actor = users.requireUser(actorId);
        if (actor.getStatus() != UserStatus.ACTIVE) throw new ForbiddenException("Account is not active");
        CallerContext caller = new CallerContext(actor.getPublicId(), actor.getRole(), actor.getMerchantId(), actor.getDistrict());
        caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
        if (caller.role() == UserRole.DISPATCHER) caller.requireDistrict(caller.districtId());
        return caller;
    }

    private UserAccount requireCourier(CallerContext caller, UUID courierId) {
        UserAccount courier = users.requireUser(courierId);
        if (courier.getRole() != UserRole.COURIER) throw new ResourceNotFoundException("Courier not found");
        caller.requireDistrict(courier.getDistrict(), UserRole.SYSTEM_ADMIN);
        return courier;
    }
}
