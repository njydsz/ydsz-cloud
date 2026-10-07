package com.njydsz.message.web.controller.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.njydsz.message.domain.dto.BatchProgressDTO;
import com.njydsz.message.domain.dto.BatchSendRequestDTO;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.server.service.SseEmitterService;
import com.njydsz.message.server.service.batch.BatchService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * {@link BatchController} 的 MockMvc 集成测试。
 *
 * <p>验证异步批量发送的两个核心接口：提交批次 / 查询进度。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@WebMvcTest(BatchController.class)
class BatchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private BatchService batchService;

  @MockBean
  private SseEmitterService sseEmitterService;

  @Nested
  @DisplayName("POST /message/batch/send 提交批次接口测试")
  class SubmitBatchEndpoint {

    @Test
    @DisplayName("提交批量发送应返回批次 VO")
    void submitBatch_shouldReturnBatchVo() throws Exception {
      MsgBatchVO batchVo = new MsgBatchVO();
      batchVo.setBatchId("batch-001");
      batchVo.setTotal(100);
      batchVo.setStatus("PENDING");

      when(batchService.submitBatch(any(BatchSendRequestDTO.class))).thenReturn(batchVo);

      BatchSendRequestDTO dto = new BatchSendRequestDTO();
      dto.setBatchId("batch-001");
      dto.setBatchName("测试批量发送");
      dto.setChannel("SMS");
      dto.setTemplateCode("SMS_VERIFY");
      dto.setReceiverList(List.of("1*********0", "1*********0"));

      mockMvc.perform(post("/message/batch/send")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(dto)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.batchId").value("batch-001"))
          .andExpect(jsonPath("$.data.total").value(100))
          .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
  }

  @Nested
  @DisplayName("GET /message/batch/progress/{batchId} 查询进度接口测试")
  class GetProgressEndpoint {

    @Test
    @DisplayName("查询批次进度应返回进度 DTO")
    void getProgress_shouldReturnProgressDto() throws Exception {
      BatchProgressDTO progressDto = new BatchProgressDTO();
      progressDto.setBatchId("batch-001");
      progressDto.setTotal(100);
      progressDto.setSuccess(80);
      progressDto.setFailed(5);
      progressDto.setProgressPercent(85.0);
      progressDto.setStatus("PROCESSING");

      when(batchService.getProgress("batch-001")).thenReturn(progressDto);

      mockMvc.perform(get("/message/batch/progress/batch-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.batchId").value("batch-001"))
          .andExpect(jsonPath("$.data.total").value(100))
          .andExpect(jsonPath("$.data.success").value(80))
          .andExpect(jsonPath("$.data.failed").value(5))
          .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }
  }
}
