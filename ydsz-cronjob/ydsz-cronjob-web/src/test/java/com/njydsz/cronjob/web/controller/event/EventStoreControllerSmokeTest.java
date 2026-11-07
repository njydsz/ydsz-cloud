package com.njydsz.cronjob.web.controller.event;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.cronjob.domain.event.JobEvent;
import com.njydsz.cronjob.domain.query.EventStoreQuery;
import com.njydsz.cronjob.server.service.event.EventStoreService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link EventStoreController} Smoke Test。
 *
 * <p>覆盖事件存储查询端点 {@code /cronjob/events/**}： 按任务查事件流、按类型分页查询。
 *
 * <p>验证路径变量绑定、{@link EventStoreQuery} 分页参数绑定（{@code pageNum/pageSize/eventType}）、 成功信封字段与分页元信息。
 *
 * @author ydsz-smoke-test
 * @since 26.11.06
 */
class EventStoreControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private EventStoreService eventStoreService;

  @org.mockito.InjectMocks
  private EventStoreController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  private JobEvent jobEvent(String eventType) {
    return new JobEvent(
        "evt-001",
        "job-001",
        eventType,
        "{\"k\":1}",
        "user-001",
        LocalDateTime.of(2026, 11, 6, 10, 0, 0));
  }

  @Nested
  @DisplayName("GET /cronjob/events/job/{jobId}")
  class GetJobEventStream {

    @Test
    @DisplayName("should return event stream in success envelope")
    void should_return_event_stream() throws Exception {
      when(eventStoreService.getJobEventStream("job-001"))
          .thenReturn(List.of(jobEvent("CREATED"), jobEvent("TRIGGERED")));

      mockMvc.perform(get("/cronjob/events/job/{jobId}", "job-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data[0].eventType").value("CREATED"))
          .andExpect(jsonPath("$.data[1].eventType").value("TRIGGERED"))
          .andExpect(jsonPath("$.data[0].jobId").value("job-001"));

      verify(eventStoreService).getJobEventStream("job-001");
    }

    @Test
    @DisplayName("should return empty list when no events")
    void should_return_empty_stream() throws Exception {
      when(eventStoreService.getJobEventStream("job-empty")).thenReturn(List.of());

      mockMvc.perform(get("/cronjob/events/job/{jobId}", "job-empty"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());
    }
  }

  @Nested
  @DisplayName("GET /cronjob/events/page")
  class PageByType {

    @Test
    @DisplayName("should bind EventStoreQuery params and return paged envelope")
    void should_page_by_type() throws Exception {
      JobEvent event = jobEvent("TRIGGERED");
      PageResponse<List<JobEvent>> page =
          PageResponse.success(1L, 1L, 20L, List.of(event));
      when(eventStoreService.pageByType(any(EventStoreQuery.class))).thenReturn(page);

      mockMvc.perform(
              get("/cronjob/events/page")
                  .param("eventType", "TRIGGERED")
                  .param("pageNum", "1")
                  .param("pageSize", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(1))
          .andExpect(jsonPath("$.data.pageNum").value(1))
          .andExpect(jsonPath("$.data.pageSize").value(20))
          .andExpect(jsonPath("$.data.data[0].eventType").value("TRIGGERED"));
    }

    @Test
    @DisplayName("should return empty page when no matching events")
    void should_return_empty_page() throws Exception {
      PageResponse<List<JobEvent>> page =
          PageResponse.success(0L, 1L, 20L, List.of());
      when(eventStoreService.pageByType(any(EventStoreQuery.class))).thenReturn(page);

      mockMvc.perform(get("/cronjob/events/page").param("eventType", "DELETED"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.total").value(0))
          .andExpect(jsonPath("$.data.data").isEmpty());
    }
  }
}
