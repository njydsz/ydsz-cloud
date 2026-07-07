package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathGuard 单元测试（P0-C5）
 *
 * @author ydsz-pmis-team
 */
@DisplayName("PathGuard 路径安全测试")
class PathGuardTest {

    // ==================== sanitize ====================

    @Test
    @DisplayName("sanitize - 正常路径应原样返回")
    void sanitize_normalPath_shouldReturnAsIs() {
        assertEquals("/users", PathGuard.sanitize("/users"));
        assertEquals("/users/123", PathGuard.sanitize("/users/123"));
    }

    @Test
    @DisplayName("sanitize - 含 .. 路径穿越应返回 null")
    void sanitize_pathTraversal_shouldReturnNull() {
        assertNull(PathGuard.sanitize("/auth/login/../users/list"));
        assertNull(PathGuard.sanitize("/../etc/passwd"));
        assertNull(PathGuard.sanitize("/users/../../etc/passwd"));
    }

    @Test
    @DisplayName("sanitize - 连续斜杠应合并为单个")
    void sanitize_consecutiveSlashes_shouldCollapse() {
        assertEquals("/users", PathGuard.sanitize("//api///v1//users"));
        assertEquals("/users/", PathGuard.sanitize("//api///v1//users//"));
    }

    @Test
    @DisplayName("sanitize - null/空应返回 null")
    void sanitize_nullOrEmpty_shouldReturnNull() {
        assertNull(PathGuard.sanitize(null));
        assertNull(PathGuard.sanitize(""));
    }

    @Test
    @DisplayName("sanitize - 非绝对路径应返回 null")
    void sanitize_nonAbsolute_shouldReturnNull() {
        assertNull(PathGuard.sanitize("api/v1/users"));
    }

    @Test
    @DisplayName("sanitize - 仅斜杠应返回 /")
    void sanitize_rootOnly_shouldReturnSlash() {
        assertEquals("/", PathGuard.sanitize("/"));
        assertEquals("/", PathGuard.sanitize("///"));
    }

    // ==================== matchWhiteList ====================

    @Test
    @DisplayName("matchWhiteList - 精确匹配应命中")
    void matchWhiteList_exactMatch_shouldHit() {
        Set<String> wl = PathGuard.whiteList("/auth/login", "/health");
        assertTrue(PathGuard.matchWhiteList("/auth/login", wl));
        assertTrue(PathGuard.matchWhiteList("/health", wl));
    }

    @Test
    @DisplayName("matchWhiteList - 子路径不应命中（精确匹配语义）")
    void matchWhiteList_subPath_shouldNotHit() {
        Set<String> wl = PathGuard.whiteList("/auth/login");
        assertFalse(PathGuard.matchWhiteList("/auth/login/anything", wl));
        assertFalse(PathGuard.matchWhiteList("/auth/login/", wl));
        assertFalse(PathGuard.matchWhiteList("/auth/login?foo=bar", wl));
    }

    @Test
    @DisplayName("matchWhiteList - 白名单以 / 结尾时允许前缀匹配子路径")
    void matchWhiteList_trailingSlash_shouldMatchSubPath() {
        Set<String> wl = PathGuard.whiteList("/public/");
        assertTrue(PathGuard.matchWhiteList("/public/", wl));
        assertTrue(PathGuard.matchWhiteList("/public/docs", wl));
        assertFalse(PathGuard.matchWhiteList("/public", wl));
    }

    @Test
    @DisplayName("matchWhiteList - 不在白名单应返回 false")
    void matchWhiteList_notInList_shouldReturnFalse() {
        Set<String> wl = PathGuard.whiteList("/auth/login");
        assertFalse(PathGuard.matchWhiteList("/users", wl));
        assertFalse(PathGuard.matchWhiteList("/auth", wl));
    }

    @Test
    @DisplayName("matchWhiteList - null/空集合应返回 false")
    void matchWhiteList_nullOrEmpty_shouldReturnFalse() {
        assertFalse(PathGuard.matchWhiteList(null, PathGuard.whiteList("/x")));
        assertFalse(PathGuard.matchWhiteList("/x", null));
        assertFalse(PathGuard.matchWhiteList("/x", Set.of()));
    }

    // ==================== whiteList 构造 ====================

    @Test
    @DisplayName("whiteList - 应过滤 null/空字符串")
    void whiteList_shouldFilterBlank() {
        Set<String> wl = PathGuard.whiteList("/a", null, "", "  ", "/b");
        assertEquals(2, wl.size());
        assertTrue(wl.contains("/a"));
        assertTrue(wl.contains("/b"));
    }

    @Test
    @DisplayName("whiteList - 返回不可变集合")
    void whiteList_shouldReturnImmutable() {
        Set<String> wl = PathGuard.whiteList("/a");
        assertThrows(UnsupportedOperationException.class, () -> wl.add("/b"));
    }

    // ==================== internalHeaders ====================

    @Test
    @DisplayName("internalHeaders - 应包含所有 X-User-* 和 X-Internal-* 头")
    void internalHeaders_shouldContainAllInternalHeaders() {
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
    @DisplayName("internalHeaders - 应为不可变集合")
    void internalHeaders_shouldBeImmutable() {
        Set<String> headers = PathGuard.internalHeaders();
        assertThrows(UnsupportedOperationException.class, () -> headers.add("X-Evil"));
    }
}
