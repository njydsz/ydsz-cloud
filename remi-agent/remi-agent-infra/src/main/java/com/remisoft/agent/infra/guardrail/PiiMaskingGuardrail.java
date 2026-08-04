package com.remisoft.agent.infra.guardrail;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.agent.domain.guardrail.GuardrailResult;
import com.remisoft.agent.domain.guardrail.OutputGuardrail;
import com.remisoft.common.safe.sensitive.SensitiveUtil;

/**
 * PII 脱敏输出护栏
 *
 * <p>对 LLM 输出中的个人身份信息（PII）进行脱敏。识别阶段使用正则表达式从自由文本中
 * 定位 PII 片段，脱敏阶段委托 {@link SensitiveUtil} 统一处理，确保与全系统脱敏规则一致。
 *
 * <ul>
 *   <li>手机号：委托 {@link SensitiveUtil#maskPhone(String)}（前3后4，中间星号）</li>
 *   <li>身份证号：委托 {@link SensitiveUtil#idCard(String, char)}（前3后5，中间8位星号）</li>
 *   <li>邮箱：委托 {@link SensitiveUtil#maskEmail(String)}（首尾字符保留，中间星号）</li>
 *   <li>银行卡号：委托 {@link SensitiveUtil#bankCard(String, char)}（后4位保留，其余星号）</li>
 *   <li>护照号：委托 {@link SensitiveUtil#passport(String, char)}（前2后2保留，中间星号）</li>
 * </ul>
 *
 * <p>本类仅负责「在自由文本中识别 PII 片段」，脱敏算法本身不在此处实现，
 * 避免与 common-safe 的脱敏规则产生二义性。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class PiiMaskingGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingGuardrail.class);

    /** 手机号识别：1[3-9] 开头 + 9 位数字，前后非数字边界 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    /** 身份证号识别：17 位数字 + 1 位数字/大小写 X */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    /** 邮箱识别：标准 email 正则 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    /** 银行卡号识别：15~18 位数字，首位非 0 */
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("(?<!\\d)[1-9]\\d{14,17}(?!\\d)");
    /** 护照号识别：G/E/K/S 开头 + 8 位数字 */
    private static final Pattern PASSPORT_PATTERN =
            Pattern.compile("(?<![A-Z])[GEKS][1-9]\\d{7}(?!\\d)");

    private static final char ASTERISK = '*';

    @Override
    public GuardrailResult check(String output) {
        if (output == null || output.isBlank()) {
            return GuardrailResult.pass(output);
        }
        String sanitized = output;
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(
                matchResult -> SensitiveUtil.maskPhone(matchResult.group()));
        sanitized = ID_CARD_PATTERN.matcher(sanitized).replaceAll(
                matchResult -> SensitiveUtil.idCard(matchResult.group(), ASTERISK));
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(
                matchResult -> SensitiveUtil.maskEmail(matchResult.group()));
        sanitized = BANK_CARD_PATTERN.matcher(sanitized).replaceAll(
                matchResult -> SensitiveUtil.bankCard(matchResult.group(), ASTERISK));
        sanitized = PASSPORT_PATTERN.matcher(sanitized).replaceAll(
                matchResult -> SensitiveUtil.passport(matchResult.group(), ASTERISK));
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
