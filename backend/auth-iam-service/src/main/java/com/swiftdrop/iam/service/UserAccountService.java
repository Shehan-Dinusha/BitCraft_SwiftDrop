package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.exception.ResourceNotFoundException;
import com.swiftdrop.iam.exception.UserAlreadyExistsException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserAccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenIssuanceService tokenIssuanceService;
    private final RefreshTokenService refreshTokenService;

    public UserAccountService(UserAccountRepository userAccountRepository,
                              PasswordEncoder passwordEncoder,
                              PasswordPolicy passwordPolicy,
                              TokenIssuanceService tokenIssuanceService,
                              RefreshTokenService refreshTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenIssuanceService = tokenIssuanceService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserProfileResponse register(RegisterUserRequest request) {
        if (request.role() != UserRole.CUSTOMER && request.role() != UserRole.MERCHANT) {
            throw new IllegalArgumentException("Self-service registration supports only CUSTOMER and MERCHANT roles");
        }
        return createUser(request.username(), request.email(), request.phone(), request.firstName(),
                request.lastName(), request.password(), request.role(), request.district(), request.merchantId());
    }

    @Transactional
    public UserProfileResponse create(CreateUserRequest request) {
        return createUser(request.username(), request.email(), request.phone(), request.firstName(),
                request.lastName(), request.password(), request.role(), request.district(), request.merchantId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID publicId) {
        return AuthMapper.toProfile(requireUser(publicId));
    }

    @Transactional(readOnly = true)
    public PagedResult<UserProfileResponse> list(UserRole role, UserStatus status, String search,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<UserAccount> result = userAccountRepository.findAll(buildFilter(role, status, search), pageable);
        List<UserProfileResponse> content = result.getContent().stream()
                .map(AuthMapper::toProfile)
                .toList();
        return PagedResult.of(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public UserProfileResponse updateRole(UUID publicId, UserRole role) {
        UserAccount user = requireUser(publicId);
        if (user.getRole() == role) {
            return AuthMapper.toProfile(user);
        }
        if (user.getRole() == UserRole.SYSTEM_ADMIN
                && activeSystemAdmins() <= 1
                && role != UserRole.SYSTEM_ADMIN) {
            throw new AuthException(HttpStatus.CONFLICT, "Cannot remove the last active SYSTEM_ADMIN");
        }

        user.setRole(role);
        userAccountRepository.save(user);
        refreshTokenService.revokeAll(user);
        tokenIssuanceService.blacklistUser(user.getPublicId());
        return AuthMapper.toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateStatus(UUID publicId, UserStatus status) {
        UserAccount user = requireUser(publicId);
        if (user.getStatus() == status) {
            return AuthMapper.toProfile(user);
        }
        if (status != UserStatus.ACTIVE
                && user.getRole() == UserRole.SYSTEM_ADMIN
                && activeSystemAdmins() <= 1) {
            throw new AuthException(HttpStatus.CONFLICT, "Cannot deactivate the last active SYSTEM_ADMIN");
        }

        user.setStatus(status);
        userAccountRepository.save(user);
        if (status != UserStatus.ACTIVE) {
            refreshTokenService.revokeAll(user);
            tokenIssuanceService.blacklistUser(user.getPublicId());
        }
        return AuthMapper.toProfile(user);
    }

    @Transactional
    public void changePassword(UUID publicId, ChangePasswordRequest request) {
        UserAccount user = requireUser(publicId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password");
        }

        passwordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(user);
        refreshTokenService.revokeAll(user);
        tokenIssuanceService.blacklistUser(user.getPublicId());
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(UUID publicId) {
        return userAccountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserProfileResponse createUser(String username, String email, String phone,
                                           String firstName, String lastName, String rawPassword,
                                           UserRole role, com.swiftdrop.contracts.enums.SriLankanDistrict district,
                                           UUID merchantId) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = normalizeNullable(phone);

        if (userAccountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserAlreadyExistsException("Username is already registered");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }
        if (normalizedPhone != null && userAccountRepository.existsByPhone(normalizedPhone)) {
            throw new UserAlreadyExistsException("Phone number is already registered");
        }

        passwordPolicy.validate(rawPassword);

        UserAccount user = new UserAccount();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setDistrict(district);
        user.setMerchantId(merchantId);
        user.setStatus(UserStatus.ACTIVE);

        return AuthMapper.toProfile(userAccountRepository.save(user));
    }

    private Specification<UserAccount> buildFilter(UserRole role, UserStatus status, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), like)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private long activeSystemAdmins() {
        return userAccountRepository.countByRoleAndStatus(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
