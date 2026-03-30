package cn.caliu.agent.domain.agent.model.valobj;

import com.google.adk.runner.Runner;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiAgentConfigTableVO {

    private String appName;
    private Agent agent;
    private Module module;

    @Data
    public static class Agent {
        private String agentId;
        private String agentName;
        private String agentDesc;

    }

    @Data
    public static class Module {
        private AiApi aiApi;
        private ChatModel chatModel;
        private List<Agent> agents;
        private List<AgentWorkflow> agentWorkflows;
        private Runner runner;
        @Data
        public static class AiApi {
            private String baseUrl;
            private String apiKey;
            private String completionsPath = "/v1/chat/completions";
            private String embeddingsPath = "/v1/embeddings";
        }

        @Data
        public static class ChatModel {
            private String model;
            private List<ToolMcp> toolMcpList;

            @Data
            public static class ToolMcp {
                private SSEServerParameters sse;
                private StdioServerParameters stdio;
                private LocalParameters local;

                @Data
                public static class SSEServerParameters {
                    private String name;
                    private String baseUri;
                    private String sseEndpoint;
                    private Integer requestTimeout = 3000;
                }

                @Data
                public static class StdioServerParameters {
                    private String name;
                    private Integer requestTimeout = 3000;
                    private ServerParameters serverParameters;

                    @Data
                    public static class ServerParameters {
                        private String command;
                        private List<String> args;
                        private Map<String, String> env;
                    }
                }

                @Data
                public static class LocalParameters{
                    private String name;
                }
            }
        }


        @Data
        public static class Agent {
            private String name;
            private String instruction;
            private String description;
            private String outputKey;
        }

        @Data
        public static class AgentWorkflow {
            /**
             * 类型；loop、parallel、sequential
             */
            private String type;
            private String name;
            private List<String> subAgents;
            private String description;
            private Integer maxIterations = 3;

        }

        @Data
        public static class Runner {
            private String agentName;
            private List<String> pluginNameList;
        }

    }

}
