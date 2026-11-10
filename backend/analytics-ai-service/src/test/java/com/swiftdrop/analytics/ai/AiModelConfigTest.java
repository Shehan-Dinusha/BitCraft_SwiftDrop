package com.swiftdrop.analytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.config.AiModelConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiModelConfigTest {
    final AiModelConfig config = new AiModelConfig();
    final ObjectMapper mapper = new ObjectMapper();
    @Test void mockNeedsNoKey() { assertThat(config.llmClient(mapper, "mock", "", "", "")).isInstanceOf(MockLlmClient.class); }
    @Test void unknownProviderRejected() { assertThatThrownBy(() -> config.llmClient(mapper, "unknown", "", "", "")).hasMessageContaining("AI_PROVIDER"); }
    @Test void realModeRequiresCredentials() { assertThatThrownBy(() -> config.llmClient(mapper, "azure-openai", "", "", "")).hasMessageContaining("AI_API_KEY"); }
    @Test void azureRequiresHttpsEndpoint() { assertThatThrownBy(() -> config.llmClient(mapper, "azure-openai", "key", "deployment", "http://unsafe.test/openai/v1")).hasMessageContaining("HTTPS"); }
    @Test void azureAndOpenAiCanBeConfiguredWithoutMakingRequests() {
        assertThat(config.llmClient(mapper, "azure-openai", "key", "deployment", "https://resource.openai.azure.com/openai/v1"))
                .isInstanceOf(OpenAiCompatibleLlmClient.class);
        assertThat(config.llmClient(mapper, "openai", "key", "model", "")).isInstanceOf(OpenAiCompatibleLlmClient.class);
    }
}
