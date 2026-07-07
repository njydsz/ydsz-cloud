package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 规则集 DO（P2-14）
 */
@Data
@TableName("pmis_rule_pack")
public class RulePackDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String packCode;
    private String packVersion;
    private String packName;
    private String industry;
    private String tags;
    private String ruleCodes;

    /**
     * 规则定义快照（P2-8 知识包版本管理）
     *
     * <p>发布该版本时，将 ruleCodes 对应的规则定义完整 JSON 列表固化存库，
     * 保证知识包版本的"内容可复现"：回滚/安装某一历史版本时，可直接取用快照，
     * 而不依赖当时在线规则表的实时状态。格式：{@code List<RuleDefinition>} 的 JSON。
     */
    private String ruleSnapshots;

    /** 升级来源版本号（如回滚/升级时记录前一版本，便于审计链路） */
    private String previousVersion;

    private String description;
    private String author;
    private Long downloadCount;
    private BigDecimal rating;
    private Boolean enabled;
    private Boolean official;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
