package cn.caliu.agent.types.common;

/**
 * Agent 流式事件内容标记常量。
 *
 * 说明：
 * - 事件在底层以纯文本传递，前缀标记用于应用层识别事件类型。
 * - 与前端渲染协议保持一致，变更需前后端同步。
 */
public final class AgentStreamMarker {

    private AgentStreamMarker() {}

    public static final String THINKING = "[[AGENT_THINKING]]";
    public static final String ROUTE = "[[AGENT_ROUTE]]";
    public static final String REPLY = "[[AGENT_REPLY]]";
    public static final String FINAL = "[[AGENT_FINAL]]";
    public static final String HISTORY_EVENT = "[[AGENT_HISTORY_EVENT]]";
}

