package com.swiftdrop.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import java.net.URI;

@Configuration
public class AiModelConfig {
    @Bean
    public LlmClient llmClient(ObjectMapper mapper,
            @Value("${ai.provider:mock}") String provider,
            @Value("${ai.api-key:}") String key,
            @Value("${ai.model:}") String model,
            @Value("${ai.endpoint:}") String endpoint) {
        if ("mock".equals(provider)) return new MockLlmClient(mapper);
        if (!java.util.Set.of("azure-openai", "openai").contains(provider)) {
            throw new IllegalStateException("AI_PROVIDER must be mock, azure-openai or openai");
        }
        if (key.isBlank() || model.isBlank()) throw new IllegalStateException("Real AI mode requires AI_API_KEY and AI_MODEL");
        if (endpoint.isBlank() && "openai".equals(provider)) endpoint = "https://api.openai.com/v1";
        URI uri;
        try { uri = URI.create(endpoint); } catch (Exception ex) { throw new IllegalStateException("Invalid AI_ENDPOINT"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("AI_ENDPOINT must be an HTTPS API base URL; Azure requires /openai/v1");
        }
        return new OpenAiCompatibleLlmClient(mapper, endpoint, key, model);
    }
}
