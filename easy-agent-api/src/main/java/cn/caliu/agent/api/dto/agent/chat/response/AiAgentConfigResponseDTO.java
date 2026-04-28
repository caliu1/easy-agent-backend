package cn.caliu.agent.api.dto.agent.chat.response;

import lombok.Data;
/**
 * AiAgentConfigResponseDTO DTO，用于接口层数据传输。
 */

@Data
public class AiAgentConfigResponseDTO {
    /**
     * 鏅鸿兘浣揑D
     */
    private String agentId;

    /**
     * 鏅鸿兘浣撳悕绉?
     */
    private String agentName;

    /**
     * 鏅鸿兘浣撴弿杩?
     */
    private String agentDesc;

}

