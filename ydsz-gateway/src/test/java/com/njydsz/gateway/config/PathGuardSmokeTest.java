package com.njydsz.gateway.config;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 {@link PathGuard} 的路径安全净化逻辑：
 * <ul>
 *   <li>sanitize: 路径穿越 / 反斜杠 / 双斜杠 / null 字节 / 混合编码拦截</li>
 *   <li>matchWhiteList: 精确匹配 + 大小写不敏感匹配</li>
 * </ul>
 *
 * <p>纯计算、无外部依赖（DB/Redis），直接调用静态方法即可。
 */
class PathGuardSmokeTest {

    @Nested
    @DisplayName("sanitize - 路径安全净化")
    class SanitizeTests {

        @Test
        @DisplayName("正常路径原样返回")
        void normalPath_returnsAsIs() {
            assertThat(PathGuard.sanitize("/api/v1/users")).isEqualTo("/api/v1/users");
        }

        @Test
        @DisplayName("空路径和 null 原样返回")
        void emptyOrNullPath_returnsAsIs() {
            assertThat(PathGuard.sanitize("")).isEmpty();
            //noinspection ConstantConditions
            assertThat(PathGuard.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("路径穿越攻击 ../ 应返回 null")
        void pathTraversal_returnsNull() {
            assertThat(PathGuard.sanitize("/api/../etc/passwd")).isNull();
            assertThat(PathGuard.sanitize("..\\windows\\system32")).isNull();
        }

        @Test
        @DisplayName("URL 编码穿越 %2e%2e 应返回 null")
        void encodedTraversal_returnsNull() {
            assertThat(PathGuard.sanitize("/api/%2e%2e/etc/passwd")).isNull();
            assertThat(PathGuard.sanitize("/static/%2e./secret")).isNull();
        }

        @Test
        @DisplayName("反斜杠和 URL 编码反斜杠应返回 null")
        void backslash_returnsNull() {
            assertThat(PathGuard.sanitize("\\etc\\passwd")).isNull();
            assertThat(PathGuard.sanitize("/api%5csecret")).isNull();
        }

        @Test
        @DisplayName("双斜杠应返回 null")
        void doubleSlash_returnsNull() {
            assertThat(PathGuard.sanitize("//etc/passwd")).isNull();
        }

        @Test
        @DisplayName("null 字节 %00 应返回 null")
        void nullByte_returnsNull() {
            assertThat(PathGuard.sanitize("/api/%00evil")).isNull();
        }

        @Test
        @DisplayName("混合编码 .%2f 应返回 null")
        void mixedEncoding_returnsNull() {
            assertThat(PathGuard.sanitize("/api.%2fsecret")).isNull();
            assertThat(PathGuard.sanitize("/api%2e%5csecret")).isNull();
        }

        @Test
        @DisplayName("双重编码 %252e%252e 经递归解码后应返回 null")
        void doubleEncoding_returnsNull() {
            // %25 解码为 %, 所以 %252e%252e → %2e%2e → .. → 穿越
            assertThat(PathGuard.sanitize("/api/%252e%252e/etc/passwd")).isNull();
        }
    }

    @Nested
    @DisplayName("matchWhiteList - 白名单匹配")
    class MatchWhiteListTests {

        @Test
        @DisplayName("精确匹配白名单成功")
        void exactMatch_succeeds() {
            Set<String> whitelist = PathGuard.whiteList("/api/health", "/api/login");
            assertThat(PathGuard.matchWhiteList("/api/health", whitelist)).isTrue();
            assertThat(PathGuard.matchWhiteList("/api/login", whitelist)).isTrue();
        }

        @Test
        @DisplayName("大小写不敏感匹配成功")
        void caseInsensitiveMatch_succeeds() {
            Set<String> whitelist = PathGuard.whiteList("/api/Health");
            assertThat(PathGuard.matchWhiteList("/api/health", whitelist)).isTrue();
            assertThat(PathGuard.matchWhiteList("/API/HEALTH", whitelist)).isTrue();
        }

        @Test
        @DisplayName("不在白名单中返回 false")
        void notInWhitelist_returnsFalse() {
            Set<String> whitelist = PathGuard.whiteList("/api/health");
            assertThat(PathGuard.matchWhiteList("/api/admin", whitelist)).isFalse();
        }

        @Test
        @DisplayName("空路径或空白名单返回 false")
        void nullOrEmpty_returnsFalse() {
            assertThat(PathGuard.matchWhiteList(null, Set.of())).isFalse();
            assertThat(PathGuard.matchWhiteList("/api/health", null)).isFalse();
            assertThat(PathGuard.matchWhiteList("/api/health", Set.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("internalHeaders - 内部头集合")
    class InternalHeadersTests {

        @Test
        @DisplayName("内部头集合包含预期的敏感头名称")
        void internalHeaders_containsExpectedHeaders() {
            Set<String> headers = PathGuard.internalHeaders();
            assertThat(headers)
                    .contains("X-User-Id", "X-Username", "X-Internal-Sig",
                            "X-Internal-Nonce", "X-Forwarded-For", "X-Real-IP");
        }

        @Test
        @DisplayName("内部头集合非空")
        void internalHeaders_isNotEmpty() {
            assertThat(PathGuard.internalHeaders()).isNotEmpty();
        }
    }
}
