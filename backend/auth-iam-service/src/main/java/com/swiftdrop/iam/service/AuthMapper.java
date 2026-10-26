package com.swiftdrop.iam.service;

import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;

final class AuthMapper {

    private AuthMapper() {
    }

    static UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getDistrict(),
                user.getMerchantId(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }

    static TokenResponse toTokenResponse(UserAccount user, TokenIssuanceService.IssuedAccessToken accessToken,
                                         String refreshToken) {
        return new TokenResponse(
                accessToken.token(),
                refreshToken,
                "Bearer",
                accessToken.expiresInSeconds(),
                user.getPublicId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getDistrict()
        );
    }
}
