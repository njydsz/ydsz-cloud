package com.njydsz.system.domain.port;

/**
 * 用户凭据校验端口（DDD 端口-适配器模式）。
 *
 * <p>定义系统管理模块调用用户中心密码校验能力的抽象契约，解除 server 层对 api 层 Feign 接口的直接依赖。
 * 具体实现在 infra 层（{@code UserCredentialFeignAdapter}），通过 Feign 跨服务调用完成。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface UserCredentialPort {

  /**
   * 校验用户明文密码是否正确。
   *
   * <p>调用用户中心服务完成密码校验，校验失败或用户不存在时返回 false；服务不可用时按降级策略返回 false。
   *
   * @param userId 用户 ID
   * @param password 明文密码（应由 HTTPS 传输）
   * @return true 表示密码正确；false 表示密码错误/用户不存在/服务不可用
   */
  boolean verifyPassword(String userId, String password);
}
