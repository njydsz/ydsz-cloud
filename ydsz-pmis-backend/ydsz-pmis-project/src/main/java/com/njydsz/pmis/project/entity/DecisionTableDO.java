package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 决策表实体
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Data
@TableName(value = "pmis_rule_decision_table", autoResultMap = true)
public class DecisionTableDO implements Serializable {

    private static final String serialVersionUID = "1";

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 决策表编码 */
    private String tableCode;

    /** 决策表名称 */
    private String tableName;

    /** 描述 */
    private String description;

    /** 类别 */
    private String category;

    /** 条件列定义 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> conditionColumns;

    /** 动作列定义 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> actionColumns;

    /** 决策行 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> rows;

    /** 默认动作 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultActions;

    /** 命中策略：UNIQUE/FIRST/PRIORITY/COLLECT/ANY，默认 FIRST */
    private String hitPolicy;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级 */
    private Integer priority;

    /** 版本 */
    private Integer version;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}