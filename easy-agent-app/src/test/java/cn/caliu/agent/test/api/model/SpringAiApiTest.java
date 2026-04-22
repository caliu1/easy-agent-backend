package cn.caliu.agent.test.api.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;


@Slf4j
public class SpringAiApiTest {

    public static void main(String[] args) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/")
                .apiKey("sk-b5909159a5da494aa942fdfcb96e12ca")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        String call = chatModel.call("hi 你好哇!");

        log.info("测试结果:{}", call);
    }

}
