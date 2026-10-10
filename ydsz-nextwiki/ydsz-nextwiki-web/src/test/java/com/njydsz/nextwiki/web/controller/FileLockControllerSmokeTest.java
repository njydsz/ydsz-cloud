package com.njydsz.nextwiki.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.nextwiki.server.service.FileLockService;
import com.njydsz.nextwiki.server.service.FilePermissionService;

/**
 * {@link FileLockController} Smoke Test。
 *
 * <p>验证文件锁定/解锁端点可正确返回 HTTP 200 + YdszResponse 信封结构，防御接口签名变更未被发现的生产事件。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FileLockControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private FileLockService fileLockService;

  private FilePermissionService filePermissionService;

  private FileLockController controller;

  @BeforeEach
  void setUp() {
    fileLockService = mock(FileLockService.class);
    filePermissionService = mock(FilePermissionService.class);
    controller = new FileLockController(fileLockService, filePermissionService);
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("POST /nextwiki/files/{nodeId}/lock")
  class LockFile {

    @Test
    @DisplayName("应返回 200 当锁定文件成功时")
    void should_return_200_when_lock_file_succeeds() throws Exception {
      String nodeId = "node-001";
      String userId = "user-001";

      mockMvc.perform(post("/nextwiki/files/{nodeId}/lock", nodeId)
              .header(AuthHeaderConstants.X_USER_ID, userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(fileLockService).lock(nodeId, userId);
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/files/{nodeId}/unlock")
  class UnlockFile {

    @Test
    @DisplayName("应返回 200 当解锁文件成功时")
    void should_return_200_when_unlock_file_succeeds() throws Exception {
      String nodeId = "node-001";
      String userId = "user-001";

      mockMvc.perform(post("/nextwiki/files/{nodeId}/unlock", nodeId)
              .header(AuthHeaderConstants.X_USER_ID, userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));

      verify(fileLockService).unlock(nodeId, userId);
    }
  }
}
