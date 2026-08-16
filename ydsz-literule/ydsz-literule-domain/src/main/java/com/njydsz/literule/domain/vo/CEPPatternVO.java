package com.njydsz.literule.domain.vo;

import java.time.Duration;
import java.util.List;

import lombok.Data;

/**
 * CEP（复杂事件处理）模式视图对象（VO）。
 *
 * <p>用于前端配置与展示复杂事件模式，支持计数窗口、滑动窗口、 会话窗口、序列模式等多种类型，并可设置阈值与聚合方式。 与后端 {@code CEPPattern}
 * 领域对象对应，仅承载展示所需字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CEPPatternVO {

  /** 模式 ID（业务唯一标识） */
  private String id;

  /** 模式类型（如 COUNT/SEQUENCE/SESSION，决定匹配语义） */
  private String type;

  /** 关联规则编码 */
  private String ruleCode;

  /** 模式名称（展示用） */
  private String name;

  /** 时间窗口长度（滑动/滚动窗口大小） */
  private Duration window;

  /** 滑动步长（滑动窗口每次向前推进的间隔） */
  private Duration slide;

  /** 窗口类型（TUMBLING 滚动 / SLIDING 滑动 / SESSION 会话） */
  private String windowType;

  /** 会话窗口最大间隔（超过该间隔则会话断开重新计数） */
  private Duration sessionGap;

  /** 触发阈值（如次数或聚合值达到该值才命中） */
  private double threshold;

  /** 关注的事件类型（模式只匹配该类型事件） */
  private String eventType;

  /** 事件过滤表达式（对事件附加条件过滤） */
  private String filter;

  /** 聚合函数（如 COUNT/SUM/AVG，用于窗口内计算） */
  private String aggregateFunction;

  /** 聚合字段（聚合函数作用的事件字段） */
  private String aggregateField;

  /** 序列事件定义（有序事件列表，仅 SEQUENCE 类型使用） */
  private List<Object> sequence;

  /** 模式描述 */
  private String description;

  /** 排序号（数值越小越优先，用于多模式展示顺序） */
  private int order;

  /** 序列事件最小间隔（相邻事件间隔下限） */
  private Duration minGap;

  /** 序列事件最大间隔（相邻事件间隔上限） */
  private Duration maxGap;
}
