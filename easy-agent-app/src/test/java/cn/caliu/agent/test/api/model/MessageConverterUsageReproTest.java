package cn.caliu.agent.test.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.springai.MessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class MessageConverterUsageReproTest {

    @Test
    void usageShouldBeMappedFromSpringAiChatResponseToAdkLlmResponse() {
        MessageConverter converter = new MessageConverter(new ObjectMapper());

        AssistantMessage assistantMessage = new AssistantMessage("hello");
        Generation generation = new Generation(assistantMessage);

        DefaultUsage usage = new DefaultUsage(12, 8, 20);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id("resp-1")
                .model("dummy-model")
                .usage(usage)
                .build();

        ChatResponse chatResponse = new ChatResponse(List.of(generation), metadata);

        // Precondition: Spring AI usage exists and is non-zero
        assertNotNull(chatResponse.getMetadata());
        assertNotNull(chatResponse.getMetadata().getUsage());
        assertEquals(20, chatResponse.getMetadata().getUsage().getTotalTokens());

        LlmResponse llmResponse = converter.toLlmResponse(chatResponse, true);

        // Expected: should be present, but currently missing in google-adk-spring-ai
        assertTrue(llmResponse.usageMetadata().isPresent(),
                "Expected usageMetadata to be mapped from ChatResponse metadata usage");
    }
}
