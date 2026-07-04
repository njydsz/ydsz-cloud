package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.dto.ContractStatusDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.enums.ContractStatus;
import com.njydsz.pmis.project.mapper.ContractMapper;
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
@DisplayName("ContractServiceImpl 单元测试")
class ContractServiceImplTest {

    @Mock
    private ContractMapper contractMapper;

    @Mock
    private NameAssembler nameAssembler;

    @InjectMocks
    private ContractServiceImpl contractService;

    @Nested
    @DisplayName("createContract 方法")
    class CreateContractTest {

        @Test
        @DisplayName("创建合同 - 正常流程")
        void createContract_Success() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CONTRACT-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);

            when(contractMapper.selectByCode("CONTRACT-001")).thenReturn(null);
            doAnswer(invocation -> {
                ContractDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(contractMapper).insert(any(ContractDO.class));

            // When
            Long id = contractService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
            verify(contractMapper).insert(any(ContractDO.class));
        }

        @Test
        @DisplayName("创建合同 - 合同编号已存在")
        void createContract_DuplicateCode() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("EXISTING-CONTRACT");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);

            ContractDO existing = new ContractDO();
            when(contractMapper.selectByCode("EXISTING-CONTRACT")).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.create(dto))
                    .isInstanceOf(BizException.class);
            verify(contractMapper, never()).insert(any(ContractDO.class));
        }

        @Test
        @DisplayName("创建合同 - 参数校验：DTO为空")
        void createContract_NullDTO() {
            // When & Then
            assertThatThrownBy(() -> contractService.create(null))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建合同 - 参数校验：合同编号为空")
        void createContract_EmptyCode() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);

            // When & Then
            assertThatThrownBy(() -> contractService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建合同 - 参数校验：客户ID为空")
        void createContract_NullCustomerId() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CONTRACT-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(null);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);

            // When & Then
            assertThatThrownBy(() -> contractService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建合同 - 参数校验：金额为负")
        void createContract_NegativeAmount() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CONTRACT-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("-1000"));
            dto.setOwnerId(200L);

            // When & Then
            assertThatThrownBy(() -> contractService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建合同 - 参数校验：到期日期早于生效日期")
        void createContract_InvalidDates() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CONTRACT-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);
            dto.setEffectiveDate(LocalDate.of(2026, 12, 31));
            dto.setExpireDate(LocalDate.of(2026, 1, 1));

            // When & Then
            assertThatThrownBy(() -> contractService.create(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("创建合同 - 默认值设置")
        void createContract_DefaultValues() {
            // Given
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CONTRACT-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("100000"));
            dto.setOwnerId(200L);
            // 不设置 status 和 currency

            when(contractMapper.selectByCode("CONTRACT-001")).thenReturn(null);
            doAnswer(invocation -> {
                ContractDO entity = invocation.getArgument(0);
                entity.setId(1L);
                // 验证默认值
                assertThat(entity.getStatus()).isEqualTo(ContractStatus.DRAFT.getCode());
                assertThat(entity.getCurrency()).isEqualTo("CNY");
                return 1;
            }).when(contractMapper).insert(any(ContractDO.class));

            // When
            Long id = contractService.create(dto);

            // Then
            assertThat(id).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("updateContract 方法")
    class UpdateContractTest {

        @Test
        @DisplayName("更新合同 - 正常流程")
        void updateContract_Success() {
            // Given
            ContractStatusDTO dto = new ContractStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus(ContractStatus.SUBMITTED.getCode());

            ContractDO existing = new ContractDO();
            existing.setId(1L);
            existing.setStatus(ContractStatus.DRAFT.getCode());
            when(contractMapper.selectById(1L)).thenReturn(existing);
            when(contractMapper.updateStatus(1L, ContractStatus.SUBMITTED.getCode())).thenReturn(1);

            // When & Then
            assertThatCode(() -> contractService.changeStatus(dto)).doesNotThrowAnyException();
            verify(contractMapper).updateStatus(1L, ContractStatus.SUBMITTED.getCode());
        }

        @Test
        @DisplayName("更新合同 - 合同不存在")
        void updateContract_NotFound() {
            // Given
            ContractStatusDTO dto = new ContractStatusDTO();
            dto.setId(999L);
            dto.setTargetStatus(ContractStatus.ACTIVE.getCode());

            when(contractMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.changeStatus(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("更新合同 - 目标状态非法")
        void updateContract_InvalidTargetStatus() {
            // Given
            ContractStatusDTO dto = new ContractStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus("INVALID_STATUS");

            ContractDO existing = new ContractDO();
            existing.setId(1L);
            existing.setStatus(ContractStatus.DRAFT.getCode());
            when(contractMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.changeStatus(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("更新合同 - 状态迁移路径非法")
        void updateContract_InvalidTransition() {
            // Given
            ContractStatusDTO dto = new ContractStatusDTO();
            dto.setId(1L);
            dto.setTargetStatus(ContractStatus.ACTIVE.getCode());

            ContractDO existing = new ContractDO();
            existing.setId(1L);
            existing.setStatus(ContractStatus.DRAFT.getCode());
            when(contractMapper.selectById(1L)).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> contractService.changeStatus(dto))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("deleteContract 方法")
    class DeleteContractTest {

        @Test
        @DisplayName("删除合同 - 正常流程")
        void deleteContract_Success() {
            // Given
            ContractDO contract = new ContractDO();
            contract.setId(1L);
            when(contractMapper.selectById(1L)).thenReturn(contract);
            when(contractMapper.deleteById(1L)).thenReturn(1);

            // When & Then
            assertThatCode(() -> contractService.delete(1L)).doesNotThrowAnyException();
            verify(contractMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除合同 - 合同不存在")
        void deleteContract_NotFound() {
            // Given
            when(contractMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.delete(999L))
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
            ContractDO contract = new ContractDO();
            contract.setId(1L);
            contract.setContractCode("CONTRACT-001");
            when(contractMapper.selectById(1L)).thenReturn(contract);

            // When
            ContractDO result = contractService.getById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询 - 合同不存在")
        void getById_NotFound() {
            // Given
            when(contractMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.getById(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询 - 正常流程")
        void page_Success() {
            // Given
            when(contractMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<ContractDO> result = contractService.page(1, 10, null, null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(contractMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带关键词过滤")
        void page_WithKeyword() {
            // Given
            when(contractMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<ContractDO> result = contractService.page(1, 10, "测试", null, null, null);

            // Then
            assertThat(result).isNotNull();
            verify(contractMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("分页查询 - 带状态过滤")
        void page_WithStatus() {
            // Given
            when(contractMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<ContractDO> result = contractService.page(1, 10, null, ContractStatus.ACTIVE.getCode(), null, null);

            // Then
            assertThat(result).isNotNull();
            verify(contractMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("evaluateRisk 方法")
    class EvaluateRiskTest {

        @Test
        @DisplayName("风险评估 - 正常流程")
        void evaluateRisk_Success() {
            // Given
            ContractDO contract = new ContractDO();
            contract.setId(1L);
            contract.setTotalAmount(new BigDecimal("100000"));
            when(contractMapper.selectById(1L)).thenReturn(contract);
            when(contractMapper.updateById(any(ContractDO.class))).thenReturn(1);

            // When
            String riskLevel = contractService.evaluateRisk(1L);

            // Then
            assertThat(riskLevel).isNotNull();
            verify(contractMapper).updateById(any(ContractDO.class));
        }

        @Test
        @DisplayName("风险评估 - 合同不存在")
        void evaluateRisk_NotFound() {
            // Given
            when(contractMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> contractService.evaluateRisk(999L))
                    .isInstanceOf(BizException.class);
        }
    }
}
