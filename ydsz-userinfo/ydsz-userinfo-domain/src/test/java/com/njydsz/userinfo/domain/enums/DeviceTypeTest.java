package com.njydsz.userinfo.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DeviceType} 单元测试。
 *
 * <p>覆盖：resolve 推断（X-Platform 优先 / User-Agent 模式匹配 / 兜底 WEB）、code 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("设备类型枚举")
class DeviceTypeTest {

  @Nested
  @DisplayName("resolve 设备推断")
  class Resolve {

    @ParameterizedTest
    @CsvSource({
        "web, WEB",
        "app, APP",
        "api, API",
        "unknown, UNKNOWN",
        "WEB, WEB",
        "App, APP",
        "API, API"
    })
    @DisplayName("X-Platform 请求头优先（大小写不敏感）")
    void platformHeaderWins(String platformHeader, DeviceType expected) {
      assertThat(DeviceType.resolve("Mozilla/5.0", platformHeader)).isEqualTo(expected);
    }

    @Test
    @DisplayName("无 X-Platform，User-Agent 含 Mobile 判定 APP")
    void userAgentMobile() {
      assertThat(DeviceType.resolve("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0)", null))
          .isEqualTo(DeviceType.APP);
    }

    @Test
    @DisplayName("User-Agent 含 Android 判定 APP")
    void userAgentAndroid() {
      assertThat(DeviceType.resolve("Mozilla/5.0 (Linux; Android 13)", null))
          .isEqualTo(DeviceType.APP);
    }

    @Test
    @DisplayName("User-Agent 含 okhttp 判定 APP")
    void userAgentOkHttp() {
      assertThat(DeviceType.resolve("okhttp/4.9.0", null)).isEqualTo(DeviceType.APP);
    }

    @Test
    @DisplayName("User-Agent 含 curl 判定 API")
    void userAgentCurl() {
      assertThat(DeviceType.resolve("curl/7.68.0", null)).isEqualTo(DeviceType.API);
    }

    @Test
    @DisplayName("User-Agent 含 Postman 判定 API")
    void userAgentPostman() {
      assertThat(DeviceType.resolve("PostmanRuntime/7.29.0", null)).isEqualTo(DeviceType.API);
    }

    @Test
    @DisplayName("User-Agent 含 Java/ 判定 API")
    void userAgentJava() {
      assertThat(DeviceType.resolve("Java/11.0.16", null)).isEqualTo(DeviceType.API);
    }

    @Test
    @DisplayName("无 X-Platform，普通浏览器 UA 兜底 WEB")
    void fallbackToWeb() {
      assertThat(DeviceType.resolve("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", null))
          .isEqualTo(DeviceType.WEB);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("两个参数都为空时兜底 WEB")
    void allEmptyFallbackWeb(String userAgent) {
      assertThat(DeviceType.resolve(userAgent, null)).isEqualTo(DeviceType.WEB);
    }

    @Test
    @DisplayName("X-Platform 未知值时回退 User-Agent 推断")
    void unknownPlatformFallsBackToUa() {
      assertThat(DeviceType.resolve("curl/7.68.0", "desktop")).isEqualTo(DeviceType.API);
    }

    @Test
    @DisplayName("X-Platform 未知且 UA 为空兜底 WEB")
    void unknownPlatformAndEmptyUaFallbackWeb() {
      assertThat(DeviceType.resolve(null, "desktop")).isEqualTo(DeviceType.WEB);
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 非空且小写")
    void allHaveLowerCaseCode() {
      for (DeviceType type : DeviceType.values()) {
        assertThat(type.getCode()).isNotBlank();
        assertThat(type.getCode()).isEqualTo(type.getCode().toLowerCase());
      }
    }
  }
}
