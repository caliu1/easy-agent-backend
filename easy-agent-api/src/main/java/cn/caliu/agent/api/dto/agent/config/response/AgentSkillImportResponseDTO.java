package cn.caliu.agent.api.dto.agent.config.response;

import lombok.Data;

import java.util.List;

/**
 * skills 压缩包导入响应 DTO。
 */
@Data
public class AgentSkillImportResponseDTO {

    private String bucket;
    private String prefix;
    private Integer fileCount;
    private Integer skillCount;
    private List<ToolSkillItemDTO> toolSkillsList;

    @Data
    public static class ToolSkillItemDTO {
        private String type;
        private String path;
        private String skillName;
    }

}
