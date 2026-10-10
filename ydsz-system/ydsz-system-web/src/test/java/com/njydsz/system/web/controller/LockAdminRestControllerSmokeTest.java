package com.njydsz.system.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.lock.admin.DistributedLockAdmin;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link LockAdminRestController} Smoke Test。
 *
 * <p>验证分布式锁分页查询和统计端点可正确返回 HTTP 200 + YdszResponse 信封结构。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class LockAdminRestControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private DistributedLockAdmin lockAdmin;

  @org.mockito.Mock
  private LockMetrics lockMetrics;

  @BeforeEach
  void setUp() {
    LockStrategy lockStrategy = org.mockito.Mockito.mock(LockStrategy.class);
    when(lockStrategy.getLockAdmin()).thenReturn(lockAdmin);
    when(lockAdmin.scanKeys("lock:*", 50)).thenReturn(new ArrayList<>());
    LockAdminRestController controller = new LockAdminRestController(lockStrategy, lockMetrics);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /system/lock/page")
  class Page {

    @Test
    @DisplayName("should return 200 when page query succeeds")
    void should_return_200_when_page_query_succeeds() throws Exception {
      mockMvc.perform(get("/system/lock/page").param("pageNum", "1").param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }

  @Nested
  @DisplayName("GET /system/lock/stats")
  class Stats {

    @Test
    @DisplayName("should return 200 when stats query succeeds")
    void should_return_200_when_stats_query_succeeds() throws Exception {
      mockMvc.perform(get("/system/lock/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
