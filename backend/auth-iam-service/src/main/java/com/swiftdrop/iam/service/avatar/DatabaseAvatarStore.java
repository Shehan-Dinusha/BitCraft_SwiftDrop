package com.swiftdrop.iam.service.avatar;

import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.entity.UserAvatar;
import com.swiftdrop.iam.domain.repository.UserAvatarRepository;

import java.time.Instant;
import java.util.Optional;

/** Profile pictures in the user_avatars table (V6), for runs without Azure Blob. */
public class DatabaseAvatarStore implements AvatarStore {

    private final UserAvatarRepository avatars;

    public DatabaseAvatarStore(UserAvatarRepository avatars) {
        this.avatars = avatars;
    }

    @Override
    public void put(UserAccount user, String contentType, byte[] image) {
        Instant now = Instant.now();
        Optional<UserAvatar> existing = avatars.findById(user.getId());
        if (existing.isPresent()) {
            existing.get().replace(contentType, image, now);
            avatars.save(existing.get());
        } else {
            avatars.save(new UserAvatar(user.getId(), contentType, image, now));
        }
    }

    @Override
    public Optional<Image> get(UserAccount user) {
        return avatars.findById(user.getId()).map(a -> new Image(a.getContentType(), a.getImage()));
    }

    @Override
    public void delete(UserAccount user) {
        avatars.findById(user.getId()).ifPresent(avatars::delete);
    }

    @Override
    public boolean external() {
        return false;
    }
}
