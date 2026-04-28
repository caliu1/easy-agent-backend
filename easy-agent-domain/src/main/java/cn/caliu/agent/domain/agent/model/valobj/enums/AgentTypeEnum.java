package cn.caliu.agent.domain.agent.model.valobj.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
/**
 * AgentTypeEnum 枚举定义。
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AgentTypeEnum {

    Supervisor("supervisor", "supervisor", "supervisorAgentNode"),
    Loop("loop", "loop", "loopAgentNode"),
    Parallel("parallel", "parallel", "parallelAgentNode"),
    Sequential("sequential", "sequential", "sequentialAgentNode");

    private String name;
    private String type;
    private String node;

    public static AgentTypeEnum formType(String type) {
        if (type == null) {
            return null;
        }

        for (AgentTypeEnum value : values()) {
            if (value.getType().equalsIgnoreCase(type)) {
                return value;
            }
        }

        return null;
    }

}
