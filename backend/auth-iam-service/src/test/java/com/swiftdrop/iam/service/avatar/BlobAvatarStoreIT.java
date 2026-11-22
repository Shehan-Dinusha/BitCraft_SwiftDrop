package com.swiftdrop.iam.service.avatar;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.swiftdrop.iam.domain.entity.UserAccount;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BlobAvatarStore} against Azurite, Microsoft's local Azure Storage emulator, so the real Blob
 * client and its error codes are exercised without an Azure account. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class BlobAvatarStoreIT {

    /** Azurite's fixed development account; public in Microsoft's documentation, not a secret. */
    private static final String DEV_ACCOUNT = "devstoreaccount1";
    private static final String DEV_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Container
    static GenericContainer<?> azurite = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:3.34.0")
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
            .withExposedPorts(10000);

    private static BlobContainerClient container;

    @BeforeAll
    static void connect() {
        String connection = "DefaultEndpointsProtocol=http;AccountName=" + DEV_ACCOUNT + ";AccountKey=" + DEV_KEY
                + ";BlobEndpoint=http://" + azurite.getHost() + ":" + azurite.getMappedPort(10000) + "/" + DEV_ACCOUNT + ";";
        container = new BlobContainerClientBuilder().connectionString(connection)
                .containerName("profile-photos").buildClient();
        container.createIfNotExists();
    }

    @Test
    void storesReadsReplacesAndDeletesAPictureUnderAvatars() {
        BlobAvatarStore store = new BlobAvatarStore(container);
        UserAccount user = new UserAccount();
        user.setPublicId(UUID.randomUUID());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9};

        assertThat(store.get(user)).isEmpty();

        store.put(user, "image/png", png);
        var stored = store.get(user).orElseThrow();
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.bytes()).isEqualTo(png);
        assertThat(container.getBlobClient("avatars/" + user.getPublicId()).exists()).isTrue();

        store.put(user, "image/jpeg", jpeg);
        assertThat(store.get(user).orElseThrow().bytes()).isEqualTo(jpeg);

        store.delete(user);
        assertThat(store.get(user)).isEmpty();
        store.delete(user); // deleting twice is fine
    }

    @Test
    void theContainerIsPrivate() {
        assertThat(container.getAccessPolicy().getBlobAccessType()).isNull();
    }
}
