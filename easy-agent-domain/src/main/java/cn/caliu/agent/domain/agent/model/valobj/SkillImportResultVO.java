package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skills 压缩包导入结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillImportResultVO {

    /**
     * OSS bucket。
     */
    private String bucket;

    /**
     * 上传根前缀（不带 oss://）。
     */
    private String prefix;

    /**
     * 成功上传的文件数。
     */
    private Integer fileCount;

    /**
     * 识别到的技能目录数（包含根目录 skill）。
     */
    private Integer skillCount;

    /**
     * 可直接回填到 chatModel.toolSkillsList 的配置项。
     */
    private List<ToolSkillLocationVO> toolSkillsList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolSkillLocationVO {
        private String type;
        private String path;
        private String skillName;
    }

}
