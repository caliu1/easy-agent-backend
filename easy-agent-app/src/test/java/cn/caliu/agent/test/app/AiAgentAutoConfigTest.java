package cn.caliu.agent.test.app;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentRegisterVO;
import com.alibaba.fastjson.JSON;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentAutoConfigTest {

    @Resource
    private ApplicationContext applicationContext;

    @Test
    public void test_agent() throws InterruptedException {
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100001", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "caliu")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("编写快速排序算法"));
        Flowable<Event> events = runner.runAsync("caliu", session.id(), userMsg);
        // Flowable<Event> events = runner.runAsync("caliu", session.id(), userMsg)
        //         .doOnSubscribe(s -> log.info("===> 开始订阅响应式流，准备触发大模型调用..."))
        //         .doOnNext(event -> log.info("===> 收到原始事件 (Raw Event): {}", event))
        //         .doOnError(e -> log.error("===> 流处理发生异常 (Stream Error): ", e))
        //         .doOnComplete(() -> log.info("===> 响应式流正常结束 (Stream Completed)"));

        ArrayList<String> output = new ArrayList<>();
        try {
            events.blockingForEach(event -> {
                String content = event.stringifyContent();
                // 有些状态变更事件 content 可能是 null，我们加个判空
                if (content != null && !content.isEmpty()) {
                    output.add(content);
                }
            });
        } catch (Exception e) {
            log.error("===> 阻塞收集结果时发生异常: ", e);
        }

        log.info("===> 最终测试结果: {}", output);

        new CountDownLatch(1).await();

    }

    @Test
    public void test_single_agent() throws InterruptedException {
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100002", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "caliu")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("转换为大写caliu"));
        Flowable<Event> events = runner.runAsync("caliu", session.id(), userMsg);
        // Flowable<Event> events = runner.runAsync("caliu", session.id(), userMsg)
        //         .doOnSubscribe(s -> log.info("===> 开始订阅响应式流，准备触发大模型调用..."))
        //         .doOnNext(event -> log.info("===> 收到原始事件 (Raw Event): {}", event))
        //         .doOnError(e -> log.error("===> 流处理发生异常 (Stream Error): ", e))
        //         .doOnComplete(() -> log.info("===> 响应式流正常结束 (Stream Completed)"));

        ArrayList<String> output = new ArrayList<>();
        try {
            events.blockingForEach(event -> {
                String content = event.stringifyContent();
                // 有些状态变更事件 content 可能是 null，我们加个判空
                if (content != null && !content.isEmpty()) {
                    output.add(content);
                }
            });
        } catch (Exception e) {
            log.error("===> 阻塞收集结果时发生异常: ", e);
        }

        log.info("===> 最终测试结果: {}", output);
    }

    @Test
    public void test_parallel_research_app(){
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100003", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "caliu")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("你具备哪些能力"));
        Flowable<Event> events = runner.runAsync("caliu", session.id(), userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        log.info("测试结果:{}", JSON.toJSONString(outputs));
    }


}
