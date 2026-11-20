package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.entity.UserAvatar;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.domain.repository.UserAvatarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

    @Mock
    private UserAccountService userAccountService;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private UserAvatarRepository avatarRepository;

    private AvatarService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new AvatarService(userAccountService, userAccountRepository, avatarRepository);
        user = new UserAccount();
        user.setId(7L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("nimal");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
    }

    @Test
    void recognisesImagesByContentOnly() {
        assertThat(AvatarService.detectType(PNG)).contains("image/png");
        assertThat(AvatarService.detectType(JPEG)).contains("image/jpeg");
        assertThat(AvatarService.detectType(WEBP)).contains("image/webp");
        assertThat(AvatarService.detectType("<svg onload=alert(1)>".getBytes())).isEmpty();
        assertThat(AvatarService.detectType("GIF89a".getBytes())).isEmpty();
    }

    @Test
    void storesAPictureAndStampsTheProfile() {
        when(userAccountService.requireUser(user.getPublicId())).thenReturn(user);
        when(avatarRepository.findById(7L)).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        var profile = service.replace(user.getPublicId(), JPEG);

        ArgumentCaptor<UserAvatar> saved = ArgumentCaptor.forClass(UserAvatar.class);
        verify(avatarRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(profile.avatarUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsNonImagesAndOversizedFiles() {
        assertThatThrownBy(() -> service.replace(user.getPublicId(), "<svg/>".getBytes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PNG, JPEG or WebP");
        byte[] big = new byte[AvatarService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> service.replace(user.getPublicId(), big))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("512 KB");
        assertThatThrownBy(() -> service.replace(user.getPublicId(), new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        verify(avatarRepository, never()).save(any());
    }

    @Test
    void removingClearsThePictureAndTheStamp() {
        user.setAvatarUpdatedAt(Instant.now());
        UserAvatar avatar = new UserAvatar(7L, "image/png", PNG, Instant.now());
        when(userAccountService.requireUser(user.getPublicId())).thenReturn(user);
        when(avatarRepository.findById(7L)).thenReturn(Optional.of(avatar));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        var profile = service.remove(user.getPublicId());

        verify(avatarRepository).delete(avatar);
        assertThat(profile.avatarUpdatedAt()).isNull();
    }
}
