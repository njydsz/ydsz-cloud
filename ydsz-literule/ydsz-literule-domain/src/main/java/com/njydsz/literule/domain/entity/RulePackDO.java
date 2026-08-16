package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 规则集实体（P2-14）。
 *
 * <p>对应 {@code ydsz_rule_pack} 表，持久化规则集的版本、分类、标签及规则编码列表。 规则集是一组关联规则的逻辑集合，支持版本管理、行业分类、下载量统计与评分。
 *
 * <p>版本管理：每次发布生成新的 {@code packVersion}，同时通过 {@link #ruleSnapshots} 固化规则定义快照，保证历史版本内容可复现、可回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_pack")
public class RulePackDO extends MpBaseEntity<String> {

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
   * <p>发布该版本时，将 ruleCodes 对应的规则定义完整 JSON 列表固化存库， 保证知识包版本的"内容可复现"：回滚/安装某一历史版本时，可直接取用快照，
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

  // ==================== 领域行为方法 ====================

  /**
   * 判断规则集是否可用（启用且非空）。
   *
   * @return true 表示可用
   */
  public boolean isAvailable() {
    return Boolean.TRUE.equals(enabled);
  }

  /**
   * 判断是否为官方认证规则集。
   *
   * @return true 表示官方发布
   */
  public boolean isOfficial() {
    return Boolean.TRUE.equals(official);
  }

  /** 增加下载次数（原子操作安全由调用方保证）。 */
  public void incrementDownloadCount() {
    if (this.downloadCount == null) {
      this.downloadCount = 1L;
    } else {
      this.downloadCount++;
    }
  }

  /**
   * 更新评分（限制在 0-5 范围内）。
   *
   * @param newRating 新评分值
   * @throws IllegalArgumentException 当评分超出范围时
   */
  public void updateRating(BigDecimal newRating) {
    if (newRating == null
        || newRating.compareTo(BigDecimal.ZERO) < 0
        || newRating.compareTo(new BigDecimal("5")) > 0) {
      throw new IllegalArgumentException("评分必须在 0-5 之间: " + newRating);
    }
    this.rating = newRating;
  }

  /** 下架规则集。 */
  public void delist() {
    this.enabled = false;
  }

  /** 上架规则集。 */
  public void list() {
    this.enabled = true;
  }
}
