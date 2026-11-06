package com.njydsz.workflow.domain.vo;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.vo.ServiceNodeConfigVO.ServiceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServiceNodeConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/HTTP/SCRIPT/AUTO_PASS）、isHttpType/isScriptType/isAutoPassType、requiresExternalCall、fromExtJson 容错。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("服务节点配置值对象")
class ServiceNodeConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值（AUTO_PASS）")
    void nullMapUsesDefaults() {
      ServiceNodeConfigVO config = ServiceNodeConfigVO.fromExt(null);
      assertThat(config.getServiceType()).isEqualTo(ServiceType.AUTO_PASS);
      assertThat(config.getUrl()).isEmpty();
      assertThat(config.getMethod()).isEqualTo("GET");
      assertThat(config.getScript()).isEmpty();
    }

    @Test
    @DisplayName("解析 HTTP 类型")
    void httpType() {
      ServiceNodeConfigVO config =
          ServiceNodeConfigVO.fromExt(
              Map.of("serviceType", "HTTP", "url", "http://api.example.com", "method", "post"));
      assertThat(config.getServiceType()).isEqualTo(ServiceType.HTTP);
      assertThat(config.getUrl()).isEqualTo("http://api.example.com");
      assertThat(config.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("解析 SCRIPT 类型")
    void scriptType() {
      ServiceNodeConfigVO config =
          ServiceNodeConfigVO.fromExt(
              Map.of("serviceType", "SCRIPT", "script", "return 1;"));
      assertThat(config.getServiceType()).isEqualTo(ServiceType.SCRIPT);
      assertThat(config.getScript()).isEqualTo("return 1;");
    }

    @Test
    @DisplayName("非法类型回退 AUTO_PASS")
    void invalidTypeFallsBack() {
      ServiceNodeConfigVO config =
          ServiceNodeConfigVO.fromExt(Map.of("serviceType", "BOGUS"));
      assertThat(config.getServiceType()).isEqualTo(ServiceType.AUTO_PASS);
    }

    @Test
    @DisplayName("method 大小写转大写")
    void methodUpperCase() {
      ServiceNodeConfigVO config = ServiceNodeConfigVO.fromExt(Map.of("method", "put"));
      assertThat(config.getMethod()).isEqualTo("PUT");
    }
  }

  @Nested
  @DisplayName("类型判定")
  class TypeCheck {

    @Test
    @DisplayName("isHttpType")
    void isHttpType() {
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "HTTP")).isHttpType())
          .isTrue();
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "SCRIPT")).isHttpType())
          .isFalse();
    }

    @Test
    @DisplayName("isScriptType")
    void isScriptType() {
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "SCRIPT")).isScriptType())
          .isTrue();
    }

    @Test
    @DisplayName("isAutoPassType")
    void isAutoPassType() {
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "AUTO_PASS")).isAutoPassType())
          .isTrue();
    }

    @Test
    @DisplayName("requiresExternalCall：HTTP/SCRIPT 为 true，AUTO_PASS 为 false")
    void requiresExternalCall() {
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "HTTP")).requiresExternalCall())
          .isTrue();
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "SCRIPT")).requiresExternalCall())
          .isTrue();
      assertThat(
              ServiceNodeConfigVO.fromExt(Map.of("serviceType", "AUTO_PASS"))
                  .requiresExternalCall())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("fromExtJson 容错")
  class FromExtJson {

    @Test
    @DisplayName("非法 JSON 容错")
    void invalidJson() {
      assertThat(ServiceNodeConfigVO.fromExtJson("bad").getServiceType())
          .isEqualTo(ServiceType.AUTO_PASS);
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      ServiceNodeConfigVO config =
          ServiceNodeConfigVO.fromExtJson("{\"serviceType\":\"HTTP\",\"url\":\"http://x\"}");
      assertThat(config.getServiceType()).isEqualTo(ServiceType.HTTP);
      assertThat(config.getUrl()).isEqualTo("http://x");
    }
  }
}
