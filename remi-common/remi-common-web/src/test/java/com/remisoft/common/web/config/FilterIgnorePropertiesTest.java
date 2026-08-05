package com.remisoft.common.web.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.remisoft.common.auth.constant.FilterIgnoreConstants;

/**
 * {@link FilterIgnoreProperties} 单元测试
 *
 * <p>覆盖合并策略、replaceBuiltin 替换策略、auth 服务名解析等行为。
 *
 * @author remi-team
 * @since 1.1.0
 */
@DisplayName("FilterIgnoreProperties 过滤忽略配置测试")
class FilterIgnorePropertiesTest {

    @Test
    @DisplayName("默认（未配置）时返回内置默认值")
    void defaults_useBuiltin() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        assertEquals(FilterIgnoreConstants.getCommonIgnoreUrls(),
                props.getMergedCommonIgnoreUrls());
        assertEquals(FilterIgnoreConstants.getSecurityExcludeUrls(),
                props.getMergedSecurityExcludeUrls());
        assertEquals(FilterIgnoreConstants.getAuthFilterIgnoreServiceNames(),
                props.getResolvedAuthFilterIgnoreServiceNames());
    }

    @Test
    @DisplayName("配置值与内置默认值合并（common-ignore-urls）")
    void commonIgnoreUrls_merge() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setCommonIgnoreUrls(List.of("/custom/**"));
        Set<String> merged = props.getMergedCommonIgnoreUrls();
        assertTrue(merged.contains("/custom/**"));
        assertTrue(merged.containsAll(FilterIgnoreConstants.getCommonIgnoreUrls()));
        // 去重
        assertEquals(FilterIgnoreConstants.getCommonIgnoreUrls().size() + 1, merged.size());
    }

    @Test
    @DisplayName("replaceBuiltin=true 时 common-ignore-urls 整体替换")
    void commonIgnoreUrls_replaceBuiltin() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setReplaceBuiltin(true);
        props.setCommonIgnoreUrls(List.of("/custom/**"));
        Set<String> merged = props.getMergedCommonIgnoreUrls();
        assertEquals(Set.of("/custom/**"), merged);
    }

    @Test
    @DisplayName("replaceBuiltin=true 且未配置时返回空集合")
    void commonIgnoreUrls_replaceBuiltinEmpty() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setReplaceBuiltin(true);
        assertTrue(props.getMergedCommonIgnoreUrls().isEmpty());
        assertTrue(props.getMergedSecurityExcludeUrls().isEmpty());
        assertTrue(props.getResolvedAuthFilterIgnoreServiceNames().isEmpty());
    }

    @Test
    @DisplayName("security-exclude-urls 与内置默认值合并")
    void securityExcludeUrls_merge() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setSecurityExcludeUrls(List.of("/health/**"));
        Set<String> merged = props.getMergedSecurityExcludeUrls();
        assertTrue(merged.contains("/health/**"));
        assertTrue(merged.containsAll(FilterIgnoreConstants.getSecurityExcludeUrls()));
    }

    @Test
    @DisplayName("auth-filter-ignore-service-names 与内置默认值合并")
    void authServiceNames_merge() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setAuthFilterIgnoreServiceNames(List.of("remi-custom-web"));
        Set<String> resolved = props.getResolvedAuthFilterIgnoreServiceNames();
        assertTrue(resolved.contains("remi-custom-web"));
        assertTrue(resolved.containsAll(FilterIgnoreConstants.getAuthFilterIgnoreServiceNames()));
    }

    @Test
    @DisplayName("返回集合可修改但不影响内置常量（副本语义）")
    void mergedIsCopy() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        Set<String> merged = props.getMergedCommonIgnoreUrls();
        // 修改返回的集合不会污染内置常量
        merged.clear();
        assertFalse(FilterIgnoreConstants.getCommonIgnoreUrls().isEmpty());
    }
}
