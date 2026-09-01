package com.njydsz.agent.infra.guardrail;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.guardrail.GuardrailResult;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * PII 脱敏输出护栏（P1-3 优化）。
 *
 * <p>对 LLM 输出中的个人身份信息（PII）进行脱敏。委托 {@link SensitiveUtil#scanAndMask(String)} 完成统一的 PII 识别与脱敏。
 *
 * <p><b>覆盖类型：</b>
 *
 * <ul>
 *   <li>手机号：委托 {@link SensitiveUtil} 标准脱敏（前3后4，中间星号）
 *   <li>身份证号：委托 {@link SensitiveUtil} 标准脱敏（前3后5，中间8位星号）
 *   <li>邮箱：委托 {@link SensitiveUtil} 标准脱敏（首尾字符保留，中间星号）
 *   <li>银行卡号：委托 {@link SensitiveUtil} 标准脱敏（后4位保留，其余星号）
 *   <li>护照号：委托 {@link SensitiveUtil} 标准脱敏（前2后2保留，中间星号）
 * </ul>
 *
 * <p><b>P1-3 重构说明：</b>
 *
 * <ul>
 *   <li>原实现中 5 个 PII 正则（PHONE/ID_CARD/EMAIL/BANK_CARD/PASSPORT）在本类独立定义
 *   <li>现统一委托 {@link SensitiveUtil#scanAndMask(String)}，与 workflow 模块、common-safe 模块 共享同一套 PII
 *       扫描正则，消除三处重复定义
 *   <li>升级 PII 类型或调整正则只需修改 {@link SensitiveUtil} 一处
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class PiiMaskingGuardrail implements OutputGuardrail {

  @Override
  public GuardrailResult check(String output) {
    if (output == null || output.isBlank()) {
      return GuardrailResult.pass(output);
    }
    // P1-3: 委托 SensitiveUtil.scanAndMask() 统一扫描+脱敏
    String sanitized = SensitiveUtil.scanAndMask(output);
    if (!sanitized.equals(output)) {
      log.info("[Guardrail] PII 脱敏处理完成");
    }
    return GuardrailResult.pass(output, sanitized);
  }

  @Override
  public String getName() {
    return "pii-masking";
  }

  @Override
  public int getPriority() {
    return 10;
  }
}
