package cn.caliu.agent.test.api.tool.skills;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;

@Slf4j
public class SpringAiToolTest {

    public static void main(String[] args) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/")
                .apiKey("sk-b5909159a5da494aa942fdfcb96e12ca")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        // https://github.com/spring-ai-community/spring-ai-agent-utils
//        ToolCallback toolCallback01 = SkillsTool.builder()
//                .addSkillsDirectory("/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/road-map/xfg-dev-tech-agent-skills/docs/skills")
//                .build();

        ToolCallback toolCallback02 = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("agent/skills"))
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .toolCallbacks(new ArrayList<>(){{
                            add(toolCallback02);
                        }})
                        .build())
                .build();

        String call = chatModel.call("基于 skill 解答，电脑性能优化");
        // String call = chatModel.call("你有什么技能");

        log.info("测试结果:{}", call);
    }

}
