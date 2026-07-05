package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.InitiationDO;
import com.njydsz.pmis.project.enums.InitiationStage;
import com.njydsz.pmis.project.mapper.BudgetItemMapper;
import com.njydsz.pmis.project.mapper.GateReviewMapper;
import com.njydsz.pmis.project.mapper.InitiationMapper;
import com.njydsz.pmis.project.feign.WorkflowServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InitiationServiceImpl 单元测试")
class InitiationServiceImplTest {

    @Mock
    private InitiationMapper initiationMapper;

    @Mock
    private BudgetItemMapper budgetItemMapper;

    @Mock
    private GateReviewMapper gateReviewMapper;

    @Mock
    private NameAssembler nameAssembler;

    @Mock
    private WorkflowServiceClient workflowServiceClient;

    @InjectMocks
    private InitiationServiceImpl initiationService;

    @Nested
    @DisplayName("createInitiation 方法")
    class CreateInitiationTest {

        @Test
        @DisplayName("创建立项 - 正常流程")
        void createInitiation_Success() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");
            dto.setPlannedStartDate(LocalDate.of(2026, 1, 1));
            dto.setPlannedEndDate(LocalDate.of(2026, 12, 31));

            when(initiationMapper.selectByCode("PRJ-001")).thenReturn(null);
            doAnswer(invocation -> {
                InitiationDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(initiationMapper).insert(any(InitiationDO.class));

            // When
            Long id = initiationService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
            verify(initiationMapper).insert(any(InitiationDO.class));
        }

        @Test
        @DisplayName("创建立项 - 项目编号已存在")
        void createInitiation_DuplicateCode() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("EXISTING-PRJ");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");

            InitiationDO existing = new InitiationDO();
            when(initiationMapper.selectByCode("EXISTING-PRJ")).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> initiationService.create(dto))
                    .isInstanceOf(BizException.class);
            verify(initiationMapper, never()).insert(any(InitiationDO.class));
        }

