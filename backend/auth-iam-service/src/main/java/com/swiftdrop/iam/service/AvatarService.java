package com.swiftdrop.iam.service;

import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.entity.UserAvatar;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.domain.repository.UserAvatarRepository;
import com.swiftdrop.iam.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Profile pictures. Only PNG, JPEG and WebP up to {@link #MAX_BYTES} are accepted, recognised by
 * their content (magic bytes), never by the file name or the type the browser claims.
 */
@Service
public class AvatarService {

    public static final int MAX_BYTES = 512 * 1024;

    public record StoredAvatar(String contentType, byte[] image, Instant updatedAt) {
    }

    private final UserAccountService userAccountService;
    private final UserAccountRepository userAccountRepository;
    private final UserAvatarRepository avatarRepository;

    public AvatarService(UserAccountService userAccountService, UserAccountRepository userAccountRepository,
                         UserAvatarRepository avatarRepository) {
        this.userAccountService = userAccountService;
        this.userAccountRepository = userAccountRepository;
        this.avatarRepository = avatarRepository;
    }

    @Transactional
    public UserProfileResponse replace(UUID publicId, byte[] image) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Choose an image to upload");
        }
        if (image.length > MAX_BYTES) {
            throw new IllegalArgumentException("The picture must be 512 KB or smaller");
        }
        String contentType = detectType(image)
                .orElseThrow(() -> new IllegalArgumentException("Use a PNG, JPEG or WebP image"));

        UserAccount user = userAccountService.requireUser(publicId);
        Instant now = Instant.now();
        Optional<UserAvatar> existing = avatarRepository.findById(user.getId());
        if (existing.isPresent()) {
            existing.get().replace(contentType, image, now);
            avatarRepository.save(existing.get());
        } else {
            avatarRepository.save(new UserAvatar(user.getId(), contentType, image, now));
        }
        user.setAvatarUpdatedAt(now);
        return AuthMapper.toProfile(userAccountRepository.save(user));
    }

    @Transactional
    public UserProfileResponse remove(UUID publicId) {
        UserAccount user = userAccountService.requireUser(publicId);
        avatarRepository.findById(user.getId()).ifPresent(avatarRepository::delete);
        user.setAvatarUpdatedAt(null);
        return AuthMapper.toProfile(userAccountRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Optional<StoredAvatar> find(UUID publicId) {
        UserAccount user = userAccountService.requireUser(publicId);
        return avatarRepository.findById(user.getId())
                .map(a -> new StoredAvatar(a.getContentType(), a.getImage(), a.getUpdatedAt()));
    }

    /** The image type from the file's first bytes. */
    static Optional<String> detectType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of("image/png");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }
}
