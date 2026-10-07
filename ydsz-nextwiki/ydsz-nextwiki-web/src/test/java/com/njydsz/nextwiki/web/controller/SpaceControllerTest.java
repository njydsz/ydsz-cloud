package com.njydsz.nextwiki.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.server.service.SpaceApplicationService;

/**
 * {@link SpaceController} 单元测试。
 *
 * <p>覆盖空间列表查询与创建 happy-path。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class SpaceControllerTest {

  private MockMvc mockMvc;

  @Mock
  private SpaceApplicationService spaceApplicationService;

  @InjectMocks
  private SpaceController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 查询空间列表返回 200。
   */
  @Test
  @DisplayName("GET /nextwiki/spaces 查询空间列表返回 200")
  void listSpacesReturnsOk() throws Exception {
    SpaceVO space = SpaceVO.builder().id("1234567890").name("测试空间").build();

    when(spaceApplicationService.listSpaces("user-001")).thenReturn(List.of(space));

    mockMvc.perform(get("/nextwiki/spaces")
            .header(AuthHeaderConstants.X_USER_ID, "user-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 创建空间返回 200。
   */
  @Test
  @DisplayName("POST /nextwiki/spaces 创建空间返回 200")
  void createSpaceReturnsOk() throws Exception {
    SpaceVO space = SpaceVO.builder().id("1234567890").name("新空间").build();

    when(spaceApplicationService.createSpace(any(), any(), any(), any()))
        .thenReturn(space);

    String body = "{\"name\":\"新空间\",\"description\":\"测试描述\",\"visibility\":\"private\"}";
    mockMvc.perform(post("/nextwiki/spaces")
            .header(AuthHeaderConstants.X_USER_ID, "user-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
