package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LiteRule 规则版本历史 DO
 *
 * <p>映射 pmis_rule_version_history 表，存储规则变更的版本快照。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_rule_version_history")
public class RuleVersionHistoryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;
    private Integer version;
    private String definitionJson;
    private String changeDesc;
    private String operator;
    private LocalDateTime createdAt;
}
