package com.njydsz.pmis.workflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部署流程 DTO
 */
@Data
public class DeployProcessDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程名称（部署名） */
    private String name;

    /** 流程分类 */
    private String category;

    /** BPMN XML 内容 */
    private String bpmnXml;

    /** 流程定义 KEY（可选，解析自 XML） */
    private String processKey;

    /** 租户 ID */
    private Long tenantId;
}
