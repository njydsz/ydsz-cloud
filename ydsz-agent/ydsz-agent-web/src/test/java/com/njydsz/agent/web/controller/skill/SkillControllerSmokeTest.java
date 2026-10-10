package com.njydsz.agent.web.controller.skill;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.agent.server.skill.SkillService;
import com.njydsz.common.test.BaseControllerMockTest;

/**
 * {@link SkillController} Smoke Test.
 *
 * <p>Verify skill management endpoints return HTTP 200 + YdszResponse envelope.
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class SkillControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @org.mockito.Mock
  private SkillService skillService;

  @org.mockito.InjectMocks
  private SkillController controller;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(controller);
  }

  @Nested
  @DisplayName("GET /agent/skill/list")
  class ListSkills {

    @Test
    @DisplayName("should return 200 when list skills succeeds")
    void should_return_200_when_list_skills_succeeds() throws Exception {
      when(skillService.listSkills()).thenReturn(List.of());

      mockMvc.perform(get("/agent/skill/list"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }
  }
}
