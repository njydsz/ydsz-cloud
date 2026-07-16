package com.njydsz.literule.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 规则链画布 DO（P0-1）
 *
 * <p>对应 ydsz_rule_chain_graph 表，存储可视化编排画布的完整 JSON 内容。
 * 一条规则对应一条画布记录，画布版本号独立递增，与规则版本号解耦。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Data
@TableName("ydsz_rule_chain_graph")
public class RuleChainGraphDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 关联规则编码（一对一） */
    private String ruleCode;

    /** 画布名称 */
    private String name;

    /** 画布描述 */
    private String description;

    /** 适用场景（与 RuleContext.scenario 对应） */
    private String scenario;

    /** 租户 ID（多租户隔离，默认 1） */
    private String tenantId;

    /** 画布版本号（独立递增） */
    private Integer graphVersion;

    /** 画布状态：DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    /** 画布内容 JSON（包含 nodes/edges/viewport/metadata） */
    private String contentJson;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
