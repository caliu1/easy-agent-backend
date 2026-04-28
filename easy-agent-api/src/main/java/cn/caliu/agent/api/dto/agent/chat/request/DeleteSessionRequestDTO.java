package cn.caliu.agent.api.dto.agent.chat.request;

import lombok.Data;
/**
 * DeleteSessionRequestDTO DTO，用于接口层数据传输。
 */

@Data
public class DeleteSessionRequestDTO {

    private String sessionId;

    private String userId;

}

