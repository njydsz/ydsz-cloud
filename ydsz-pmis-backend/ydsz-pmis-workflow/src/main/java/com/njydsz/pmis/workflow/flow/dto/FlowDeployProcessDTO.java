package com.njydsz.pmis.workflow.flow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 自建工作流引擎 - 部署流程 DTO
 *
 * <p>使用 JSON 描述流程图（不依赖 BPMN XML），对标 Warm-Flow 的轻量模型。
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

    /** 节点列表 */
    private List<FlowNodeDTO> nodes;

    /** 跳转列表 */
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
