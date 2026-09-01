package com.njydsz.literule.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 决策表请求 DTO（统一新增/修改）。
 *
 * <p>创建时 {@code id} 字段不传，更新时传入 {@code id}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DecisionTableDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 决策表 ID（更新时传入） */
  private Long id;

  /** 决策表编码，业务唯一 */
  private String tableCode;

  /** 决策表名称 */
  private String tableName;

  /** 决策表描述 */
  private String description;

  /** 分类编码 */
  private String category;

  /** 条件列定义列表 */
  private List<Map<String, Object>> conditionColumns;

  /** 动作列定义列表 */
  private List<Map<String, Object>> actionColumns;

  /** 决策行数据列表 */
  private List<Map<String, Object>> rows;

  /** 默认动作（无匹配行时执行） */
  private Map<String, Object> defaultActions;

  /** 命中策略（UNIQUE/FIRST/PRIORITY/COLLECT/ANY/RULE_ORDER） */
  private String hitPolicy;

  /** 是否启用 */
  private Boolean enabled;

  /** 优先级，数值越小优先级越高 */
  private Integer priority;

  /** 版本号 */
  private Integer version;
}
