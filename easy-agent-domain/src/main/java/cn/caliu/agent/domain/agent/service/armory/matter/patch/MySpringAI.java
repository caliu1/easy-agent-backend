package cn.caliu.agent.domain.agent.service.armory.matter.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.springai.MessageConverter;
import com.google.adk.models.springai.error.SpringAIErrorMapper;
import com.google.adk.models.springai.observability.SpringAIObservabilityHandler;
import com.google.adk.models.springai.properties.SpringAIProperties;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * MySpringAI 绫汇€?
 */


public class MySpringAI extends BaseLlm {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ObjectMapper objectMapper;
    private final MessageConverter messageConverter;
    private final SpringAIObservabilityHandler observabilityHandler;

    public MySpringAI(ChatModel chatModel) {
        super(extractModelName(chatModel));
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.streamingChatModel =
                (chatModel instanceof StreamingChatModel) ? (StreamingChatModel) chatModel : null;
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(createDefaultObservabilityConfig());
    }

    public MySpringAI(ChatModel chatModel, String modelName) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.streamingChatModel =
                (chatModel instanceof StreamingChatModel) ? (StreamingChatModel) chatModel : null;
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(createDefaultObservabilityConfig());
    }

    public MySpringAI(StreamingChatModel streamingChatModel) {
        super(extractModelName(streamingChatModel));
        this.chatModel =
                (streamingChatModel instanceof ChatModel) ? (ChatModel) streamingChatModel : null;
        this.streamingChatModel =
                Objects.requireNonNull(streamingChatModel, "streamingChatModel cannot be null");
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(createDefaultObservabilityConfig());
    }

    public MySpringAI(StreamingChatModel streamingChatModel, String modelName) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel =
                (streamingChatModel instanceof ChatModel) ? (ChatModel) streamingChatModel : null;
        this.streamingChatModel =
                Objects.requireNonNull(streamingChatModel, "streamingChatModel cannot be null");
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(createDefaultObservabilityConfig());
    }

    public MySpringAI(ChatModel chatModel, StreamingChatModel streamingChatModel, String modelName) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.streamingChatModel =
                Objects.requireNonNull(streamingChatModel, "streamingChatModel cannot be null");
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(createDefaultObservabilityConfig());
    }

    public MySpringAI(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            String modelName,
            SpringAIProperties.Observability observabilityConfig) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.streamingChatModel =
                Objects.requireNonNull(streamingChatModel, "streamingChatModel cannot be null");
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(
                        Objects.requireNonNull(observabilityConfig, "observabilityConfig cannot be null"));
    }

    public MySpringAI(
            ChatModel chatModel, String modelName, SpringAIProperties.Observability observabilityConfig) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.streamingChatModel =
                (chatModel instanceof StreamingChatModel) ? (StreamingChatModel) chatModel : null;
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(
                        Objects.requireNonNull(observabilityConfig, "observabilityConfig cannot be null"));
    }

    public MySpringAI(
            StreamingChatModel streamingChatModel,
            String modelName,
            SpringAIProperties.Observability observabilityConfig) {
        super(Objects.requireNonNull(modelName, "model name cannot be null"));
        this.chatModel =
                (streamingChatModel instanceof ChatModel) ? (ChatModel) streamingChatModel : null;
        this.streamingChatModel =
                Objects.requireNonNull(streamingChatModel, "streamingChatModel cannot be null");
        this.objectMapper = new ObjectMapper();
        this.messageConverter = new MyMessageConverter(objectMapper);
        this.observabilityHandler =
                new SpringAIObservabilityHandler(
                        Objects.requireNonNull(observabilityConfig, "observabilityConfig cannot be null"));
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
        if (stream) {
            if (this.streamingChatModel == null) {
                return Flowable.error(new IllegalStateException("StreamingChatModel is not configured"));
            }

            return generateStreamingContent(llmRequest);
        } else {
            if (this.chatModel == null) {
                return Flowable.error(new IllegalStateException("ChatModel is not configured"));
            }

            return generateContent(llmRequest);
        }
    }

    private Flowable<LlmResponse> generateContent(LlmRequest llmRequest) {
        SpringAIObservabilityHandler.RequestContext context =
                observabilityHandler.startRequest(model(), "chat");

        try {
            Prompt prompt = messageConverter.toLlmPrompt(llmRequest);
            observabilityHandler.logRequest(prompt.toString(), model());

            ChatResponse chatResponse = chatModel.call(prompt);
            LlmResponse llmResponse = messageConverter.toLlmResponse(chatResponse);

            observabilityHandler.logResponse(extractTextFromResponse(llmResponse), model());

            // Extract token counts if available
            int totalTokens = extractTokenCount(chatResponse);
            int inputTokens = extractInputTokenCount(chatResponse);
            int outputTokens = extractOutputTokenCount(chatResponse);

            observabilityHandler.recordSuccess(context, totalTokens, inputTokens, outputTokens);
            return Flowable.just(llmResponse);
        } catch (Exception e) {
            observabilityHandler.recordError(context, e);
            return Flowable.error(toProviderAwareRuntimeException(e));
        }
    }

    private Flowable<LlmResponse> generateStreamingContent(LlmRequest llmRequest) {
        SpringAIObservabilityHandler.RequestContext context =
                observabilityHandler.startRequest(model(), "streaming");

        return Flowable.create(
                emitter -> {
                    AtomicBoolean terminated = new AtomicBoolean(false);
                    try {
                        Prompt prompt = messageConverter.toLlmPrompt(llmRequest);
                        observabilityHandler.logRequest(prompt.toString(), model());

                        Flux<ChatResponse> responseFlux = streamingChatModel.stream(prompt);

                        reactor.core.Disposable subscription = responseFlux.subscribe(
                                chatResponse -> {
                                    if (terminated.get() || emitter.isCancelled()) {
                                        return;
                                    }
                                    try {
                                        LlmResponse llmResponse =
                                                messageConverter.toLlmResponse(chatResponse, true);
                                        emitter.onNext(llmResponse);
                                    } catch (Exception e) {
                                        terminateStreamingWithError(emitter, terminated, context, e);
                                    }
                                },
                                error -> terminateStreamingWithError(emitter, terminated, context, error),
                                () -> {
                                    if (terminated.compareAndSet(false, true) && !emitter.isCancelled()) {
                                        observabilityHandler.recordSuccess(context, 0, 0, 0);
                                        emitter.onComplete();
                                    }
                                });

                        emitter.setCancellable(subscription::dispose);
                    } catch (Exception e) {
                        terminateStreamingWithError(emitter, terminated, context, e);
                    }
                },
                BackpressureStrategy.BUFFER);
    }

    private void terminateStreamingWithError(
            io.reactivex.rxjava3.core.FlowableEmitter<LlmResponse> emitter,
            AtomicBoolean terminated,
            SpringAIObservabilityHandler.RequestContext context,
            Throwable error) {
        observabilityHandler.recordError(context, error);
        if (terminated.compareAndSet(false, true) && !emitter.isCancelled()) {
            emitter.onError(toProviderAwareRuntimeException(error));
        }
    }
    private RuntimeException toProviderAwareRuntimeException(Throwable error) {
        SpringAIErrorMapper.MappedError mappedError = SpringAIErrorMapper.mapError(error);
        String message = mappedError.getNormalizedMessage();

        if (error instanceof WebClientResponseException webClientError) {
            String responseBody = webClientError.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isBlank()) {
                responseBody = responseBody.replaceAll("[\\r\\n]+", " ").trim();
                if (responseBody.length() > 1200) {
                    responseBody = responseBody.substring(0, 1200) + "...";
                }
                message = message + " | provider_response=" + responseBody;
            }
        }

        return new RuntimeException(message, error);
    }

    @Override
    public BaseLlmConnection connect(LlmRequest llmRequest) {
        throw new UnsupportedOperationException(
                "Live connection is not supported for Spring AI models.");
    }

    private static String extractModelName(Object model) {
        // Spring AI models may not always have a straightforward way to get model name
        // This is a fallback that can be overridden by providing explicit model name
        String className = model.getClass().getSimpleName();
        return className.toLowerCase().replace("chatmodel", "").replace("model", "");
    }

    private SpringAIProperties.Observability createDefaultObservabilityConfig() {
        SpringAIProperties.Observability config = new SpringAIProperties.Observability();
        config.setEnabled(true);
        config.setMetricsEnabled(true);
        config.setIncludeContent(false);
        return config;
    }

    private int extractTokenCount(ChatResponse chatResponse) {
        // Spring AI may include usage metadata in the response
        // This is a simplified implementation - actual token counts depend on provider
        try {
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                return chatResponse.getMetadata().getUsage().getTotalTokens();
            }
        } catch (Exception e) {
            // Ignore errors in token extraction
        }
        return 0;
    }

    private int extractInputTokenCount(ChatResponse chatResponse) {
        try {
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                return chatResponse.getMetadata().getUsage().getPromptTokens();
            }
        } catch (Exception e) {
            // Ignore errors in token extraction
        }
        return 0;
    }

    private int extractOutputTokenCount(ChatResponse chatResponse) {
        try {
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                return chatResponse.getMetadata().getUsage().getCompletionTokens();
            }
        } catch (Exception e) {
            // Ignore errors in token extraction
        }
        return 0;
    }

    private String extractTextFromResponse(LlmResponse response) {
        if (response.content().isPresent() && response.content().get().parts().isPresent()) {
            return response.content().get().parts().get().stream()
                    .map(part -> part.text().orElse(""))
                    .filter(text -> text != null && !text.isEmpty())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

}

