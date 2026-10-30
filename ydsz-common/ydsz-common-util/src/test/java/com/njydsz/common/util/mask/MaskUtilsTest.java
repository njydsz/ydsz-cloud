package com.njydsz.common.util.mask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MaskUtils} 的行为钉住测试。
 *
 * <p>本类存在的理由：`MaskUtils.maskJson` 的唯一调用方是 `AuditAspect`（审计日志落库），
 * 而审计日志的读取面通常比业务数据更宽 —— 脱敏不生效等于把凭据写进审计库。
 * `ydsz-common-util` 此前零测试。
 *
 * <p>实测（修复前）：`maskJson("{\"password\":\"P@ssw0rd\"}")` **原样返回明文** ——
 * `DEFAULT_SENSITIVE_PATTERNS` 命中 `password` 后调用 `maskByFieldName`，而后者只处理
 * 手机/身份证/银行卡/邮箱/姓名，其余一律"原样返回"，于是 token/secret/password/sessionId
 * 这些凭据字段全部明文进入审计日志。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("MaskUtils：脱敏必须真的脱")
class MaskUtilsTest {

  @Nested
  @DisplayName("JSON 级脱敏（审计日志唯一入口）")
  class MaskJson {

    @Test
    @DisplayName("密码不得明文出现在脱敏结果里")
    void should_not_leak_password() {
      String masked = MaskUtils.maskJson("{\"username\":\"zhangsan\",\"password\":\"P@ssw0rd\"}");

      assertThat(masked).doesNotContain("P@ssw0rd");
    }

    @Test
    @DisplayName("token / secret / refreshToken / sessionId 等凭据字段整体替换为 ***")
    void should_replace_credential_fields_with_placeholder() {
      String json =
          "{\"token\":\"tk-1234567890\",\"clientSecret\":\"s3cr3t-value\","
              + "\"refreshToken\":\"rt-0987654321\",\"sessionId\":\"sess-abc\"}";

      String masked = MaskUtils.maskJson(json);

      assertThat(masked)
          .doesNotContain("tk-1234567890")
          .doesNotContain("s3cr3t-value")
          .doesNotContain("rt-0987654321")
          .doesNotContain("sess-abc");
      assertThat(masked).contains(MaskUtils.PLACEHOLDER);
    }

    @Test
    @DisplayName("PII 字段按专属策略脱敏，非敏感字段保持原样")
    void should_mask_pii_and_keep_other_fields() {
      String json =
          "{\"mobile\":\"13812345678\",\"email\":\"abc@qq.com\",\"nickname\":\"张三\",\"age\":30}";

      String masked = MaskUtils.maskJson(json);

      assertThat(masked).doesNotContain("13812345678").doesNotContain("abc@qq.com");
      assertThat(masked).contains("nickname").contains("30");
    }

    @Test
    @DisplayName("嵌套对象与数组内的敏感字段同样脱敏")
    void should_mask_nested_structures() {
      String json =
          "{\"data\":{\"password\":\"inner-pwd\"},\"list\":[{\"token\":\"list-token\"}]}";

      String masked = MaskUtils.maskJson(json);

      assertThat(masked).doesNotContain("inner-pwd").doesNotContain("list-token");
    }

    @Test
    @DisplayName("值为 null 时保持 null，不得变成字符串 \"null\"")
    void should_keep_null_value_as_null() {
      String masked = MaskUtils.maskJson("{\"password\":null,\"token\":null}");

      assertThat(masked).doesNotContain("\"null\"");
    }

    @Test
    @DisplayName("额外敏感词生效；解析失败时降级返回原文（有文档约定）")
    void should_apply_extra_patterns_and_degrade_on_broken_json() {
      assertThat(MaskUtils.maskJson("{\"innerKey\":\"v-123456\"}", Set.of("innerkey")))
          .doesNotContain("v-123456");

      String broken = "{not-json";
      assertThat(MaskUtils.maskJson(broken)).isEqualTo(broken);
    }
  }

  @Nested
  @DisplayName("单值脱敏")
  class SingleValue {

    @Test
    @DisplayName("手机号保留前 3 后 4，身份证保留前 3 后 4，银行卡保留前 4 后 4")
    void should_mask_specific_types() {
      assertThat(MaskUtils.maskPhone("13812345678")).isEqualTo("138****5678");
      assertThat(MaskUtils.maskIdCard("320102199001011234")).isEqualTo("320***********1234");
      assertThat(MaskUtils.maskBankCard("6222021234567890")).isEqualTo("6222********7890");
    }

    @Test
    @DisplayName("邮箱保留首字符与域名；姓名保留首字；null 透传")
    void should_mask_email_and_name() {
      assertThat(MaskUtils.maskEmail("abc@qq.com")).isEqualTo("a**@qq.com");
      assertThat(MaskUtils.maskName("张三丰")).isEqualTo("张**");
      assertThat(MaskUtils.maskName("李")).isEqualTo("李");
      assertThat(MaskUtils.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("通用 mask：前后缀之和覆盖整串时原样返回，否则中段替换")
    void should_mask_generic_text() {
      assertThat(MaskUtils.mask("13812345678", 3, 4)).isEqualTo("138****5678");
      assertThat(MaskUtils.mask("abc", 2, 2)).isEqualTo("abc");
      assertThat(MaskUtils.mask("", 1, 1)).isEmpty();
      assertThat(MaskUtils.mask(null, 1, 1)).isNull();
    }
  }
}
