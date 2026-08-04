package com.remisoft.literule.domain.entity;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;
import com.remisoft.common.jdbc.handler.JsonTypeHandler;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则测试用例实体
 *
 * @author remi
 * @since 2026-07-02
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "remi_rule_test_case", autoResultMap = true)
public class RuleTestCaseDO extends MpBaseEntity<String> {

    /** 测试用例名称 */
    private String name;

    /** 关联规则编码（可选，null 表示通用测试用例） */
    private String ruleCode;

    /** 事实数据 JSON */
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> factsData;

    /** 预期触发规则编码列表 */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> expectedTriggered;

    /** 描述 */
    private String description;
}
