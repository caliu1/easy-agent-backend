package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;

import java.util.List;

/**
 * Skill 目录资产查询响应。
 */
@Data
public class AgentSkillAssetsResponseDTO {

    private String bucket;
    private String prefix;
    private Integer fileCount;
    private Integer folderCount;
    private List<Entry> entries;

    @Data
    public static class Entry {
        private String kind;
        private String path;
        private String content;
    }

}

