package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 加权票签策略单元测试
 *
 * <p>验证 WEIGHTED_VOTE 策略：按 weight 累加、自定义阈值、回退到简单票签、推进时 skipByNode。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WEIGHTED_VOTE 加权票签策略测试")
class WeightedVoteCountersignStrategyTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowUserMapper userMapper;
    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private WeightedVoteCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 WEIGHTED_VOTE")
    void supportedType() {
        assertEquals(FlowPerformType.WEIGHTED_VOTE, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 标记用户已处理并累加计数")
    void onUserPassed_markAndAccumulate() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveFinished(1);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setUserId("100");
        dto.setComment("agree");

        strategy.onUserPassed(task, dto);

        verify(userMapper).markProcessed(anyString(), anyString(), anyString(), any());
        assertEquals(2, task.getApproveFinished());
        verify(archiveService).completeAndArchive(task, "agree");
    }

    @Test
    @DisplayName("onUserPassed userId 为 null 时跳过 markProcessed")
    void onUserPassed_nullUserId() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(2);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setUserId(null);

        strategy.onUserPassed(task, dto);

        verify(userMapper, org.mockito.Mockito.never()).markProcessed(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("shouldAdvance 按 weight 累加: 总权重 10, 阈值为 6, 通过 6 时达成")
    void shouldAdvance_weightedThreshold() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(3); // 仅 fallback 使用

        FlowUserDO u1 = new FlowUserDO();
        u1.setWeight(3);
        u1.setProcessed(1);
        FlowUserDO u2 = new FlowUserDO();
        u2.setWeight(3);
        u2.setProcessed(1);
        FlowUserDO u3 = new FlowUserDO();
        u3.setWeight(4);
        u3.setProcessed(0);
        when(userMapper.selectByTaskId("T1")).thenReturn(List.of(u1, u2, u3));

        // passedWeight=6, total=10, threshold=6 → 达成
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance 通过权重未达阈值")
    void shouldAdvance_weightedNotReached() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        FlowUserDO u1 = new FlowUserDO();
        u1.setWeight(2);
        u1.setProcessed(1);
        FlowUserDO u2 = new FlowUserDO();
        u2.setWeight(5);
        u2.setProcessed(0);
        when(userMapper.selectByTaskId("T1")).thenReturn(List.of(u1, u2));

        // passedWeight=2, total=7, threshold=4 → 未达成
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance user 列表为空时回退到简单票签")
    void shouldAdvance_fallbackToSimpleVote() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(3);
        task.setApproveFinished(2);
        when(userMapper.selectByTaskId("T1")).thenReturn(List.of());

        // fallback: 2 >= 3/2+1=2 → 达成
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance user 列表为 null 时回退到简单票签")
    void shouldAdvance_nullUserList() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(3);
        task.setApproveFinished(1);
        when(userMapper.selectByTaskId("T1")).thenReturn(null);

        // fallback: 1 < 2 → 未达成
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance 自定义 votePassRate=0.6 → ceil(10*0.6)=6")
    void shouldAdvance_customRate() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setVotePassRate(new BigDecimal("0.6"));

        FlowUserDO u1 = new FlowUserDO();
        u1.setWeight(5);
        u1.setProcessed(1);
        FlowUserDO u2 = new FlowUserDO();
        u2.setWeight(5);
        u2.setProcessed(0);
        when(userMapper.selectByTaskId("T1")).thenReturn(List.of(u1, u2));

        // passedWeight=5, total=10, threshold=ceil(10*0.6)=6 → 未达成
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance null weight 按 1 处理")
    void shouldAdvance_nullWeight() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        FlowUserDO u1 = new FlowUserDO();
        u1.setWeight(null);
        u1.setProcessed(1);
        FlowUserDO u2 = new FlowUserDO();
        u2.setWeight(null);
        u2.setProcessed(0);
        when(userMapper.selectByTaskId("T1")).thenReturn(List.of(u1, u2));

        // weight 兜底为 1, passedWeight=1, total=2, threshold=2 → 未达成
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("onAdvance 跳过同节点剩余 PENDING 任务")
    void onAdvance_skipRemaining() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId("I1");
        task.setNodeCode("N1");

        strategy.onAdvance(task, new FlowTaskOperateDTO());

        verify(taskMapper).skipByNode("I1", "N1", FlowTaskStatus.SKIPPED.name());
    }
}
