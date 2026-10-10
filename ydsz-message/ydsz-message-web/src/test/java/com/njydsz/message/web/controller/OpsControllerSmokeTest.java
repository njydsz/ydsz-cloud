package com.njydsz.message.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.message.domain.vo.CacheStatsVO;
import com.njydsz.message.server.consumer.BloomFilterDeduplicator;
import com.njydsz.message.server.service.chain.SendPipelineFacade;
import com.njydsz.message.server.service.impl.ScheduledMessageScanner;
import com.njydsz.message.server.template.cache.CachedMessageTemplateRenderer;

/**
 * {@link OpsController} Smoke Test。
 *
 * <p>验证运维诊断端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class OpsControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private CachedMessageTemplateRenderer cachedTemplateEngine;

  @org.mockito.Mock
  private BloomFilterDeduplicator bloomFilterDeduplicator;

  @org.mockito.Mock
  private SendPipelineFacade sendPipelineFacade;

  @org.mockito.Mock
  private ScheduledMessageScanner scheduledMessageScanner;

  @org.mockito.InjectMocks
  private OpsController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /message/ops/template-cache/stats")
  class TemplateCacheStats {

    @Test
    @DisplayName("should return 200 when get template cache stats succeeds")
    void should_return_200_when_get_template_cache_stats_succeeds() throws Exception {
      when(cachedTemplateEngine.getCacheStats()).thenReturn(new com.njydsz.common.cache.stats.CacheStats(0L, 0L, 0L, 0L, 0L, 0L));
      when(cachedTemplateEngine.cacheSize()).thenReturn(0L);

      mockMvc.perform(get("/message/ops/template-cache/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
