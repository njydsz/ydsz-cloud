package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部接口调用保护配置属性（P0-6）。
 *
 * <p>为 {@code /api/internal/**} 端点提供服务端二次校验能力，作为网关白名单之外的最后一道防线：
 * 即使网关路由配置错误，缺少内部调用标记的外部请求也会在服务端被拒绝。
 *
 * <p><b>渐进式启用说明：</b>
 *
 * <ul>
 *   <li>{@code enabled=false}（默认）：仅网关白名单生效，兼容尚未注入 {@code X-Internal-Call} 头的存量 Feign 客户端
 *   <li>{@code enabled=true}：所有 {@code /api/internal/**} 请求必须携带 {@code X-Internal-Call: true} 请求头，
 *       由 Feign 拦截器统一注入（调用方需在各业务模块 Feign Client 配置该头）
 * </ul>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     internal-call:
 *       enabled: false
 *       header-name: X-Internal-Call
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.internal-call")
public class InternalCallProperties {

  /** 默认内部调用标记头名称 */
  private static final String DEFAULT_HEADER_NAME = "X-Internal-Call";

  /** 是否启用服务端二次校验（默认关闭，渐进式启用） */
  private boolean enabled = false;

  /** 内部调用标记头名称（默认 X-Internal-Call） */
  private String headerName = DEFAULT_HEADER_NAME;
}
