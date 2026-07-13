package com.njydsz.pmis.agent.infra.guardrail;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.guardrail.GuardrailResult;
import com.njydsz.pmis.agent.domain.guardrail.OutputGuardrail;

/**
 * PII 脱敏输出护栏
 *
 * <p>对 LLM 输出中的个人身份信息（PII）进行脱敏：
 * <ul>
 *   <li>手机号：138****8888</li>
 *   <li>身份证号：3201**********1234</li>
 *   <li>邮箱：z***@example.com</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class PiiMaskingGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingGuardrail.class);

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @Override
    public GuardrailResult check(String output) {
        if (output == null || output.isBlank()) {
            return GuardrailResult.pass(output);
        }
        String sanitized = output;
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String phone = matchResult.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });
        sanitized = ID_CARD_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String id = matchResult.group();
            return id.substring(0, 4) + "**********" + id.substring(14);
        });
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String email = matchResult.group();
            int atIdx = email.indexOf('@');
            if (atIdx <= 1) {
                return email;
            }
            return email.charAt(0) + "***" + email.substring(atIdx);
        });
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
