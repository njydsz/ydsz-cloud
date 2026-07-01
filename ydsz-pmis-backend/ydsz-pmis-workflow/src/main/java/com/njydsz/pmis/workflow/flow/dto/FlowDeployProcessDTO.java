package com.njydsz.pmis.workflow.flow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 自建工作流引擎 - 部署流程 DTO
 *
 * <p>支持两种部署模式：
 * <ul>
 *   <li>BPMN 2.0 模式：传入 {@code bpmnXml}（标准 BPMN XML），由 BpmnXmlParser 自动解析为节点/跳转</li>
 *   <li>轻量 JSON 模式：直接传入 {@code nodes} + {@code skips} 数组（对标 Warm-Flow）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FlowDeployProcessDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程编码 */
    @NotBlank(message = "flowCode 不能为空")
    private String flowCode;

    /** 流程名称 */
    @NotBlank(message = "flowName 不能为空")
    private String flowName;

    /** 流程版本 */
    private String version;

    /** 流程类别 */
    private String category;

    /** 流程描述 */
    private String description;

    /** 审批表单路径 */
    private String formPath;

    /**
     * BPMN 2.0 XML 内容（标准 .bpmn20.xml 文件内容）
     *
     * <p>与 {@code nodes}/{@code skips} 二选一。优先使用 BPMN XML 模式。
     */
    private String bpmnXml;

    /**
     * 轻量节点列表（JSON 模式）
     */
    private List<FlowNodeDTO> nodes;

    /**
     * 轻量跳转列表（JSON 模式）
     */
    private List<FlowSkipDTO> skips;

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;

    /** 节点定义 */
    @Data
    public static class FlowNodeDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 节点编码（流程内唯一） */
        private String nodeCode;
        /** 节点名称 */
        private String nodeName;
        /** 节点类型：0开始/1审批/2抄送/3条件/4并行/5包容/6结束/7子流程 */
        private Integer nodeType;
        /** 办理人权限标识 */
        private String permissionFlag;
        /** 会签类型 */
        private String performType;
        /** 任意跳转目标节点 */
        private String skipAnyNode;
    }

    /** 跳转定义 */
    @Data
    public static class FlowSkipDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 源节点编码 */
        private String fromNodeCode;
        /** 目标节点编码 */
        private String toNodeCode;
        /** 跳转类型：PASS/REJECT */
        private String skipType;
        /** 跳转条件 */
        private String skipCondition;
        /** 跳转名称（线上标签） */
        private String skipName;
    }
}
