package com.njydsz.gateway.filter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthGlobalFilter} 内部头签名密钥启动校验的单元测试。
 *
 * <p>该校验是网关身份链路的 fail-closed 开关：密钥缺失或强度不足时必须阻止启动，
 * 否则 {@code X-User-*} 透传头无法签名，下游无从区分网关注入与客户端伪造。
 *
 * <p>被测方法与两个 {@code @Value} 字段均为私有，此处用反射注入，不启动 Spring Context。
 */
@DisplayName("AuthGlobalFilter — 内部头签名密钥 fail-closed 校验")
class AuthGlobalFilterSecretTest {

  /** HMAC-SHA256 要求的最小密钥字节数，与被测类中的 MIN_INTERNAL_SECRET_LENGTH 同值。 */
  private static final int MIN_BYTES = 32;

  /** 恰好 32 字节（纯 ASCII）。 */
  private static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";

  /** 11 个汉字 = 33 字节，按字符数判定会误判为不足 32。 */
  private static final String SECRET_33_BYTES_CN = "云顶安全密钥测试字符串";

  /** 10 个汉字 = 30 字节。 */
  private static final String SECRET_30_BYTES_CN = "云顶安全密钥测试字符";

  private AuthGlobalFilter filter;
  private Method validateSecret;

  @BeforeEach
  void setUp() throws Exception {
    // validateSecret 只用两个 @Value 字段，三个构造依赖传 null 即可保持纯单测
    filter = new AuthGlobalFilter(null, null, null);
    validateSecret = AuthGlobalFilter.class.getDeclaredMethod("validateSecret");
    validateSecret.setAccessible(true);
  }

  private void givenSecrets(String internalSignSecret, String tokenSecretKey) throws Exception {
    setField("internalSignSecret", internalSignSecret);
    setField("tokenSecretKey", tokenSecretKey);
  }

  private void setField(String name, String value) throws Exception {
    Field field = AuthGlobalFilter.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(filter, value);
  }

  /** 反射调用并把 InvocationTargetException 拆包成真实异常，断言时按业务异常直接判定。 */
  private void invokeValidateSecret() {
    try {
      validateSecret.invoke(filter);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("validateSecret 非预期异常", cause);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("无法反射调用 validateSecret", e);
    }
  }

  @Nested
  @DisplayName("密钥缺失 —— 阻止启动")
  class MissingSecret {

    @Test
    @DisplayName("null 密钥拒绝启动")
    void nullSecret() throws Exception {
      givenSecrets(null, "jwt-secret");
      assertThatThrownBy(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("空串密钥拒绝启动")
    void emptySecret() throws Exception {
      givenSecrets("", "jwt-secret");
      assertThatThrownBy(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("纯空格密钥拒绝启动（旧 isEmpty 判不出，改 isBlank 才拦住）")
    void blankSecret() throws Exception {
      givenSecrets("        ", "jwt-secret");
      assertThatThrownBy(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not configured");
    }
  }

  @Nested
  @DisplayName("密钥强度 —— 按 UTF-8 字节数判定")
  class SecretStrength {

    @Test
    @DisplayName("31 字节拒绝")
    void belowMinBytes() throws Exception {
      givenSecrets(SECRET_32_BYTES.substring(0, MIN_BYTES - 1), "jwt-secret");
      assertThatThrownBy(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("less than " + MIN_BYTES)
          .hasMessageContaining("current byte length: " + (MIN_BYTES - 1));
    }

    @Test
    @DisplayName("32 字节放行")
    void atMinBytes() throws Exception {
      givenSecrets(SECRET_32_BYTES, "jwt-secret");
      assertThatCode(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("11 个汉字 = 33 字节，达标放行（旧按字符长度判定会误拒）")
    void multiByteMeetsMinBytes() throws Exception {
      assertThat(SECRET_33_BYTES_CN.getBytes(StandardCharsets.UTF_8))
          .hasSize(MIN_BYTES + 1);
      givenSecrets(SECRET_33_BYTES_CN, "jwt-secret");
      assertThatCode(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("10 个汉字 = 30 字节，不足 32 字节仍拒绝")
    void multiByteBelowMinBytes() throws Exception {
      assertThat(SECRET_30_BYTES_CN.getBytes(StandardCharsets.UTF_8))
          .hasSize(MIN_BYTES - 2);
      givenSecrets(SECRET_30_BYTES_CN, "jwt-secret");
      assertThatThrownBy(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("current byte length: " + (MIN_BYTES - 2));
    }
  }

  @Nested
  @DisplayName("密钥复用 —— 只告警不阻断")
  class SecretReuse {

    @Test
    @DisplayName("内部签名密钥与 JWT 密钥相同不阻断启动")
    void sameSecretOnlyWarns() throws Exception {
      givenSecrets(SECRET_32_BYTES, SECRET_32_BYTES);
      assertThatCode(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JWT 密钥缺省为空串时，内部签名密钥照常校验通过")
    void emptyTokenSecretDoesNotShortCircuit() throws Exception {
      givenSecrets(SECRET_32_BYTES, "");
      assertThatCode(AuthGlobalFilterSecretTest.this::invokeValidateSecret)
          .doesNotThrowAnyException();
    }
  }
}
