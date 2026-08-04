package com.njydsz.common.util.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UrlPathUtils} 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("UrlPathUtils URL 路径匹配测试")
class UrlPathUtilsTest {

    @Nested
    @DisplayName("matchAny")
    class MatchAnyTest {

        @Test
        @DisplayName("精确匹配")
        void exactMatch() {
            List<String> patterns = Collections.singletonList("/login");
            assertThat(UrlPathUtils.matchAny(patterns, "/login")).isTrue();
        }

        @Test
        @DisplayName("单层通配符 /*")
        void singleLevelWildcard() {
            List<String> patterns = Collections.singletonList("/actuator/*");
            assertThat(UrlPathUtils.matchAny(patterns, "/actuator/health")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/actuator/health/info")).isFalse();
        }

        @Test
        @DisplayName("多层通配符 /**")
        void multiLevelWildcard() {
            List<String> patterns = Collections.singletonList("/api/**");
            assertThat(UrlPathUtils.matchAny(patterns, "/api/users")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/api/users/123/posts")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/api/")).isTrue();
        }

        @Test
        @DisplayName("多个模式任一匹配")
        void multiplePatterns() {
            List<String> patterns = Arrays.asList("/login", "/register", "/public/**");
            assertThat(UrlPathUtils.matchAny(patterns, "/login")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/register")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/public/assets/app.js")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/private/secret")).isFalse();
        }

        @Test
        @DisplayName("空模式集合返回 false")
        void emptyPatterns() {
            assertThat(UrlPathUtils.matchAny(Collections.emptyList(), "/any")).isFalse();
        }

        @Test
        @DisplayName("null 模式集合返回 false")
        void nullPatterns() {
            assertThat(UrlPathUtils.matchAny(null, "/any")).isFalse();
        }

        @Test
        @DisplayName("null path 返回 false")
        void nullPath() {
            assertThat(UrlPathUtils.matchAny(Collections.singletonList("/api/**"), null)).isFalse();
        }

        @Test
        @DisplayName("空 path 返回 false")
        void emptyPath() {
            assertThat(UrlPathUtils.matchAny(Collections.singletonList("/api/**"), "")).isFalse();
        }

        @Test
        @DisplayName("集合中含 null 模式时不崩溃")
        void patternsWithNull() {
            List<String> patterns = Arrays.asList(null, "/login", null);
            assertThat(UrlPathUtils.matchAny(patterns, "/login")).isTrue();
            assertThat(UrlPathUtils.matchAny(patterns, "/other")).isFalse();
        }
    }

    @Nested
    @DisplayName("isIgnoreUrl")
    class IsIgnoreUrlTest {

        @Test
        @DisplayName("命中忽略清单")
        void hitIgnore() {
            List<String> ignoreUrls = Arrays.asList("/login", "/swagger/**");
            assertThat(UrlPathUtils.isIgnoreUrl(ignoreUrls, "/login")).isTrue();
            assertThat(UrlPathUtils.isIgnoreUrl(ignoreUrls, "/swagger/index.html")).isTrue();
        }

        @Test
        @DisplayName("未命中忽略清单")
        void missIgnore() {
            List<String> ignoreUrls = Collections.singletonList("/login");
            assertThat(UrlPathUtils.isIgnoreUrl(ignoreUrls, "/api/users")).isFalse();
        }

        @Test
        @DisplayName("null URL 返回 false")
        void nullUrl() {
            assertThat(UrlPathUtils.isIgnoreUrl(Collections.singletonList("/login"), null)).isFalse();
        }

        @Test
        @DisplayName("空忽略清单返回 false")
        void emptyIgnoreUrls() {
            assertThat(UrlPathUtils.isIgnoreUrl(Collections.emptyList(), "/login")).isFalse();
        }

        @Test
        @DisplayName("null 忽略清单返回 false")
        void nullIgnoreUrls() {
            assertThat(UrlPathUtils.isIgnoreUrl(null, "/login")).isFalse();
        }
    }
}