        @Test
        @DisplayName("创建立项 - 参数校验：DTO为空")
        void createInitiation_NullDTO() {
            // When & Then
            assertThatThrownBy(() -> initiationService.create(null))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建立项 - 参数校验：项目编号为空")
        void createInitiation_EmptyCode() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");

            // When & Then
            assertThatThrownBy(() -> initiationService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建立项 - 参数校验：客户ID为空")
        void createInitiation_NullCustomerId() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(null);
            dto.setProjectType("OUTSOURCING");

            // When & Then
            assertThatThrownBy(() -> initiationService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建立项 - 参数校验：结束日期早于开始日期")
        void createInitiation_InvalidDates() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");
            dto.setPlannedStartDate(LocalDate.of(2026, 12, 31));
            dto.setPlannedEndDate(LocalDate.of(2026, 1, 1));

            // When & Then
            assertThatThrownBy(() -> initiationService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建立项 - 默认值设置")
        void createInitiation_DefaultValues() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");
            // 不设置 stage 和 projectLevel

            when(initiationMapper.selectByCode("PRJ-001")).thenReturn(null);
            doAnswer(invocation -> {
                InitiationDO entity = invocation.getArgument(0);
                entity.setId(1L);
                // 验证默认值
                assertThat(entity.getStage()).isEqualTo(InitiationStage.PRE_INITIATION.getCode());
                assertThat(entity.getProjectLevel()).isEqualTo("C");
                return 1;
            }).when(initiationMapper).insert(any(InitiationDO.class));

            // When
            Long id = initiationService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
        }

        @Test
        @DisplayName("创建立项 - 自动计算工期天数")
        void createInitiation_CalculateDuration() {
            // Given
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("OUTSOURCING");
            dto.setPlannedStartDate(LocalDate.of(2026, 1, 1));
            dto.setPlannedEndDate(LocalDate.of(2026, 1, 11)); // 10天

            when(initiationMapper.selectByCode("PRJ-001")).thenReturn(null);
            doAnswer(invocation -> {
                InitiationDO entity = invocation.getArgument(0);
                entity.setId(1L);
                // 验证工期计算
                assertThat(entity.getDurationDays()).isEqualTo(10);
                return 1;
            }).when(initiationMapper).insert(any(InitiationDO.class));

            // When
            Long id = initiationService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("updateInitiation / changeStage 方法")
    class UpdateInitiationTest {

        @Test
        @DisplayName("阶段迁移 - 正常流程")
        void changeStage_Success() {
            // Given
            InitiationStageDTO dto = new InitiationStageDTO();
            dto.setId(1L);
            dto.setTargetStage(InitiationStage.SUBMITTED.getCode());

            InitiationDO existing = new InitiationDO();
            existing.setId(1L);
            existing.setStage(InitiationStage.PRE_INITIATION.getCode());
            existing.setCurrentGate("GD0");
            when(initiationMapper.selectById(1L)).thenReturn(existing);
            when(initiationMapper.updateStage(eq(1L), eq(InitiationStage.SUBMITTED.getCode()), anyString())).thenReturn(1);

            // When & Then
            assertThatCode(() -> initiationService.changeStage(dto)).doesNotThrowAnyException();
            verify(initiationMapper).updateStage(eq(1L), eq(InitiationStage.SUBMITTED.getCode()), anyString());
        }

        @Test
        @DisplayName("阶段迁移 - 立项不存在")
        void changeStage_NotFound() {
            // Given
            InitiationStageDTO dto = new InitiationStageDTO();
            dto.setId(999L);
            dto.setTargetStage(InitiationStage.APPROVED.getCode());

            when(initiationMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> initiationService.changeStage(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("阶段迁移 - 目标阶段非法")
        void changeStage_InvalidTargetStage() {
            // Given
            InitiationStageDTO dto = new InitiationStageDTO();
            dto.setId(1L);
            dto.setTargetStage("INVALID_STAGE");

            InitiationDO existing = new InitiationDO();
            existing.setId(1L);
            existing.setStage(InitiationStage.PRE_INITIATION.getCode());
            when(initiationMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> initiationService.changeStage(dto))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("approve / markApproved 方法")
    class ApproveTest {

        @Test
        @DisplayName("标记已批准 - 正常流程")
        void markApproved_Success() {
            // Given
            InitiationDO existing = new InitiationDO();
            existing.setId(1L);
            existing.setStage(InitiationStage.APPROVING.getCode());
            when(initiationMapper.selectById(1L)).thenReturn(existing);
            when(initiationMapper.updateStage(eq(1L), eq(InitiationStage.APPROVED.getCode()), anyString())).thenReturn(1);

            // When & Then
            assertThatCode(() -> initiationService.markApproved(1L)).doesNotThrowAnyException();
            verify(initiationMapper).updateStage(eq(1L), eq(InitiationStage.APPROVED.getCode()), anyString());
        }

        @Test
        @DisplayName("标记已批准 - 立项不存在")
        void markApproved_NotFound() {
            // Given
            when(initiationMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> initiationService.markApproved(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("reject / markRejected 方法")
    class RejectTest {

        @Test
        @DisplayName("标记已驳回 - 正常流程")
        void markRejected_Success() {
            // Given
            InitiationDO existing = new InitiationDO();
            existing.setId(1L);
            existing.setStage(InitiationStage.APPROVING.getCode());
            existing.setCurrentGate("GD1");
            when(initiationMapper.selectById(1L)).thenReturn(existing);
            when(initiationMapper.updateStage(eq(1L), eq(InitiationStage.REJECTED.getCode()), eq("GD1"))).thenReturn(1);

            // When & Then
            assertThatCode(() -> initiationService.markRejected(1L, "不符合要求")).doesNotThrowAnyException();
            verify(initiationMapper).updateStage(1L, InitiationStage.REJECTED.getCode(), "GD1");
        }

        @Test
        @DisplayName("标记已驳回 - 立项不存在")
        void markRejected_NotFound() {
            // Given
            when(initiationMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> initiationService.markRejected(999L, "原因"))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除立项 - 正常流程")
        void delete_Success() {
            // Given
            InitiationDO existing = new InitiationDO();
            existing.setId(1L);
            when(initiationMapper.selectById(1L)).thenReturn(existing);
            when(initiationMapper.deleteById(1L)).thenReturn(1);

            // When & Then
            assertThatCode(() -> initiationService.delete(1L)).doesNotThrowAnyException();
            verify(initiationMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除立项 - 立项不存在")
        void delete_NotFound() {
            // Given
            when(initiationMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> initiationService.delete(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("根据ID查询 - 正常流程")
        void getById_Success() {
            // Given
            InitiationDO initiation = new InitiationDO();
            initiation.setId(1L);
            initiation.setProjectCode("PRJ-001");
            when(initiationMapper.selectById(1L)).thenReturn(initiation);

            // When
            InitiationDO result = initiationService.getById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询 - 立项不存在")
        void getById_NotFound() {
            // Given
            when(initiationMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> initiationService.getById(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询 - 正常流程")
        @SuppressWarnings("unchecked")
        void page_Success() {
            // Given
            when(initiationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<InitiationDO> result = initiationService.page(1, 10, null, null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(initiationMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带关键词过滤")
        @SuppressWarnings("unchecked")
        void page_WithKeyword() {
            // Given
            when(initiationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<InitiationDO> result = initiationService.page(1, 10, "测试", null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(initiationMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带阶段过滤")
        @SuppressWarnings("unchecked")
        void page_WithStage() {
            // Given
            when(initiationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<InitiationDO> result = initiationService.page(1, 10, null, InitiationStage.APPROVED.getCode(), null, null);

            // Then
            assertThat(result).isNotNull();
            verify(initiationMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("recomputeBudget 方法")
    class RecomputeBudgetTest {

        @Test
        @DisplayName("重新计算预算 - 正常流程")
        void recomputeBudget_Success() {
            // Given
            InitiationDO initiation = new InitiationDO();
            initiation.setId(1L);
            when(initiationMapper.selectById(1L)).thenReturn(initiation);
            when(budgetItemMapper.selectByInitiationId(1L)).thenReturn(java.util.List.of());
            when(initiationMapper.updateById(any(InitiationDO.class))).thenReturn(1);

            // When
            BigDecimal total = initiationService.recomputeBudget(1L);

            // Then
            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
            verify(initiationMapper).updateById(any(InitiationDO.class));
        }
    }
}
