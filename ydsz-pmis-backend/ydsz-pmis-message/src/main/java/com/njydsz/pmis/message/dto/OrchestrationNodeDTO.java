package com.njydsz.pmis.message.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 消息编排节点 DTO。
 *
 * <p>P1-9: DAG 流程中的一个节点，表示一次消息发送操作。
 * 节点间通过 {@code dependsOn} 建立依赖关系，形成有向无环图。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
public class OrchestrationNodeDTO {

    /** 节点 ID（流程内唯一） */
    private String nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 发送通道 */
    private String channel;

    /** 模板编码 */
    private String templateCode;

    /** 接收人（固定值或 SpEL 表达式，如 {@code #{parent.receiver}}） */
    private String receiver;

    /** 模板参数（固定值或 SpEL 表达式） */
    private Map<String, Object> params;

    /** 依赖节点 ID 列表（必须全部成功后才能执行本节点） */
    private List<String> dependsOn;

    /** 执行条件（SpEL 表达式，为空时无条件执行） */
    private String condition;

    /** 节点超时时间（秒，超时自动跳过） */
    private Integer timeoutSeconds;

    /** 失败策略：CONTINUE（继续后续节点）/ ABORT（终止整个流程）/ RETRY（重试本节点） */
    private String onFailure = "CONTINUE";
}
