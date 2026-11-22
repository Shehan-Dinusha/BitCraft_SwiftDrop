package com.swiftdrop.iam.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.swiftdrop.iam.domain.repository.UserAvatarRepository;
import com.swiftdrop.iam.service.avatar.AvatarStore;
import com.swiftdrop.iam.service.avatar.BlobAvatarStore;
import com.swiftdrop.iam.service.avatar.DatabaseAvatarStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks where profile pictures are stored. With {@code AZURE_STORAGE_CONNECTION_STRING} set (the same
 * storage account as proof-of-delivery photos) they go to a private Blob container; without it they
 * stay in PostgreSQL, so local runs and tests need no Azure account.
 */
@Configuration
public class AvatarStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageConfig.class);

    @Bean
    public AvatarStore avatarStore(
            @Value("${swiftdrop.avatars.azure-connection-string:}") String connectionString,
            @Value("${swiftdrop.avatars.container-name:profile-photos}") String containerName,
            UserAvatarRepository avatars) {
        if (connectionString == null || connectionString.isBlank()) {
            log.info("Profile pictures are stored in PostgreSQL (no Azure storage connection string)");
            return new DatabaseAvatarStore(avatars);
        }
        BlobContainerClient container = new BlobContainerClientBuilder()
                .connectionString(connectionString.trim())
                .containerName(containerName)
                .buildClient();
        // Created private (no public access level) if it does not exist yet. A storage outage must not
        // stop sign-in from starting: only picture requests fail until Azure answers again.
        try {
            container.createIfNotExists();
        } catch (RuntimeException e) {
            log.warn("Could not reach the profile picture container {} at startup ({})", containerName,
                    e.getClass().getSimpleName());
        }
        log.info("Profile pictures are stored in Azure Blob container {}", containerName);
        return new BlobAvatarStore(container);
    }
}
