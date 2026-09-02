package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则包视图对象（VO）。
 *
 * <p>用于 Controller 层返回规则包的完整信息。规则包是行业级规则集合的封装， 支持版本管理、规则快照、评分和下载统计，实现规则的复用与共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RulePackVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则包唯一标识（主键） */
  private String id;

  /** 规则包编码，业务唯一 */
  private String packCode;

  /** 规则包版本号 */
  private String packVersion;

  /** 规则包名称 */
  private String packName;

  /** 所属行业（如 finance/ecommerce/healthcare） */
  private String industry;

  /** 标签，逗号分隔 */
  private String tags;

  /** 包含的规则编码列表，逗号分隔 */
  private String ruleCodes;

  /** 规则快照 JSON，保存发布时的规则定义副本 */
  private String ruleSnapshots;

  /** 前一版本号 */
  private String previousVersion;

  /** 规则包描述 */
  private String description;

  /** 作者 */
  private String author;

  /** 下载次数 */
  private Long downloadCount;

  /** 评分（0~5） */
  private BigDecimal rating;

  /** 是否启用 */
  private Boolean enabled;

  /** 是否为官方包 */
  private Boolean official;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
