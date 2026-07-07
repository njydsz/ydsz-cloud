package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowCommentCreateDTO;
import com.njydsz.pmis.workflow.entity.FlowCommentDO;
import com.njydsz.pmis.workflow.mapper.FlowCommentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-2: 流程评论多级回复单元测试。
 *
 * <p>聚焦测试 {@link FlowCommentServiceImpl} 的 addComment / deleteComment / 查询方法。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@DisplayName("P2-2 流程评论多级回复测试")
@ExtendWith(MockitoExtension.class)
class FlowCommentServiceImplTest {

    @Mock
    private FlowCommentMapper commentMapper;

    @InjectMocks
    private FlowCommentServiceImpl service;

    private FlowCommentCreateDTO buildDto(String instanceId, String content, String parentCommentId) {
        FlowCommentCreateDTO dto = new FlowCommentCreateDTO();
        dto.setInstanceId(instanceId);
        dto.setContent(content);
        dto.setParentCommentId(parentCommentId);
        dto.setTaskId("task-1");
        dto.setNodeCode("node-approve");
        return dto;
    }

    private FlowCommentDO buildComment(String id, String instanceId, String userId, String parentCommentId) {
        FlowCommentDO comment = new FlowCommentDO();
        comment.setId(id);
        comment.setInstanceId(instanceId);
        comment.setUserId(userId);
        comment.setParentCommentId(parentCommentId);
        comment.setContent("评论内容");
        comment.setDeleted(0);
        return comment;
    }

    // ============================== 新增评论 / 回复 ==============================

    @Nested
    @DisplayName("新增评论 addComment")
    class AddCommentTest {

        @Test
        @DisplayName("userId 为空 → 抛 BAD_REQUEST")
        void emptyUserId_throwsBadRequest() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "内容", null);
            BizException ex = assertThrows(BizException.class,
                    () -> service.addComment(dto, "", "张三", "1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("一级评论（parentCommentId=null）→ 直接插入成功")
        void rootComment_insertsSuccessfully() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "这是一级评论", null);

            service.addComment(dto, "user-1", "张三", "1");

            ArgumentCaptor<FlowCommentDO> captor = ArgumentCaptor.forClass(FlowCommentDO.class);
            verify(commentMapper).insert(captor.capture());
            FlowCommentDO saved = captor.getValue();
            assertEquals("inst-1", saved.getInstanceId());
            assertEquals("user-1", saved.getUserId());
            assertEquals("张三", saved.getUserName());
            assertEquals("这是一级评论", saved.getContent());
            assertEquals(null, saved.getParentCommentId());
            assertEquals("1", saved.getTenantId());
        }

