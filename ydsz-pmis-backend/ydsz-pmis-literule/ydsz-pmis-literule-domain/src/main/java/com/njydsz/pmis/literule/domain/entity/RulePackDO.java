package com.njydsz.pmis.literule.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 规则集实体（P2-14）。
 *
 * <p>对应 {@code pmis_rule_pack} 表，持久化规则集的版本、分类、标签及规则编码列表。
 * 规则集是一组关联规则的逻辑集合，支持版本管理、行业分类、下载量统计与评分。
 *
 * <p>版本管理：每次发布生成新的 {@code packVersion}，同时通过 {@link #ruleSnapshots}
 * 固化规则定义快照，保证历史版本内容可复现、可回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-14)
 */
@Data
@TableName("pmis_rule_pack")
public class RulePackDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法字符串） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 规则集编码（全局唯一，用于版本间关联） */
    private String packCode;

    /** 规则集版本号（如 v1.0.0、v2.1.3） */
    private String packVersion;

    /** 规则集名称 */
    private String packName;

    /** 所属行业（如 FINANCE / MANUFACTURING / HEALTHCARE） */
    private String industry;

    /** 标签（JSON 数组，如 ["风控", "审批"]） */
    private String tags;

    /** 包含的规则编码列表（JSON 数组，逗号分隔） */
    private String ruleCodes;

    /**
     * 规则定义快照（P2-8 知识包版本管理）。
     *
     * <p>发布该版本时，将 ruleCodes 对应的规则定义完整 JSON 列表固化存库，
     * 保证知识包版本的"内容可复现"：回滚/安装某一历史版本时，可直接取用快照，
     * 而不依赖当时在线规则表的实时状态。格式：{@code List<RuleDefinition>} 的 JSON。
     */
    private String ruleSnapshots;

    /** 升级来源版本号（如回滚/升级时记录前一版本，便于审计链路） */
    private String previousVersion;

    /** 规则集描述 */
    private String description;

    /** 作者（创建人用户名） */
    private String author;

    /** 下载次数（安装时 +1） */
    private Long downloadCount;

    /** 评分（0-5，保留 1 位小数） */
    private BigDecimal rating;

    /** 是否启用（true=可用, false=已下架） */
    private Boolean enabled;

    /** 是否官方认证规则集（true=官方发布, false=社区贡献） */
    private Boolean official;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
