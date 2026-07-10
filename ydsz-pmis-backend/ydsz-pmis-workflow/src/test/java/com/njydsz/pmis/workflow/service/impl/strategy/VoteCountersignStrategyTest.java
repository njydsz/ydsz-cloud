package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.impl.instance.FlowTaskArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 票签策略单元测试
 *
 * <p>验证 VOTE 策略：累加计数、默认过半数阈值、自定义 votePassRate 阈值、推进时 skipByNode。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VOTE 票签策略测试")
class VoteCountersignStrategyTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private VoteCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 VOTE")
    void supportedType() {
        assertEquals(FlowPerformType.VOTE, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 累加计数并归档")
    void onUserPassed_accumulate() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveFinished(0);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("agree");

        strategy.onUserPassed(task, dto);

        assertEquals(1, task.getApproveFinished());
        verify(archiveService).completeAndArchive(task, "agree");
    }

    @Test
    @DisplayName("乐观锁冲突抛 RESOURCE_CONFLICT")
    void onUserPassed_optimisticLockConflict() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(0);

        assertThrows(BizException.class,
                () -> strategy.onUserPassed(task, new FlowTaskOperateDTO()));
    }

    @Test
    @DisplayName("shouldAdvance 默认过半: 3 人中 2 人通过时达成")
    void shouldAdvance_defaultHalf() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(2);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance 默认过半: 3 人中 1 人通过时未达成")
    void shouldAdvance_defaultHalf_notReached() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(1);
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance 自定义 votePassRate=0.5 → ceil(3*0.5)=2")
    void shouldAdvance_customRate_half() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(2);
        task.setVotePassRate(new BigDecimal("0.5"));
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance 自定义 votePassRate=0.8 → ceil(3*0.8)=3")
    void shouldAdvance_customRate_high() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(2);
        task.setVotePassRate(new BigDecimal("0.8"));
        assertFalse(strategy.shouldAdvance(task));

        task.setApproveFinished(3);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance votePassRate=0 视为无效，使用默认过半 (3 人/2 阈)")
    void shouldAdvance_zeroRate_fallbackToDefault() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(1);
        task.setVotePassRate(BigDecimal.ZERO);
        // rate=0 不满足 (rate > 0 && rate <= 1.0)，threshold 仍走默认 (3/2)+1=2
        // 1 < 2 → 未达成
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance votePassRate>1 视为无效，使用默认过半")
    void shouldAdvance_invalidRate_fallbackDefault() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(3);
        task.setApproveFinished(2);
        task.setVotePassRate(new BigDecimal("1.5"));
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance count=1 时阈值为 1, 任何 1 人通过即达成")
    void shouldAdvance_countOne() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveCount(1);
        task.setApproveFinished(1);
        assertTrue(strategy.shouldAdvance(task));
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
