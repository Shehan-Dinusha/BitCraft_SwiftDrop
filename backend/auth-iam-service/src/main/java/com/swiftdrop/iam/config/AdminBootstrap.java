package com.swiftdrop.iam.config;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.service.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Value("${swiftdrop.bootstrap.admin.username:}")
    private String username;

    @Value("${swiftdrop.bootstrap.admin.password:}")
    private String password;

    @Value("${swiftdrop.bootstrap.admin.email:}")
    private String email;

    @Value("${swiftdrop.bootstrap.admin.district:COLOMBO}")
    private String district;

    public AdminBootstrap(UserAccountRepository userAccountRepository,
                          PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("Bootstrap admin is disabled (BOOTSTRAP_ADMIN_USERNAME/PASSWORD not set)");
            return;
        }
        if (userAccountRepository.countByRoleAndStatus(UserRole.SYSTEM_ADMIN, UserStatus.ACTIVE) > 0) {
            log.info("Bootstrap admin skipped because an active SYSTEM_ADMIN already exists");
            return;
        }
        if (userAccountRepository.existsByUsernameIgnoreCase(username.trim())) {
            log.warn("Bootstrap admin skipped because username '{}' already exists", username.trim());
            return;
        }

        try {
            passwordPolicy.validate(password);
        } catch (IllegalArgumentException exception) {
            log.error("Bootstrap admin password does not satisfy policy: {}", exception.getMessage());
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setUsername(username.trim());
        admin.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT));
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.SYSTEM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setDistrict(parseDistrict(district));
        userAccountRepository.save(admin);

        log.info("Bootstrap SYSTEM_ADMIN '{}' created", admin.getUsername());
    }

    private SriLankanDistrict parseDistrict(String value) {
        if (value == null || value.isBlank()) {
            return SriLankanDistrict.COLOMBO;
        }
        try {
            return SriLankanDistrict.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Unknown bootstrap admin district '{}'; defaulting to COLOMBO", value);
            return SriLankanDistrict.COLOMBO;
        }
    }
}
