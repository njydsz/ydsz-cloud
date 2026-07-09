package com.njydsz.pmis.project.entity.ruleengine;

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
 * 规则测试用例实体
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Data
@TableName(value = "pmis_rule_test_case", autoResultMap = true)
public class RuleTestCaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 测试用例名称 */
    private String name;

    /** 关联规则编码（可选，null 表示通用测试用例） */
    private String ruleCode;

    /** 事实数据 JSON */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> factsData;

    /** 预期触发规则编码列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> expectedTriggered;

    /** 描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}