package cn.caliu.agent.domain.user.service.subscription;

import cn.caliu.agent.domain.user.repository.IAgentSubscribeRepository;
import cn.caliu.agent.domain.user.service.IUserSubscriptionService;
import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户订阅领域服务实现。
 *
 * 负责用户与 Agent 订阅关系的查询、订阅、取消订阅。
 */
@Service
public class UserSubscriptionServiceImpl implements IUserSubscriptionService {

    @Resource
    private IAgentSubscribeRepository agentSubscribeRepository;

    @Override
    public List<String> querySubscribedAgentIds(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        return agentSubscribeRepository.querySubscribedAgentIds(userId.trim());
    }

    @Override
    public boolean subscribeAgent(String userId, String agentId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        if (StringUtils.isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        agentSubscribeRepository.subscribe(userId.trim(), agentId.trim());
        return true;
    }

    @Override
    public boolean unsubscribeAgent(String userId, String agentId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "userId is blank");
        }
        if (StringUtils.isBlank(agentId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "agentId is blank");
        }
        return agentSubscribeRepository.unsubscribe(userId.trim(), agentId.trim());
    }

}
