package com.swiftdrop.iam.service.avatar;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.swiftdrop.iam.domain.entity.UserAccount;

import java.util.Optional;

/**
 * Profile pictures in a private Azure Blob container, one blob per user at {@code avatars/<public id>}.
 * The container is never public: pictures are only served through the authenticated
 * {@code /api/v1/users/me/avatar} endpoint, so no blob URL ever reaches a browser.
 */
public class BlobAvatarStore implements AvatarStore {

    static final String PREFIX = "avatars/";

    private final BlobContainerClient container;

    public BlobAvatarStore(BlobContainerClient container) {
        this.container = container;
    }

    @Override
    public void put(UserAccount user, String contentType, byte[] image) {
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(BinaryData.fromBytes(image))
                .setHeaders(new BlobHttpHeaders().setContentType(contentType).setCacheControl("private, no-cache"));
        blob(user).uploadWithResponse(options, null, null);
    }

    @Override
    public Optional<Image> get(UserAccount user) {
        BlobClient blob = blob(user);
        try {
            String contentType = blob.getProperties().getContentType();
            return Optional.of(new Image(contentType, blob.downloadContent().toBytes()));
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(UserAccount user) {
        blob(user).deleteIfExists();
    }

    @Override
    public boolean external() {
        return true;
    }

    private BlobClient blob(UserAccount user) {
        return container.getBlobClient(PREFIX + user.getPublicId());
    }
}
