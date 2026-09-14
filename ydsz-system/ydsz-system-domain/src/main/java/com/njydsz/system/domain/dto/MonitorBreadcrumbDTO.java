package com.njydsz.system.domain.dto;

import java.util.Map;

import lombok.Data;

/**
 * 前端错误面包屑 DTO
 *
 * <p>对应 ydsz-micro 中 {@code @ydsz/monitor} 的 Breadcrumb 结构，记录错误发生前的
 * 用户行为轨迹（前端为环形缓冲，最多保留 30 条）。字段全部为前端可选项，
 * 服务端不做非空约束，缺失时按空处理。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Data
public class MonitorBreadcrumbDTO {

  /** 采集时间戳（epoch 毫秒） */
  private Long timestamp;

  /** 面包屑类别：ui / navigation / http / log / user */
  private String category;

  /** 级别：debug / info / warning / error */
  private String level;

  /** 消息文本，例如“点击按钮：提交” */
  private String message;

  /** 附加数据（键值对，由前端自定义，服务端不解析其内部结构） */
  private Map<String, Object> data;
}
