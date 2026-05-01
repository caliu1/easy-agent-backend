package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 目录中的资产项（文件/文件夹）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAssetEntryVO {

    /**
     * 类型：file 或 folder。
     */
    private String kind;

    /**
     * 相对 skill 根目录路径，例如：
     * - SKILL.md
     * - docs/guide.md
     * - assets/icons
     */
    private String path;

    /**
     * 文件内容（kind=file 时有效）。
     */
    private String content;

}
