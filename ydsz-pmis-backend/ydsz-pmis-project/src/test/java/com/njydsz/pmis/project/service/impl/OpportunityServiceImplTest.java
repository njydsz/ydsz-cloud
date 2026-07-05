package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import com.njydsz.pmis.project.service.InitiationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityServiceImpl 单元测试")
class OpportunityServiceImplTest {

    @Mock
    private OpportunityMapper opportunityMapper;

    @Mock
    private NameAssembler nameAssembler;

    @Mock
    private InitiationService initiationService;

    @InjectMocks
    private OpportunityServiceImpl opportunityService;

    @Nested
    @DisplayName("createOpportunity 方法")
    class CreateOpportunityTest {

        @Test
        @DisplayName("创建商机 - 正常流程")
        void createOpportunity_Success() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            when(opportunityMapper.selectByCode("OPP-001")).thenReturn(null);
            doAnswer(invocation -> {
                OpportunityDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(opportunityMapper).insert(any(OpportunityDO.class));

            // When
            Long id = opportunityService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
            verify(opportunityMapper).insert(any(OpportunityDO.class));
        }

        @Test
        @DisplayName("创建商机 - 商机编号已存在")
        void createOpportunity_DuplicateCode() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("EXISTING-OPP");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            OpportunityDO existing = new OpportunityDO();
            when(opportunityMapper.selectByCode("EXISTING-OPP")).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> opportunityService.create(dto))
                    .isInstanceOf(BizException.class);
            verify(opportunityMapper, never()).insert(any(OpportunityDO.class));
        }

