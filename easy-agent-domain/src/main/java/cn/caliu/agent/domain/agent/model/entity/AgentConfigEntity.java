package cn.caliu.agent.domain.agent.model.entity;

import cn.caliu.agent.types.enums.ResponseCode;
import cn.caliu.agent.types.exception.AppException;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
public class AgentConfigEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_OFFICIAL = "OFFICIAL";

    public static final String PLAZA_ON = "ON";
    public static final String PLAZA_OFF = "OFF";

    private String agentId;
    private String appName;
    private String agentName;
    private String agentDesc;
    private String configJson;
    private String status;
    private Long currentVersion;
    private Long publishedVersion;
    private String operator;
    private String ownerUserId;
    private String sourceType;
    private String plazaStatus;
    private Long plazaPublishTime;
    private Long createTime;
    private Long updateTime;

    public boolean isPublished() {
        return STATUS_PUBLISHED.equalsIgnoreCase(StringUtils.defaultString(status));
    }

    public boolean isOfficialSource() {
        return SOURCE_OFFICIAL.equalsIgnoreCase(normalizeSourceType());
    }

    public boolean isInPlaza() {
        return PLAZA_ON.equalsIgnoreCase(normalizePlazaStatus());
    }

    public String resolveOwnerUserId() {
        if (StringUtils.isNotBlank(ownerUserId)) {
            return ownerUserId.trim();
        }
        return StringUtils.trimToEmpty(operator);
    }

    public boolean canOperatePlaza(String currentOperator) {
        if (StringUtils.isBlank(currentOperator)) {
            return false;
        }
        String owner = resolveOwnerUserId();
        return StringUtils.isNotBlank(owner) && owner.equals(currentOperator.trim());
    }

    public String normalizeSourceType() {
        return StringUtils.isBlank(sourceType) ? SOURCE_USER : sourceType.trim();
    }

    public String normalizePlazaStatus() {
        return StringUtils.isBlank(plazaStatus) ? PLAZA_OFF : plazaStatus.trim();
    }

    public static AgentConfigEntity createUserDraft(
            String agentId,
            String appName,
            String agentName,
            String agentDesc,
            String configJson,
            String operator,
            String ownerUserId,
            long now
    ) {
        String normalizedOperator = normalizeOperator(operator);
        return AgentConfigEntity.builder()
                .agentId(StringUtils.trimToEmpty(agentId))
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .configJson(configJson)
                .status(STATUS_DRAFT)
                .currentVersion(1L)
                .publishedVersion(null)
                .operator(normalizedOperator)
                .ownerUserId(resolveOwner(ownerUserId, normalizedOperator))
                .sourceType(SOURCE_USER)
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    public AgentConfigEntity toDraftUpdate(
            String appName,
            String agentName,
            String agentDesc,
            String configJson,
            long nextVersion,
            String operatorCandidate,
            long updateTime
    ) {
        return copyBuilder()
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .configJson(configJson)
                .status(STATUS_DRAFT)
                .currentVersion(nextVersion)
                .publishedVersion(publishedVersion)
                .operator(resolveOperatorWithFallback(operatorCandidate))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(normalizePlazaStatus())
                .plazaPublishTime(plazaPublishTime)
                .updateTime(updateTime)
                .build();
    }

    public AgentConfigEntity toPublished(
            String appName,
            String agentName,
            String agentDesc,
            long publishedVersion,
            String operatorCandidate,
            long updateTime
    ) {
        return copyBuilder()
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .status(STATUS_PUBLISHED)
                .currentVersion(publishedVersion)
                .publishedVersion(publishedVersion)
                .operator(resolveOperatorWithFallback(operatorCandidate))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(normalizePlazaStatus())
                .plazaPublishTime(plazaPublishTime)
                .updateTime(updateTime)
                .build();
    }

    public AgentConfigEntity toOffline(String operatorCandidate, long updateTime) {
        return copyBuilder()
                .status(STATUS_OFFLINE)
                .operator(resolveOperatorWithFallback(operatorCandidate))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .updateTime(updateTime)
                .build();
    }

    public AgentConfigEntity toRollbackPublished(
            String appName,
            String agentName,
            String agentDesc,
            String configJson,
            long nextVersion,
            String rollbackOperator,
            long updateTime
    ) {
        return copyBuilder()
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .configJson(configJson)
                .status(STATUS_PUBLISHED)
                .currentVersion(nextVersion)
                .publishedVersion(nextVersion)
                .operator(normalizeOperator(rollbackOperator))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(normalizePlazaStatus())
                .plazaPublishTime(plazaPublishTime)
                .updateTime(updateTime)
                .build();
    }

    public AgentConfigEntity toPlazaPublished(String operatorCandidate, long publishTime, long updateTime) {
        return copyBuilder()
                .operator(resolveOperatorWithFallback(operatorCandidate))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(PLAZA_ON)
                .plazaPublishTime(publishTime)
                .updateTime(updateTime)
                .build();
    }

    public AgentConfigEntity toPlazaUnpublished(String operatorCandidate, long updateTime) {
        return copyBuilder()
                .operator(resolveOperatorWithFallback(operatorCandidate))
                .ownerUserId(resolveOwnerUserId())
                .sourceType(normalizeSourceType())
                .plazaStatus(PLAZA_OFF)
                .plazaPublishTime(null)
                .updateTime(updateTime)
                .build();
    }

    public void assertCanOperatePlaza(String currentOperator) {
        if (isOfficialSource()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "official agent can not be changed by this api");
        }
        if (StringUtils.isBlank(currentOperator)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "operator is blank");
        }
        if (!canOperatePlaza(currentOperator)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "no permission to update plaza status");
        }
    }

    public void assertCanBeListedInPlaza() {
        if (!isPublished()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "only published agent can be listed in plaza");
        }
    }

    private AgentConfigEntityBuilder copyBuilder() {
        return AgentConfigEntity.builder()
                .agentId(agentId)
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .configJson(configJson)
                .status(status)
                .currentVersion(currentVersion)
                .publishedVersion(publishedVersion)
                .operator(operator)
                .ownerUserId(ownerUserId)
                .sourceType(sourceType)
                .plazaStatus(plazaStatus)
                .plazaPublishTime(plazaPublishTime)
                .createTime(createTime)
                .updateTime(updateTime);
    }

    private String resolveOperatorWithFallback(String operatorCandidate) {
        return StringUtils.isNotBlank(operatorCandidate) ? operatorCandidate.trim() : StringUtils.trimToEmpty(operator);
    }

    private static String resolveOwner(String ownerUserId, String operator) {
        if (StringUtils.isNotBlank(ownerUserId)) {
            return ownerUserId.trim();
        }
        return StringUtils.trimToEmpty(operator);
    }

    private static String normalizeOperator(String operator) {
        return StringUtils.trimToEmpty(operator);
    }

}
