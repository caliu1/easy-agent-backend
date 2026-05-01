package cn.caliu.agent.api.dto.agent.config.request;

import lombok.Data;

import java.util.List;

/**
 * 在线保存 skill 文件结构请求。
 */
@Data
public class AgentSkillSaveRequestDTO {

    private String operator;
    private String rootFolder;
    private List<Entry> entries;

    @Data
    public static class Entry {
        private String kind;
        private String path;
        private String content;
    }

}
