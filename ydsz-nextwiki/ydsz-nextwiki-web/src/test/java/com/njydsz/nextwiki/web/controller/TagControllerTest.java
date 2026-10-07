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
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.server.service.TagApplicationService;

/**
 * {@link TagController} 单元测试。
 *
 * <p>覆盖标签创建与查询端点。
 *
 * @author catpaw-test
 */
@ExtendWith(MockitoExtension.class)
class TagControllerTest {

  private MockMvc mockMvc;

  @Mock
  private TagApplicationService tagApplicationService;

  @InjectMocks
  private TagController controller;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  /**
   * Happy-path: 创建标签返回 200。
   */
  @Test
  @DisplayName("POST /nextwiki/tags 创建标签返回 200")
  void createTagReturnsOk() throws Exception {
    TagDTO tagDTO = new TagDTO();
    tagDTO.setId("tag-001");
    tagDTO.setName("合同");

    when(tagApplicationService.createTag(any(), any(), any())).thenReturn(tagDTO);

    String body = "{\"name\":\"合同\",\"color\":\"#FF0000\"}";
    mockMvc.perform(post("/nextwiki/tags")
            .header(AuthHeaderConstants.X_USER_ID, "user-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }

  /**
   * Happy-path: 查询全部标签返回 200。
   */
  @Test
  @DisplayName("GET /nextwiki/tags 查询全部标签返回 200")
  void listTagsReturnsOk() throws Exception {
    TagVO tagVO = TagVO.builder().id("tag-001").name("合同").build();
    when(tagApplicationService.getAllTags()).thenReturn(List.of(tagVO));

    mockMvc.perform(get("/nextwiki/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(YdszResponse.SUCCESS));
  }
}
