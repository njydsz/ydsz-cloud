package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AgentDefinition 视图对象。
 *
 * <p>用于 Controller 层返回 Agent 定义的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentDefinitionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;
    /** Agent 编码 */
    private String agentCode;
    /** Agent 名称 */
    private String agentName;
    /** Agent 类型 */
    private String agentType;
    /** 描述 */
    private String description;
    /** 系统提示词 */
    private String systemPrompt;
    /** 模型配置 JSON */
    private String modelConfig;
    /** 工具名称列表 JSON */
    private String toolNames;
    /** 温度参数 */
    private Double temperature;
    /** 最大生成 Token 数 */
    private Integer maxTokens;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
