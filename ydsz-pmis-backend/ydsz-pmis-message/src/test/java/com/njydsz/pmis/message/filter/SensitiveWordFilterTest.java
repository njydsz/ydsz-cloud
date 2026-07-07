package com.njydsz.pmis.message.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveWordFilter} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SensitiveWordFilter 敏感词过滤测试")
class SensitiveWordFilterTest {

    @Test
    @DisplayName("默认词库加载(无配置)")
    void shouldLoadDefaultWordsWhenNoConfig() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "");
        // 默认 6 个内置词
        assertEquals(6, filter.currentWords().size());
        assertTrue(filter.currentWords().contains("色情"));
        assertTrue(filter.currentWords().contains("赌博"));
    }

    @Test
    @DisplayName("配置词库覆盖默认")
    void shouldOverrideDefaultWordsFromConfig() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "敏感词A, 敏感词B,敏感词C");
        assertEquals(3, filter.currentWords().size());
        assertTrue(filter.currentWords().contains("敏感词a"));
        assertTrue(filter.currentWords().contains("敏感词b"));
    }

    @Test
    @DisplayName("filter 命中敏感词替换为 ***")
    void shouldReplaceSensitiveWordWithMask() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "赌博,色情");
        String result = filter.filter("欢迎来到赌博网站,提供色情内容");
        assertEquals("欢迎来到***网站,提供***内容", result);
    }

    @Test
    @DisplayName("filter 大小写不敏感替换")
    void shouldReplaceCaseInsensitively() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "BadWord");
        String result = filter.filter("this is BADWORD and badword");
        assertEquals("this is *** and ***", result);
    }

    @Test
    @DisplayName("filter 未启用时原样返回")
    void shouldReturnOriginalWhenDisabled() {
        SensitiveWordFilter filter = new SensitiveWordFilter(false, "赌博");
        String result = filter.filter("欢迎赌博");
        assertEquals("欢迎赌博", result);
    }

    @Test
    @DisplayName("filter 空内容原样返回")
    void shouldReturnOriginalWhenContentBlank() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "赌博");
        assertEquals(null, filter.filter(null));
        assertEquals("", filter.filter(""));
        assertEquals("   ", filter.filter("   "));
    }

    @Test
    @DisplayName("filter 无命中原样返回")
    void shouldReturnOriginalWhenNoHit() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "赌博");
        assertEquals("正常内容", filter.filter("正常内容"));
    }

    @Test
    @DisplayName("filter 多次命中分别替换")
    void shouldReplaceMultipleOccurrences() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "赌博");
        assertEquals("***1 ***2 ***3", filter.filter("赌博1 赌博2 赌博3"));
    }

    @Test
    @DisplayName("reload 运行时刷新词库")
    void shouldReloadWordsAtRuntime() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "");
        assertEquals(6, filter.currentWords().size());
        Set<String> newWords = new LinkedHashSet<>();
        newWords.add("新词1");
        newWords.add("新词2");
        filter.reload(newWords);
        assertEquals(2, filter.currentWords().size());
        assertTrue(filter.currentWords().contains("新词1"));
    }

    @Test
    @DisplayName("reload 空参数回退默认词库")
    void shouldFallbackToDefaultWhenReloadEmpty() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "自定义词");
        assertEquals(1, filter.currentWords().size());
        filter.reload(null);
        assertEquals(6, filter.currentWords().size());
    }

    @Test
    @DisplayName("currentWords 返回不可修改视图")
    void shouldReturnUnmodifiableView() {
        SensitiveWordFilter filter = new SensitiveWordFilter(true, "");
        Set<String> words = filter.currentWords();
        assertNotNull(words);
        try {
            words.add("hack");
            org.junit.jupiter.api.Assertions.fail("应抛出 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
    }
}
