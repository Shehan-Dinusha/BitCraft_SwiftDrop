package com.swiftdrop.analytics.ai;

import com.swiftdrop.analytics.dto.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryAssistantAgentTest {
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }
    @Test void legacyPayloadUsesVerifiedIdentityAndNewTools() {
        var request = new MockHttpServletRequest();
        UUID user = UUID.randomUUID();
        request.addHeader("X-User-Id", user.toString()); request.addHeader("X-User-Role", "CUSTOMER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var chat = mock(AiChatService.class);
        when(chat.chat(any(), any())).thenReturn(new AiChatResponse("Delivered", UUID.randomUUID(),
                List.of(new AiChatResponse.ToolUse("track_parcel", "OK"))));
        var result = new DeliveryAssistantAgent(chat).processQuery(new AssistantQueryRequest("Where is my parcel?", "SYSTEM_ADMIN", "SD-ABCD-1234"));
        assertThat(result.answer()).isEqualTo("Delivered");
        assertThat(result.detectedIntent()).isEqualTo("TRACKING_STATUS");
        var context = ArgumentCaptor.forClass(ToolRequestContext.class);
        var input = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(chat).chat(input.capture(), context.capture());
        assertThat(context.getValue().role()).isEqualTo("CUSTOMER");
        assertThat(context.getValue().userId()).isEqualTo(user);
        assertThat(input.getValue().message()).contains("SD-ABCD-1234");
    }
}
