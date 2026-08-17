package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.util.Random;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;

/**
 * 验证码服务（手机验证码发送与校验）。
 *
 * <p>用于自助注册/找回密码场景。验证码存储在 Redis，TTL 5 分钟，发送频率限制 60 秒。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:verifycode:{type}:{phone}  →  "123456"   验证码，TTL 5 分钟
 *   userinfo:verifycode:limit:{phone}   →  "1"        发送频率限制标记，TTL 60 秒
 * </pre>
 *
 * <p><b>注意：</b>当前实现生成随机 6 位数作为验证码，未实际发送短信。 生产环境需集成 SNS 短信服务（如阿里云 SMS、腾讯云 SMS）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeService {

  /** 验证码 Redis Key 前缀 */
  private static final String VERIFY_CODE_KEY_PREFIX = "userinfo:verifycode:";

  /** 发送频率限制标记前缀 */
  private static final String VERIFY_CODE_LIMIT_PREFIX = "userinfo:verifycode:limit:";

  /** 验证码有效期（5 分钟） */
  private static final Duration CODE_TTL = Duration.ofMinutes(5);

  /** 发送频率限制间隔（60 秒） */
  private static final Duration LIMIT_TTL = Duration.ofSeconds(60);

  /** 验证码长度 */
  private static final int CODE_LENGTH = 6;

  private final RedisStringOps redisStringOps;
  private final Random random = new Random();

  /**
   * 发送验证码（生成并存储到 Redis，未实际发送短信）。
   *
   * @param type 类型：REGISTER / FORGOT_PASSWORD
   * @param phone 目标手机号
   * @throws BusinessException 发送过于频繁时抛出
   */
  public void sendCode(String type, String phone) {
    // 检查发送频率限制
    String limitKey = VERIFY_CODE_LIMIT_PREFIX + phone;
    try {
      String limitFlag = redisStringOps.get(limitKey, String.class);
      if (limitFlag != null) {
        throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_RATE_LIMITED);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("读取验证码频率限制异常: phone={}, error={}", phone, e.getMessage());
    }

    // 生成 6 位随机验证码
    String code = generateCode();

    // 存储到 Redis，TTL 5 分钟
    String codeKey = VERIFY_CODE_KEY_PREFIX + type + ":" + phone;
    try {
      redisStringOps.set(codeKey, code, CODE_TTL);
      redisStringOps.set(limitKey, "1", LIMIT_TTL);
      log.info("验证码已生成: type={}, phone={}, code={}", type, phone, code);
    } catch (Exception e) {
      log.error("存储验证码异常: type={}, phone={}, error={}", type, phone, e.getMessage());
      throw new BusinessException(UserInfoExceptionCode.VERIFY_CODE_INVALID);
    }
  }

  /**
   * 校验验证码是否正确（校验后无论成功与否均清除）。
   *
   * @param type 类型：REGISTER / FORGOT_PASSWORD
   * @param phone 手机号
   * @param code 待校验的验证码
   * @return true 验证通过；false 验证失败
   */
  public boolean verifyCode(String type, String phone, String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    String codeKey = VERIFY_CODE_KEY_PREFIX + type + ":" + phone;
    try {
      String storedCode = redisStringOps.get(codeKey, String.class);
      if (storedCode == null) {
        log.warn("验证码不存在或已过期: type={}, phone={}", type, phone);
        return false;
      }
      boolean matched = storedCode.equals(code);
      if (matched) {
        // 验证成功，清除验证码（一次性使用）
        redisStringOps.del(codeKey);
      }
      return matched;
    } catch (Exception e) {
      log.warn("校验验证码异常: type={}, phone={}, error={}", type, phone, e.getMessage());
      return false;
    }
  }

  /**
   * 生成 6 位数字验证码。
   *
   * @return 6 位数字字符串
   */
  private String generateCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(random.nextInt(10));
    }
    return sb.toString();
  }
}
