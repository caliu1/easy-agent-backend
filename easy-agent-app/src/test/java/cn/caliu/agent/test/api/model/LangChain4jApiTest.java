package cn.caliu.agent.test.api.model;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public class LangChain4jApiTest {

    public static void main(String[] args) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/")
                .apiKey("sk-b5909159a5da494aa942fdfcb96e12ca")
                .modelName("qwen-plus")
                .build();

        String chat = model.chat("hi 你好哇!");
        log.info("测试结果:{}", chat);
    }

}
