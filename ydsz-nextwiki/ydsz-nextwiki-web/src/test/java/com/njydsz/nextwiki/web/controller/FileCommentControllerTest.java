package com.njydsz.nextwiki.web.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;
import com.njydsz.nextwiki.server.mention.MentionService;
import com.njydsz.nextwiki.server.service.FileCommentService;
import com.njydsz.nextwiki.web.handler.NextwikiExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FileCommentController} comment lifecycle HTTP contract test.
 */
class FileCommentControllerTest extends BaseControllerMockTest {

  private static final String CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

  private MockMvc mockMvc;
  private SnowflakeIdGenerator snowflakeIdGenerator;
  private FileCommentService fileCommentService;
  private MentionService mentionService;

  @BeforeEach
  void setUp() {
    snowflakeIdGenerator = mock(SnowflakeIdGenerator.class);
    fileCommentService = mock(FileCommentService.class);
    mentionService = mock(MentionService.class);
    FileCommentController controller =
        new FileCommentController(snowflakeIdGenerator, fileCommentService, mentionService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new NextwikiExceptionHandler(new MockEnvironment()))
        .build();
  }

  @Nested
  @DisplayName("GET /nextwiki/comments/file/{fileNodeId}")
  class ListComments {

    @Test
    @DisplayName("binds fileNodeId and returns the service comment list")
    void should_list_comments() throws Exception {
      FileCommentVO comment = comment("c001", "file-001", "Looks good");
      when(fileCommentService.listComments("file-001")).thenReturn(List.of(comment));

      mockMvc.perform(get("/nextwiki/comments/file/file-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].id").value("c001"))
          .andExpect(jsonPath("$.data[0].fileNodeId").value("file-001"))
          .andExpect(jsonPath("$.data[0].content").value("Looks good"));

      verify(fileCommentService).listComments("file-001");
      verifyNoInteractions(mentionService);
    }

