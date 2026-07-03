package com.njydsz.pmis.workflow.job;

import com.njydsz.pmis.workflow.config.FlowHistoryProperties;
import com.njydsz.pmis.workflow.service.FlowHistoryArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowHistoryArchiveJobHandler 单元测试
 *
 * <p>P2-8：覆盖 JobHandler 的归档开关、paramsJson 参数覆盖、Service 委托逻辑。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>archiveEnabled=false 时跳过执行，不调用 Service</li>
 *   <li>archiveEnabled=true 且 paramsJson 为空时使用配置默认值调用 Service</li>
 *   <li>paramsJson 包含 days/batchSize/maxProcessMs 时覆盖默认值</li>
 *   <li>paramsJson 解析失败时回退到 null（使用配置默认值）</li>
 *   <li>返回结果包含 archive 与 purge 两个子 Map</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FlowHistoryArchiveJobHandlerTest {

    @Mock
    private FlowHistoryArchiveService archiveService;

    private FlowHistoryProperties properties;

    @InjectMocks
    private FlowHistoryArchiveJobHandler handler;

    @BeforeEach
    void setUp() {
        properties = new FlowHistoryProperties();
        handler = new FlowHistoryArchiveJobHandler(archiveService, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldSkipWhenArchiveDisabled() {
        properties.setArchiveEnabled(false);

        Object result = handler.execute(null);

        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(true, map.get("ok"));
        assertEquals(true, map.get("skipped"));
        assertEquals("archiveEnabled=false", map.get("reason"));
        verify(archiveService, never()).archive(any(), any(), any());
        verify(archiveService, never()).purge(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldUseConfigDefaultsWhenParamsJsonEmpty() {
        properties.setArchiveEnabled(true);

        Map<String, Object> archiveResult = new LinkedHashMap<>();
        archiveResult.put("ok", true);
        archiveResult.put("archived", 0);
        when(archiveService.archive(isNull(), isNull(), isNull())).thenReturn(archiveResult);

        Map<String, Object> purgeResult = new LinkedHashMap<>();
        purgeResult.put("ok", true);
        purgeResult.put("skipped", true);
        when(archiveService.purge(isNull())).thenReturn(purgeResult);

        Object result = handler.execute(null);

        Map<String, Object> map = (Map<String, Object>) result;
        assertNotNullMap(map, "archive");
        assertNotNullMap(map, "purge");
        verify(archiveService, times(1)).archive(isNull(), isNull(), isNull());
        verify(archiveService, times(1)).purge(isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldOverrideFromParamsJson() {
        properties.setArchiveEnabled(true);
        String paramsJson = "{\"days\":90,\"batchSize\":50,\"maxProcessMs\":5000,\"purgeDays\":730}";

        when(archiveService.archive(eq(90), eq(50), eq(5000L)))
                .thenReturn(Map.of("ok", true, "archived", 5));
        when(archiveService.purge(eq(730)))
                .thenReturn(Map.of("ok", true, "purgedInstances", 0));

        Object result = handler.execute(paramsJson);

        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("archive"));
        assertTrue(map.containsKey("purge"));
        verify(archiveService, times(1)).archive(eq(90), eq(50), eq(5000L));
        verify(archiveService, times(1)).purge(eq(730));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldFallbackToNullWhenParamsJsonInvalid() {
        properties.setArchiveEnabled(true);
        String invalidJson = "not a json";

        when(archiveService.archive(isNull(), isNull(), isNull()))
                .thenReturn(new HashMap<>());
        when(archiveService.purge(isNull()))
                .thenReturn(new HashMap<>());

        Object result = handler.execute(invalidJson);

        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("archive"));
        assertTrue(map.containsKey("purge"));
        verify(archiveService, times(1)).archive(isNull(), isNull(), isNull());
        verify(archiveService, times(1)).purge(isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldFallbackToNullWhenParamsJsonHasZeroOrNegativeValues() {
        properties.setArchiveEnabled(true);
        // 0 和负数应被过滤为 null
        String paramsJson = "{\"days\":0,\"batchSize\":-1,\"maxProcessMs\":0,\"purgeDays\":-5}";

        when(archiveService.archive(isNull(), isNull(), isNull()))
                .thenReturn(new HashMap<>());
        when(archiveService.purge(isNull()))
                .thenReturn(new HashMap<>());

        Object result = handler.execute(paramsJson);

        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("archive"));
        verify(archiveService, times(1)).archive(isNull(), isNull(), isNull());
        verify(archiveService, times(1)).purge(isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldHandlePartialParamsJson() {
        properties.setArchiveEnabled(true);
        // 仅提供 days，其他字段不覆盖
        String paramsJson = "{\"days\":120}";

        when(archiveService.archive(eq(120), isNull(), isNull()))
                .thenReturn(new HashMap<>());
        when(archiveService.purge(isNull()))
                .thenReturn(new HashMap<>());

        Object result = handler.execute(paramsJson);

        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("archive"));
        verify(archiveService, times(1)).archive(eq(120), isNull(), isNull());
        verify(archiveService, times(1)).purge(isNull());
    }

    // ============ 辅助方法 ============

    private void assertNotNullMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertTrue(value instanceof Map, key + " 应为 Map 类型");
    }
}
