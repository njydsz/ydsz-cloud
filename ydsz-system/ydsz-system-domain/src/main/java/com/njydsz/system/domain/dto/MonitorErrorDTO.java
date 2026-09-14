package com.njydsz.system.domain.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 前端错误上报条目 DTO
 *
 * <p>对应 ydsz-micro 中 {@code @ydsz/monitor} 的 ErrorReport 结构。前端经
 * {@code navigator.sendBeacon} 批量上报（降级通道为 keepalive fetch）到
 * {@code POST /monitor/error}，字段命名与前端契约保持 camelCase 一致。
 *
 * <p><strong>容量约束：</strong>单条报文的 {@code stack} 与 {@code message}
 * 已设置长度上限，防止异常客户端提交超大报文导致内存与存储压力。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Data
public class MonitorErrorDTO {

  /** 错误类型：vue（Vue 组件异常）/ window（全局异常）/ promise（未处理拒绝）/ resource（资源加载失败） */
  private String type;

  /** 错误消息（截断上限 4000 字符） */
  @Size(max = 4000, message = "错误消息长度超出上限")
  private String message;

  /** 错误堆栈（截断上限 20000 字符，后端结合 sourcemap 做符号化） */
  @Size(max = 20000, message = "错误堆栈长度超出上限")
  private String stack;

  /** 出错脚本文件路径 */
  private String filename;

  /** 出错行号（1 起） */
  private Integer lineno;

  /** 出错列号（1 起） */
  private Integer colno;

  /** 出错页面 URL */
  private String url;

  /** 采集时间戳（epoch 毫秒） */
  private Long timestamp;

  /** 浏览器 User-Agent */
  private String userAgent;

  /** 应用版本号 */
  private String appVersion;

  /** 用户标识（脱敏由前端 beforeSend 钩子负责） */
  private String userId;

  /** 错误发生时的前端路由路径 */
  private String route;

  /** 会话 ID（单次页面生命周期唯一） */
  private String sessionId;

  /** 错误追踪 ID（单条错误唯一，便于与后端 traceId 关联） */
  private String traceId;

  /** 发布版本（commit hash），用于关联已上传的 sourcemap */
  private String release;

  /** 错误发生前的用户行为面包屑（前端最多 30 条） */
  private List<MonitorBreadcrumbDTO> breadcrumbs;

  /** 附加信息（前端自定义键值对） */
  private Map<String, Object> extra;
}
