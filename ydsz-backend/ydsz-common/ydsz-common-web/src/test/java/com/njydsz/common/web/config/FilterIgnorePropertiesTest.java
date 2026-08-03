package com.njydsz.common.web.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.web.config.FilterIgnoreConstant;

/**
 * {@link FilterIgnoreProperties} 单元测试
 *
 * <p>覆盖合并策略、replaceBuiltin 替换策略、auth 服务名解析等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("FilterIgnoreProperties 过滤忽略配置测试")
class FilterIgnorePropertiesTest {

    @Test
    @DisplayName("默认（未配置）时返回内置默认值")
    void defaults_useBuiltin() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        assertEquals(FilterIgnoreConstant.getCommonIgnoreUrls(),
                props.getMergedCommonIgnoreUrls());
        assertEquals(FilterIgnoreConstant.getSecurityExcludeUrls(),
                props.getMergedSecurityExcludeUrls());
        assertEquals(FilterIgnoreConstant.getAuthFilterIgnoreServiceNames(),
                props.getResolvedAuthFilterIgnoreServiceNames());
    }

    @Test
    @DisplayName("配置值与内置默认值合并（common-ignore-urls）")
    void commonIgnoreUrls_merge() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setCommonIgnoreUrls(List.of("/custom/**"));
        Set<String> merged = props.getMergedCommonIgnoreUrls();
        assertTrue(merged.contains("/custom/**"));
        assertTrue(merged.containsAll(FilterIgnoreConstant.getCommonIgnoreUrls()));
        // 去重
        assertEquals(FilterIgnoreConstant.getCommonIgnoreUrls().size() + 1, merged.size());
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
        assertTrue(merged.containsAll(FilterIgnoreConstant.getSecurityExcludeUrls()));
    }

    @Test
    @DisplayName("auth-filter-ignore-service-names 与内置默认值合并")
    void authServiceNames_merge() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        props.setAuthFilterIgnoreServiceNames(List.of("ydsz-custom-web"));
        Set<String> resolved = props.getResolvedAuthFilterIgnoreServiceNames();
        assertTrue(resolved.contains("ydsz-custom-web"));
        assertTrue(resolved.containsAll(FilterIgnoreConstant.getAuthFilterIgnoreServiceNames()));
    }

    @Test
    @DisplayName("返回集合可修改但不影响内置常量（副本语义）")
    void mergedIsCopy() {
        FilterIgnoreProperties props = new FilterIgnoreProperties();
        Set<String> merged = props.getMergedCommonIgnoreUrls();
        // 修改返回的集合不会污染内置常量
        merged.clear();
        assertFalse(FilterIgnoreConstant.getCommonIgnoreUrls().isEmpty());
    }
}
