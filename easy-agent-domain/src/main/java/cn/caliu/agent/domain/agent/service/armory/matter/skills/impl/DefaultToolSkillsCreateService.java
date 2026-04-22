package cn.caliu.agent.domain.agent.service.armory.matter.skills.impl;

import cn.caliu.agent.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.caliu.agent.domain.agent.service.armory.matter.skills.IToolSkillsCreateService;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultToolSkillsCreateService implements IToolSkillsCreateService {

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception {
        String type = toolSkills.getType();
        String path = toolSkills.getPath();

        List<ToolCallback> toolCallbackList = new ArrayList<>();

        if (type.equals("resource")){
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsResource(new ClassPathResource(path))
                    .build();
            toolCallbackList.add(toolCallback);
        }
        if (type.equals("directory")){
            ToolCallback toolCallback = SkillsTool.builder()
                    .addSkillsDirectory(path)
                    .build();
            toolCallbackList.add(toolCallback);
        }

        return toolCallbackList.toArray(new ToolCallback[0]);
    }
}
