package cn.caliu.agent.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 目录资产读取结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillAssetsResultVO {

    private String bucket;
    private String prefix;
    private Integer fileCount;
    private Integer folderCount;
    private List<SkillAssetEntryVO> entries;

}

