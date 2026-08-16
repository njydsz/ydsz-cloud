package com.njydsz.userinfo.web.controller;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.server.auth.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码 Controller
 *
 * <p>提供图形验证码的生成与校验能力，是用户登录流程的反机器人/防爆破第一道防线。 验证码通过 Redis 存储（key = captchaKey，TTL 默认 5 分钟），使用一次后立即失效。
 *
 * <p><b>接口路径：</b>{@code /api/v1/captcha}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>{@code GET /generate} — 生成图形验证码，返回 captchaKey + Base64 PNG
 *   <li>{@code POST /validate} — 校验用户输入的验证码（登录前置）
 * </ul>
 *
 * <p><b>典型流程：</b>
 *
 * <pre>
 *   1. 前端调用 /generate 获取 captchaKey + image
 *   2. 用户在 UI 输入图片中的字符
 *   3. 提交登录时携带 captchaKey + captcha，由 /validate 校验
 *   4. 登录接口（AuthController）可选择性要求 captchaKey/captcha 字段
 * </pre>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>captchaKey 由 UUID 生成（128 bit 熵），不可枚举
 *   <li>验证码一次性使用，校验成功后立即从 Redis 删除
 *   <li>/validate 接口启用 {@link Idempotent} 防重放
 *   <li>/validate 接口启用 {@link RateLimit} 限流 50 QPS（防暴力枚举）
 *   <li>校验失败不暴露具体原因（统一返回 false）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CaptchaService 验证码业务逻辑
 * @see com.njydsz.userinfo.web.controller.AuthController 认证 Controller（消费 captcha）
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
@Tag(name = "验证码", description = "图形验证码生成与校验")
public class CaptchaController {

  private final CaptchaService captchaService;

  /**
   * 生成图形验证码
   *
   * <p>生成一张随机字符的 PNG 图片（Base64 编码）和对应的 captchaKey。 验证码字符长度、字符集、干扰线、噪点等参数在 {@link CaptchaService}
   * 中配置。
   *
   * <p>captchaKey 由 UUID 生成（去除连字符），用于后续校验时定位 Redis 中的验证码。
   *
   * <p>该接口<b>无防护</b>（业务方可高频调用），但客户端应<b>避免重复生成</b>（每次生成覆盖前一次的 captchaKey）。
   *
   * @return 包含 captchaKey（用于校验）和 image（Base64 PNG）的 Map
   */
  @GetMapping("/generate")
  @Operation(summary = "生成验证码", description = "返回 Base64 PNG 图片和 captchaKey")
  public BaseResponse<Map<String, String>> generate() {
    String captchaKey = UUID.randomUUID().toString().replace("-", "");
    String imageBase64 = captchaService.generateCaptcha(captchaKey);
    return BaseResponse.success(
        Map.of(
            "captchaKey", captchaKey,
            "image", imageBase64));
  }

  /**
   * 校验图形验证码
   *
   * <p>幂等保护 5 秒；限流 50 QPS（防暴力枚举）。
   *
   * <p>校验通过后该 captchaKey 立即从 Redis 删除（一次性）。
   *
   * <p>失败时不区分「不存在」「已过期」「字符错误」，统一返回 false， 避免暴露内部状态给攻击者。
   *
   * @param captchaKey 生成时返回的验证码标识
   * @param captcha 用户输入的验证码字符
   * @return true=校验通过；false=校验失败
   */
  @RateLimit(resource = "userinfo.captcha.validate", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:CaptchaController:validate:lock", ttlSeconds = 5)
  @PostMapping("/validate")
  @Operation(summary = "校验验证码", description = "校验用户输入的验证码")
  public BaseResponse<Boolean> validate(
      @RequestParam String captchaKey, @RequestParam String captcha) {
    return BaseResponse.success(captchaService.validate(captchaKey, captcha));
  }
}
