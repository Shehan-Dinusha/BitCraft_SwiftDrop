package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.entity.UserAvatar;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.domain.repository.UserAvatarRepository;
import com.swiftdrop.iam.service.avatar.AvatarStore;
import com.swiftdrop.iam.service.avatar.DatabaseAvatarStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
        service = new AvatarService(userAccountService, userAccountRepository, avatarRepository,
                new DatabaseAvatarStore(avatarRepository));
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

    /** Stands in for Azure Blob: pictures live outside the database. */
    private static final class FakeBlobStore implements AvatarStore {
        final Map<UUID, Image> blobs = new HashMap<>();

        @Override public void put(UserAccount u, String type, byte[] image) { blobs.put(u.getPublicId(), new Image(type, image)); }
        @Override public Optional<Image> get(UserAccount u) { return Optional.ofNullable(blobs.get(u.getPublicId())); }
        @Override public void delete(UserAccount u) { blobs.remove(u.getPublicId()); }
        @Override public boolean external() { return true; }
    }

    @Test
    void withBlobStorageAPictureGoesToTheStoreAndAnOldDatabaseCopyIsDropped() {
        FakeBlobStore blob = new FakeBlobStore();
        AvatarService blobService = new AvatarService(userAccountService, userAccountRepository, avatarRepository, blob);
        UserAvatar legacy = new UserAvatar(7L, "image/png", PNG, Instant.now());
        when(userAccountService.requireUser(user.getPublicId())).thenReturn(user);
        when(avatarRepository.findById(7L)).thenReturn(Optional.of(legacy));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));

        blobService.replace(user.getPublicId(), JPEG);

        assertThat(blob.blobs.get(user.getPublicId()).contentType()).isEqualTo("image/jpeg");
        verify(avatarRepository).delete(legacy);
        verify(avatarRepository, never()).save(any());
    }

    @Test
    void aPictureSavedBeforeBlobStorageMovesThereWhenFirstRead() {
        FakeBlobStore blob = new FakeBlobStore();
        AvatarService blobService = new AvatarService(userAccountService, userAccountRepository, avatarRepository, blob);
        UserAvatar legacy = new UserAvatar(7L, "image/png", PNG, Instant.now());
        when(userAccountService.requireUser(user.getPublicId())).thenReturn(user);
        when(avatarRepository.findById(7L)).thenReturn(Optional.of(legacy));

        var found = blobService.find(user.getPublicId());

        assertThat(found).isPresent();
        assertThat(found.get().contentType()).isEqualTo("image/png");
        assertThat(blob.blobs).containsKey(user.getPublicId());
        verify(avatarRepository).delete(legacy);
    }

    @Test
    void deletingAnAccountErasesThePictureEverywhere() {
        FakeBlobStore blob = new FakeBlobStore();
        AvatarService blobService = new AvatarService(userAccountService, userAccountRepository, avatarRepository, blob);
        blob.put(user, "image/png", PNG);
        user.setAvatarUpdatedAt(Instant.now());
        when(userAccountService.requireUser(user.getPublicId())).thenReturn(user);
        when(avatarRepository.findById(7L)).thenReturn(Optional.empty());

        blobService.eraseFor(user.getPublicId());

        assertThat(blob.blobs).isEmpty();
        assertThat(user.getAvatarUpdatedAt()).isNull();
        verify(userAccountRepository).save(user);
    }
}
