package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PathGuard} 单元测试
 *
 * <p>覆盖路径穿越防护、白名单精确匹配、内部头集合等核心安全逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PathGuard 路径安全工具测试")
class PathGuardTest {

    // ==================== sanitize ====================

    @Nested
    @DisplayName("sanitize() 路径规范化")
    class SanitizeTest {

        @Test
        @DisplayName("正常路径应原样返回")
        void shouldReturnNormalPath() {
            String result = PathGuard.sanitize("/auth/login");
            assertEquals("/auth/login", result);
        }

        @Test
        @DisplayName("合并连续斜杠")
        void shouldCollapseSlashes() {
            String result = PathGuard.sanitize("//auth///login");
            assertEquals("/auth/login", result);
        }

        @Test
        @DisplayName("null 路径返回 null")
        void shouldReturnNullForNullPath() {
            assertNull(PathGuard.sanitize(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void shouldReturnNullForEmptyPath() {
            assertNull(PathGuard.sanitize(""));
        }

        @Test
        @DisplayName("包含 .. 的路径返回 null（路径穿越攻击拦截）")
        void shouldRejectPathTraversalWithDoubleDots() {
            assertNull(PathGuard.sanitize("/auth/../users/list"));
        }

        @Test
        @DisplayName("根路径 .. 返回 null")
        void shouldRejectRootTraversal() {
            assertNull(PathGuard.sanitize("/.."));
        }

        @Test
        @DisplayName("多层 .. 返回 null")
        void shouldRejectMultipleTraversal() {
            assertNull(PathGuard.sanitize("/a/b/../../c"));
        }

        @Test
        @DisplayName("单层 . 路径应被规范化")
        void shouldNormalizeSingleDot() {
            String result = PathGuard.sanitize("/auth/./login");
            assertEquals("/auth/login", result);
        }

        @Test
        @DisplayName("不带前导斜杠的路径返回 null")
        void shouldRejectNonAbsolutePath() {
            assertNull(PathGuard.sanitize("auth/login"));
        }
    }

    // ==================== matchWhiteList ====================

    @Nested
    @DisplayName("matchWhiteList() 白名单匹配")
    class MatchWhiteListTest {

        private final Set<String> whiteList = PathGuard.whiteList(
                "/auth/login",
                "/auth/refresh",
                "/health"
        );

        @Test
        @DisplayName("精确匹配白名单路径返回 true")
        void shouldMatchExactPath() {
            assertTrue(PathGuard.matchWhiteList("/auth/login", whiteList));
            assertTrue(PathGuard.matchWhiteList("/health", whiteList));
        }

        @Test
        @DisplayName("非白名单路径返回 false")
        void shouldNotMatchNonWhitelistPath() {
            assertFalse(PathGuard.matchWhiteList("/users/list", whiteList));
        }

        @Test
        @DisplayName("白名单路径的子路径不匹配（精确匹配安全策略）")
        void shouldNotMatchChildPath() {
            // /auth/login 不应匹配 /auth/login/anything
            assertFalse(PathGuard.matchWhiteList("/auth/login/anything", whiteList));
        }

        @Test
        @DisplayName("以斜杠结尾的白名单允许匹配子路径")
        void shouldMatchChildPathForTrailingSlashWhitelist() {
            Set<String> wl = PathGuard.whiteList("/public/");
            assertTrue(PathGuard.matchWhiteList("/public/image.png", wl));
            assertTrue(PathGuard.matchWhiteList("/public", wl) == false); // "/public" != "/public/"
        }

        @Test
        @DisplayName("null 路径返回 false")
        void shouldReturnFalseForNullPath() {
            assertFalse(PathGuard.matchWhiteList(null, whiteList));
        }

        @Test
        @DisplayName("null 白名单返回 false")
        void shouldReturnFalseForNullWhiteList() {
            assertFalse(PathGuard.matchWhiteList("/auth/login", null));
        }

        @Test
        @DisplayName("空白名单返回 false")
        void shouldReturnFalseForEmptyWhiteList() {
            assertFalse(PathGuard.matchWhiteList("/auth/login", Set.of()));
        }
    }

    // ==================== whiteList ====================

    @Nested
    @DisplayName("whiteList() 白名单构建")
    class WhiteListBuildTest {

        @Test
        @DisplayName("正常构建白名单")
        void shouldBuildWhiteList() {
            Set<String> wl = PathGuard.whiteList("/a", "/b", "/c");
            assertEquals(3, wl.size());
            assertTrue(wl.contains("/a"));
            assertTrue(wl.contains("/b"));
            assertTrue(wl.contains("/c"));
        }

        @Test
        @DisplayName("过滤 null 和空白字符串")
        void shouldFilterNullAndBlank() {
            Set<String> wl = PathGuard.whiteList("/a", null, "", "  ", "/b");
            assertEquals(2, wl.size());
            assertTrue(wl.contains("/a"));
            assertTrue(wl.contains("/b"));
        }

        @Test
        @DisplayName("去重")
        void shouldDeduplicate() {
            Set<String> wl = PathGuard.whiteList("/a", "/a", "/b");
            assertEquals(2, wl.size());
        }

        @Test
        @DisplayName("空参数返回空集合")
        void shouldReturnEmptyForNoArgs() {
            Set<String> wl = PathGuard.whiteList();
            assertTrue(wl.isEmpty());
        }
    }

    // ==================== internalHeaders ====================

    @Nested
    @DisplayName("internalHeaders() 内部头集合")
    class InternalHeadersTest {

        @Test
        @DisplayName("包含所有预期的内部头")
        void shouldContainAllExpectedHeaders() {
            Set<String> headers = PathGuard.internalHeaders();
            assertTrue(headers.contains("X-User-Id"));
            assertTrue(headers.contains("X-Username"));
            assertTrue(headers.contains("X-User-Dept-Id"));
            assertTrue(headers.contains("X-User-Roles"));
            assertTrue(headers.contains("X-User-Permissions"));
            assertTrue(headers.contains("X-Internal-Sig"));
            assertTrue(headers.contains("X-Internal-Ts"));
        }

        @Test
        @DisplayName("返回不可变集合")
        void shouldReturnImmutableSet() {
            Set<String> headers = PathGuard.internalHeaders();
            assertThrows(UnsupportedOperationException.class, () -> headers.add("X-New-Header"));
        }
    }
}
