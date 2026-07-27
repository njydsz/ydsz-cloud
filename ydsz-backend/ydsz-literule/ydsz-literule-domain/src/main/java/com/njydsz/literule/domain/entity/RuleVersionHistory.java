package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * LiteRule 规则版本历史 DO
 *
 * <p>映射 ydsz_rule_version_history 表，存储规则变更的版本快照。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_version_history")
public class RuleVersionHistory extends MpBaseEntity<String> {

    private String ruleCode;
    private Integer version;
    private String definitionJson;
    private String changeDesc;
    private String operator;
}