        @Test
        @DisplayName("回复评论（parentCommentId 非空）且父评论存在 → 插入成功")
        void replyComment_insertsSuccessfully() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "这是回复", "parent-1");
            dto.setReplyToUserId("user-parent");
            dto.setReplyToUserName("李四");
            when(commentMapper.selectById("parent-1"))
                    .thenReturn(buildComment("parent-1", "inst-1", "user-parent", null));

            service.addComment(dto, "user-2", "王五", "1");

            ArgumentCaptor<FlowCommentDO> captor = ArgumentCaptor.forClass(FlowCommentDO.class);
            verify(commentMapper).insert(captor.capture());
            FlowCommentDO saved = captor.getValue();
            assertEquals("parent-1", saved.getParentCommentId());
            assertEquals("user-parent", saved.getReplyToUserId());
            assertEquals("李四", saved.getReplyToUserName());
        }

        @Test
        @DisplayName("父评论不存在 → 抛 NOT_FOUND")
        void parentNotFound_throwsNotFound() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "回复", "parent-1");
            when(commentMapper.selectById("parent-1")).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.addComment(dto, "user-2", "王五", "1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_f2a3b4c5", ex.getErrorMessage());
        }

        @Test
        @DisplayName("父评论已删除 → 抛 NOT_FOUND")
        void parentDeleted_throwsNotFound() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "回复", "parent-1");
            FlowCommentDO deletedParent = buildComment("parent-1", "inst-1", "user-parent", null);
            deletedParent.setDeleted(1);
            when(commentMapper.selectById("parent-1")).thenReturn(deletedParent);

            assertThrows(BizException.class,
                    () -> service.addComment(dto, "user-2", "王五", "1"));
        }

        @Test
        @DisplayName("父评论实例不匹配 → 抛 BAD_REQUEST")
        void parentInstanceMismatch_throwsBadRequest() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "回复", "parent-1");
            // 父评论属于 inst-2，回复却指向 inst-1
            when(commentMapper.selectById("parent-1"))
                    .thenReturn(buildComment("parent-1", "inst-2", "user-parent", null));

            BizException ex = assertThrows(BizException.class,
                    () -> service.addComment(dto, "user-2", "王五", "1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_a3b4c5d6", ex.getErrorMessage());
        }

        @Test
        @DisplayName("tenantId=null → 默认 '1'")
        void nullTenantId_defaultsTo1() {
            FlowCommentCreateDTO dto = buildDto("inst-1", "内容", null);

            service.addComment(dto, "user-1", "张三", null);

            ArgumentCaptor<FlowCommentDO> captor = ArgumentCaptor.forClass(FlowCommentDO.class);
            verify(commentMapper).insert(captor.capture());
            assertEquals("1", captor.getValue().getTenantId());
        }
    }

    // ============================== 删除评论 ==============================

    @Nested
    @DisplayName("删除评论 deleteComment")
    class DeleteCommentTest {

        @Test
        @DisplayName("评论不存在 → 返回 false")
        void commentNotFound_returnsFalse() {
            when(commentMapper.selectById("c-1")).thenReturn(null);

            assertFalse(service.deleteComment("c-1", "user-1"));
        }

        @Test
        @DisplayName("评论已删除 → 返回 false")
        void alreadyDeleted_returnsFalse() {
            FlowCommentDO comment = buildComment("c-1", "inst-1", "user-1", null);
            comment.setDeleted(1);
            when(commentMapper.selectById("c-1")).thenReturn(comment);

            assertFalse(service.deleteComment("c-1", "user-1"));
        }

        @Test
        @DisplayName("非评论人删除 → 抛 FORBIDDEN")
        void otherUser_throwsForbidden() {
            FlowCommentDO comment = buildComment("c-1", "inst-1", "user-1", null);
            when(commentMapper.selectById("c-1")).thenReturn(comment);

            BizException ex = assertThrows(BizException.class,
                    () -> service.deleteComment("c-1", "user-2"));
            assertEquals(BizErrorCode.FORBIDDEN.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_b4c5d6e7", ex.getErrorMessage());
        }

        @Test
        @DisplayName("评论人本人删除 → 软删除成功，返回 true")
        void ownerDeletes_softDeletesSuccessfully() {
            FlowCommentDO comment = buildComment("c-1", "inst-1", "user-1", null);
            when(commentMapper.selectById("c-1")).thenReturn(comment);

            boolean result = service.deleteComment("c-1", "user-1");

            assertTrue(result);
            ArgumentCaptor<FlowCommentDO> captor = ArgumentCaptor.forClass(FlowCommentDO.class);
            verify(commentMapper).updateById(captor.capture());
            assertEquals(1, captor.getValue().getDeleted());
        }
    }

    // ============================== 查询方法 ==============================

    @Nested
    @DisplayName("查询评论")
    class QueryTest {

        @Test
        @DisplayName("listByInstance → 委托 mapper.listByInstance")
        void listByInstance_delegates() {
            List<FlowCommentDO> mockList = List.of(buildComment("c-1", "inst-1", "u1", null));
            when(commentMapper.listByInstance("1", "inst-1")).thenReturn(mockList);

            List<FlowCommentDO> result = service.listByInstance("1", "inst-1");

            assertEquals(1, result.size());
            verify(commentMapper).listByInstance("1", "inst-1");
        }

        @Test
        @DisplayName("listRootComments → 委托 mapper.listRootComments")
        void listRootComments_delegates() {
            when(commentMapper.listRootComments("1", "inst-1"))
                    .thenReturn(List.of(buildComment("c-1", "inst-1", "u1", null)));

            List<FlowCommentDO> result = service.listRootComments("1", "inst-1");

            assertEquals(1, result.size());
            verify(commentMapper).listRootComments("1", "inst-1");
        }

        @Test
        @DisplayName("listReplies → 委托 mapper.listReplies")
        void listReplies_delegates() {
            when(commentMapper.listReplies("parent-1"))
                    .thenReturn(List.of(buildComment("c-2", "inst-1", "u2", "parent-1")));

            List<FlowCommentDO> result = service.listReplies("parent-1");

            assertEquals(1, result.size());
            verify(commentMapper).listReplies("parent-1");
        }
    }
}
