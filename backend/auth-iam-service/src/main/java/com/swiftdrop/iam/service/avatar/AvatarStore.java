package com.swiftdrop.iam.service.avatar;

import com.swiftdrop.iam.domain.entity.UserAccount;

import java.util.Optional;

/**
 * Where profile pictures live. Production uses Azure Blob ({@link BlobAvatarStore}); without a storage
 * connection string (local runs, tests) they stay in the user_avatars table ({@link DatabaseAvatarStore}).
 */
public interface AvatarStore {

    record Image(String contentType, byte[] bytes) {
    }

    void put(UserAccount user, String contentType, byte[] image);

    Optional<Image> get(UserAccount user);

    void delete(UserAccount user);

    /** True when pictures live outside the database, so rows left in user_avatars are legacy copies. */
    boolean external();
}
