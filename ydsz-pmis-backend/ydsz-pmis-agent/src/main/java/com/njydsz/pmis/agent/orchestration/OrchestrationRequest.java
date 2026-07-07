package com.njydsz.pmis.agent.orchestration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 编排请求
 *
 * <p>由协调器消费的顶层请求 DTO。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationRequest implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private String bizId;
    /** 业务引用（编码/名称） */
    private String bizRef;
    /** 调用人 ID */
    private String callerId;
    /** 调用人姓名 */
    private String callerName;
    /** 来源 */
    private String source;
    /** 编排模式 */
    private OrchestrationMode mode;
    /** 参与编排的 Agent 类型列表（按声明顺序敏感） */
    private List<String> agentTypes;
    /** 业务输入参数 / 事实 */
    private Map<String, Object> facts;
    /** 权重（VOTING 模式使用，key=agentType value=权重 0-1） */
    private Map<String, Double> weights;
    /** 置信度阈值（CASCADE 模式使用，达标即停） */
    private Double confidenceThreshold;
    /** 备注 */
    private String remark;
}
