package cn.caliu.agent.api.dto.agent.chat.request;

import lombok.Data;
/**
 * CreateSessionRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}