        @Test
        @DisplayName("创建商机 - 参数校验：DTO为空")
        void createOpportunity_NullDTO() {
            // When & Then
            assertThatThrownBy(() -> opportunityService.create(null))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建商机 - 参数校验：商机编号为空")
        void createOpportunity_EmptyCode() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            // When & Then
            assertThatThrownBy(() -> opportunityService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建商机 - 参数校验：客户ID为空")
        void createOpportunity_NullCustomerId() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(null);
            dto.setOwnerId(200L);

            // When & Then
            assertThatThrownBy(() -> opportunityService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建商机 - 参数校验：负责人ID为空")
        void createOpportunity_NullOwnerId() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建商机 - 默认值设置")
        void createOpportunity_DefaultValues() {
            // Given
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);
            // 不设置 status 和 level

            when(opportunityMapper.selectByCode("OPP-001")).thenReturn(null);
            doAnswer(invocation -> {
                OpportunityDO entity = invocation.getArgument(0);
                entity.setId(1L);
                // 验证默认值
                assertThat(entity.getStatus()).isEqualTo(OpportunityStatus.FOLLOWING.getCode());
                assertThat(entity.getLevel()).isEqualTo("C");
                return 1;
            }).when(opportunityMapper).insert(any(OpportunityDO.class));

            // When
            Long id = opportunityService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("convertToInitiation 方法")
    class ConvertToInitiationTest {

        @Test
        @DisplayName("商机转立项 - 正常流程")
        void convertToInitiation_Success() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setOpportunityCode("OPP-001");
            opp.setOpportunityName("测试商机");
            opp.setStatus(OpportunityStatus.WON.getCode());
            opp.setCustomerId(100L);
            opp.setCustomerName("客户A");
            opp.setEstimatedAmount(new BigDecimal("500000"));

            when(opportunityMapper.selectById(1L)).thenReturn(opp);
            when(initiationService.create(any())).thenReturn(10L);
            when(opportunityMapper.updateStatus(eq(1L), eq(OpportunityStatus.CONVERTED.getCode()), any())).thenReturn(1);

            // When
            Long initiationId = opportunityService.convertToInitiation(1L, 300L, 400L);

            // Then
            assertThat(initiationId).isEqualTo(10L);
            verify(initiationService).create(any());
            verify(opportunityMapper).updateStatus(1L, OpportunityStatus.CONVERTED.getCode(), null);
        }

        @Test
        @DisplayName("商机转立项 - 商机ID为空")
        void convertToInitiation_NullOpportunityId() {
            // When & Then
            assertThatThrownBy(() -> opportunityService.convertToInitiation(null, 300L, 400L))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("商机转立项 - 商机不存在")
        void convertToInitiation_OpportunityNotFound() {
            // Given
            when(opportunityMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.convertToInitiation(999L, 300L, 400L))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("商机转立项 - 商机状态非WON")
        void convertToInitiation_StatusNotWon() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setOpportunityCode("OPP-001");
            opp.setStatus(OpportunityStatus.FOLLOWING.getCode());
            opp.setCustomerId(100L);

            when(opportunityMapper.selectById(1L)).thenReturn(opp);

            // When & Then
            assertThatThrownBy(() -> opportunityService.convertToInitiation(1L, 300L, 400L))
                    .isInstanceOf(BizException.class);
            verify(initiationService, never()).create(any());
        }

        @Test
        @DisplayName("商机转立项 - 商机客户为空")
        void convertToInitiation_NullCustomer() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setOpportunityCode("OPP-001");
            opp.setStatus(OpportunityStatus.WON.getCode());
            opp.setCustomerId(null);

            when(opportunityMapper.selectById(1L)).thenReturn(opp);

            // When & Then
            assertThatThrownBy(() -> opportunityService.convertToInitiation(1L, 300L, 400L))
                    .isInstanceOf(BizException.class);
            verify(initiationService, never()).create(any());
        }
    }

    @Nested
    @DisplayName("evaluateWinRate 方法")
    class EvaluateWinRateTest {

        @Test
        @DisplayName("评估赢单率 - 正常流程")
        void evaluateWinRate_Success() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setEstimatedAmount(new BigDecimal("100000"));
            when(opportunityMapper.selectById(1L)).thenReturn(opp);
            when(opportunityMapper.updateById(any(OpportunityDO.class))).thenReturn(1);

            // When
            BigDecimal rate = opportunityService.evaluateWinRate(1L, "A", true);

            // Then
            assertThat(rate).isNotNull();
            verify(opportunityMapper).updateById(any(OpportunityDO.class));
        }

        @Test
        @DisplayName("评估赢单率 - 商机不存在")
        void evaluateWinRate_OpportunityNotFound() {
            // Given
            when(opportunityMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.evaluateWinRate(999L, "A", true))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("评估赢单率 - 不同信用等级")
        void evaluateWinRate_DifferentCreditLevels() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setEstimatedAmount(new BigDecimal("100000"));
            when(opportunityMapper.selectById(1L)).thenReturn(opp);
            when(opportunityMapper.updateById(any(OpportunityDO.class))).thenReturn(1);

            // When - 测试不同信用等级
            BigDecimal rateA = opportunityService.evaluateWinRate(1L, "A", true);
            BigDecimal rateB = opportunityService.evaluateWinRate(1L, "B", false);
            BigDecimal rateC = opportunityService.evaluateWinRate(1L, "C", true);
            BigDecimal rateD = opportunityService.evaluateWinRate(1L, "D", false);

            // Then
            assertThat(rateA).isNotNull();
            assertThat(rateB).isNotNull();
            assertThat(rateC).isNotNull();
            assertThat(rateD).isNotNull();
        }
    }

    @Nested
    @DisplayName("update 方法")
    class UpdateTest {

        @Test
        @DisplayName("更新商机 - 正常流程")
        void update_Success() {
            // Given
            OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
            dto.setId(1L);
            dto.setOpportunityName("更新后商机名称");
            dto.setLevel("A");

            OpportunityDO existing = new OpportunityDO();
            existing.setId(1L);
            existing.setOpportunityName("原商机名称");
            when(opportunityMapper.selectById(1L)).thenReturn(existing);
            when(opportunityMapper.updateById(any(OpportunityDO.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> opportunityService.update(dto)).doesNotThrowAnyException();
            verify(opportunityMapper).updateById(any(OpportunityDO.class));
        }

        @Test
        @DisplayName("更新商机 - ID为空")
        void update_IdIsNull() {
            // Given
            OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
            dto.setId(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.update(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("更新商机 - 商机不存在")
        void update_NotFound() {
            // Given
            OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
            dto.setId(999L);

            when(opportunityMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.update(dto))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除商机 - 正常流程")
        void delete_Success() {
            // Given
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            when(opportunityMapper.selectById(1L)).thenReturn(opp);
            when(opportunityMapper.deleteById(1L)).thenReturn(1);

            // When & Then
            assertThatCode(() -> opportunityService.delete(1L)).doesNotThrowAnyException();
            verify(opportunityMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除商机 - 商机不存在")
        void delete_NotFound() {
            // Given
            when(opportunityMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.delete(999L))
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
            OpportunityDO opp = new OpportunityDO();
            opp.setId(1L);
            opp.setOpportunityCode("OPP-001");
            when(opportunityMapper.selectById(1L)).thenReturn(opp);

            // When
            OpportunityDO result = opportunityService.getById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询 - 商机不存在")
        void getById_NotFound() {
            // Given
            when(opportunityMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> opportunityService.getById(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("changeStatus 方法")
    class ChangeStatusTest {

        @Test
        @DisplayName("状态迁移 - 正常流程")
        void changeStatus_Success() {
            // Given
            OpportunityStatusDTO dto = new OpportunityStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus(OpportunityStatus.QUOTED.getCode());

            OpportunityDO existing = new OpportunityDO();
            existing.setId(1L);
            existing.setStatus(OpportunityStatus.FOLLOWING.getCode());
            when(opportunityMapper.selectById(1L)).thenReturn(existing);
            when(opportunityMapper.updateStatus(eq(1L), eq(OpportunityStatus.QUOTED.getCode()), any())).thenReturn(1);

            // When & Then
            assertThatCode(() -> opportunityService.changeStatus(dto)).doesNotThrowAnyException();
            verify(opportunityMapper).updateStatus(eq(1L), eq(OpportunityStatus.QUOTED.getCode()), any());
        }

        @Test
        @DisplayName("状态迁移 - 输单需要原因")
        void changeStatus_LostRequiresReason() {
            // Given
            OpportunityStatusDTO dto = new OpportunityStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus(OpportunityStatus.LOST.getCode());
            dto.setLostReason(null); // 没有输单原因

            OpportunityDO existing = new OpportunityDO();
            existing.setId(1L);
            existing.setStatus(OpportunityStatus.FOLLOWING.getCode());
            when(opportunityMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> opportunityService.changeStatus(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("状态迁移 - 目标状态非法")
        void changeStatus_InvalidTargetStatus() {
            // Given
            OpportunityStatusDTO dto = new OpportunityStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus("INVALID_STATUS");

            OpportunityDO existing = new OpportunityDO();
            existing.setId(1L);
            existing.setStatus(OpportunityStatus.FOLLOWING.getCode());
            when(opportunityMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> opportunityService.changeStatus(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("状态迁移 - 迁移路径非法")
        void changeStatus_InvalidTransition() {
            // Given
            OpportunityStatusDTO dto = new OpportunityStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus(OpportunityStatus.CONVERTED.getCode());

            OpportunityDO existing = new OpportunityDO();
            existing.setId(1L);
            existing.setStatus(OpportunityStatus.FOLLOWING.getCode());
            when(opportunityMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> opportunityService.changeStatus(dto))
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
            when(opportunityMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<OpportunityDO> result = opportunityService.page(1, 10, null, null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(opportunityMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带关键词过滤")
        @SuppressWarnings("unchecked")
        void page_WithKeyword() {
            // Given
            when(opportunityMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<OpportunityDO> result = opportunityService.page(1, 10, "测试", null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(opportunityMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带状态过滤")
        @SuppressWarnings("unchecked")
        void page_WithStatus() {
            // Given
            when(opportunityMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<OpportunityDO> result = opportunityService.page(1, 10, null, OpportunityStatus.FOLLOWING.getCode(), null, null);

            // Then
            assertThat(result).isNotNull();
            verify(opportunityMapper).selectPage(any(Page.class), any());
        }
    }
}