    @Test
    @DisplayName("returns an empty data array when the service finds no comments")
    void should_return_empty_list() throws Exception {
      when(fileCommentService.listComments("empty-file")).thenReturn(List.of());

      mockMvc.perform(get("/nextwiki/comments/file/empty-file"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").isEmpty());
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/comments")
  class AddComment {

    @Test
    @DisplayName("binds request fields and headers, assigns an ID, and notifies mentions")
    void should_add_comment_with_mentions() throws Exception {
      when(snowflakeIdGenerator.nextId()).thenReturn(123456789L);
      when(mentionService.extractMentions("@alice please review"))
          .thenReturn(List.of("alice"));
      FileCommentVO saved = comment("123456789", "file-001", "@alice please review");
      when(fileCommentService.addComment(any(FileCommentDTO.class))).thenReturn(saved);

      mockMvc.perform(post("/nextwiki/comments")
              .header("X-User-Id", "user-001")
              .header("X-User-Name", "Bob")
              .contentType(CONTENT_TYPE)
              .content("{\"fileNodeId\":\"file-001\",\"content\":\"@alice please review\","
                  + "\"parentCommentId\":\"parent-001\",\"position\":\"line:10\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("123456789"))
          .andExpect(jsonPath("$.data.fileNodeId").value("file-001"))
          .andExpect(jsonPath("$.data.content").value("@alice please review"))
          .andExpect(jsonPath("$.data.parentCommentId").value("parent-001"))
          .andExpect(jsonPath("$.data.position").value("line:10"));

      ArgumentCaptor<FileCommentDTO> commentCaptor = ArgumentCaptor.forClass(FileCommentDTO.class);
      verify(fileCommentService).addComment(commentCaptor.capture());
      FileCommentDTO sent = commentCaptor.getValue();
      assertThat(sent.getId()).isEqualTo("123456789");
      assertThat(sent.getFileNodeId()).isEqualTo("file-001");
      assertThat(sent.getContent()).isEqualTo("@alice please review");
      assertThat(sent.getParentCommentId()).isEqualTo("parent-001");
      assertThat(sent.getPosition()).isEqualTo("line:10");
      assertThat(sent.getIsResolved()).isFalse();
      assertThat(sent.getIsEdited()).isFalse();
      assertThat(sent.getMentions()).containsExactly("alice");
      verify(mentionService).sendMentionNotifications(
          "123456789", "file-001", "user-001", "Bob", "@alice please review", List.of("alice"));
    }

    @Test
    @DisplayName("passes null mentions and skips notification for ordinary comments")
    void should_add_comment_without_mentions() throws Exception {
      when(snowflakeIdGenerator.nextId()).thenReturn(99L);
      when(mentionService.extractMentions("ordinary comment")).thenReturn(List.of());
      when(fileCommentService.addComment(any(FileCommentDTO.class)))
          .thenReturn(comment("99", "file-001", "ordinary comment"));

      mockMvc.perform(post("/nextwiki/comments")
              .header("X-User-Id", "user-001")
              .contentType(CONTENT_TYPE)
              .content("{\"fileNodeId\":\"file-001\",\"content\":\"ordinary comment\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.id").value("99"));

      ArgumentCaptor<FileCommentDTO> commentCaptor = ArgumentCaptor.forClass(FileCommentDTO.class);
      verify(fileCommentService).addComment(commentCaptor.capture());
      assertThat(commentCaptor.getValue().getMentions()).isNull();
      verify(mentionService, never()).sendMentionNotifications(
          any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejects malformed JSON and does not call comment services")
    void should_reject_malformed_json() throws Exception {
      mockMvc.perform(post("/nextwiki/comments")
              .header("X-User-Id", "user-001")
              .contentType(CONTENT_TYPE)
              .content("{\"fileNodeId\":\"file-001\""))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(snowflakeIdGenerator);
      verifyNoInteractions(mentionService);
      verify(fileCommentService, never()).addComment(any());
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR without sending a mention notification when persistence fails")
    void should_not_notify_when_comment_persistence_fails() throws Exception {
      when(snowflakeIdGenerator.nextId()).thenReturn(123L);
      when(mentionService.extractMentions("@alice")).thenReturn(List.of("alice"));
      when(fileCommentService.addComment(any(FileCommentDTO.class)))
          .thenThrow(new IllegalStateException("database unavailable"));

      mockMvc.perform(post("/nextwiki/comments")
              .header("X-User-Id", "user-001")
              .contentType(CONTENT_TYPE)
              .content("{\"fileNodeId\":\"file-001\",\"content\":\"@alice\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));

      verify(mentionService, never()).sendMentionNotifications(
          any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("DELETE /nextwiki/comments/{commentId}")
  class DeleteComment {

    @Test
    @DisplayName("binds commentId and delegates deletion to the service")
    void should_delete_comment() throws Exception {
      mockMvc.perform(delete("/nextwiki/comments/comment-001")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(fileCommentService).deleteComment("comment-001");
      verifyNoInteractions(snowflakeIdGenerator, mentionService);
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when deletion fails")
    void should_return_error_when_delete_fails() throws Exception {
      doThrow(new IllegalStateException("delete failed"))
          .when(fileCommentService).deleteComment("comment-001");

      mockMvc.perform(delete("/nextwiki/comments/comment-001")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  @Nested
  @DisplayName("POST /nextwiki/comments/{commentId}/resolve")
  class ResolveComment {

    @Test
    @DisplayName("binds both path and header and delegates resolution")
    void should_resolve_comment() throws Exception {
      mockMvc.perform(post("/nextwiki/comments/comment-001/resolve")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(fileCommentService).resolveComment("comment-001", "user-001");
      verifyNoInteractions(snowflakeIdGenerator, mentionService);
    }

    @Test
    @DisplayName("returns SYSTEM_ERROR when resolution fails")
    void should_return_error_when_resolve_fails() throws Exception {
      doThrow(new IllegalStateException("resolve failed"))
          .when(fileCommentService).resolveComment("comment-001", "user-001");

      mockMvc.perform(post("/nextwiki/comments/comment-001/resolve")
              .header("X-User-Id", "user-001"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }
  }

  private FileCommentVO comment(String id, String fileNodeId, String content) {
    return FileCommentVO.builder()
        .id(id)
        .fileNodeId(fileNodeId)
        .content(content)
        .isResolved(false)
        .isEdited(false)
        .createdBy("user-001")
        .createdAt(LocalDateTime.of(2026, 10, 27, 10, 0))
        .build();
  }
}
