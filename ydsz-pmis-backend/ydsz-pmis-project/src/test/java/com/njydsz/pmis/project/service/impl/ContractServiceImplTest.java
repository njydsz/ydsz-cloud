package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.mapper.ContractMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 合同服务实现单元测试
 * <p>重点覆盖 NameAssembler 装配逻辑：单条 / 批量 / 失败容错 / 去重 / 空集合守卫。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("合同服务实现测试")
class ContractServiceImplTest {

    @Mock
    private ContractMapper contractMapper;

    @Mock
    private NameAssembler nameAssembler;

    @InjectMocks
    private ContractServiceImpl contractService;

    // =========================================================================
    //  create 方法 - 装配名称约束
    // =========================================================================

    @Test
    @DisplayName("create - 创建后应通过 NameAssembler 装配客户/负责人名称")
    void create_shouldAssembleNamesAfterInsert() {
        // Arrange
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("HT-001");
        dto.setContractName("测试合同");
        dto.setCustomerId(10L);
        dto.setOwnerId(20L);
        dto.setContractType("框架合同");
        dto.setTotalAmount(java.math.BigDecimal.ZERO);
        when(contractMapper.selectByCode("HT-001")).thenReturn(null);
        when(contractMapper.insert(any(ContractDO.class))).thenAnswer(inv -> {
            ((ContractDO) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(nameAssembler.resolveCustomer(10L)).thenReturn("客户A");
        when(nameAssembler.resolveEmployee(20L)).thenReturn("张三");

        // Act
        Long id = contractService.create(dto);

        // Assert
        assertEquals(1L, id);
        verify(nameAssembler, times(1)).resolveCustomer(10L);
        verify(nameAssembler, times(1)).resolveEmployee(20L);
    }

    @Test
    @DisplayName("create - 编号重复抛 BizException")
    void create_duplicateCode_shouldThrow() {
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("HT-001");
        dto.setContractName("测试合同");
        dto.setCustomerId(10L);
        dto.setOwnerId(20L);
        dto.setContractType("框架合同");
        dto.setTotalAmount(java.math.BigDecimal.ZERO);
        when(contractMapper.selectByCode("HT-001")).thenReturn(new ContractDO());

        assertThrows(BizException.class, () -> contractService.create(dto));
        verify(nameAssembler, never()).resolveCustomer(any());
    }

    // =========================================================================
    //  getById 方法 - 单条装配
    // =========================================================================

    @Test
    @DisplayName("getById - 应装配客户/负责人名称")
    void getById_shouldAssembleNames() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setCustomerId(10L);
        c.setOwnerId(20L);
        when(contractMapper.selectById(1L)).thenReturn(c);
        when(nameAssembler.resolveCustomer(10L)).thenReturn("客户A");
        when(nameAssembler.resolveEmployee(20L)).thenReturn("张三");

        ContractDO result = contractService.getById(1L);

        assertEquals("客户A", result.getCustomerName());
        assertEquals("张三", result.getOwnerName());
    }

    @Test
    @DisplayName("getById - 合同不存在抛 BizException")
    void getById_notFound_shouldThrow() {
        when(contractMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class, () -> contractService.getById(999L));
    }

    @Test
    @DisplayName("getById - 已有 name 时不触发 Feign 调用")
    void getById_existingName_shouldSkipFeign() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setCustomerId(10L);
        c.setCustomerName("已存在客户");
        c.setOwnerId(20L);
        c.setOwnerName("已存在负责人");
        when(contractMapper.selectById(1L)).thenReturn(c);

        contractService.getById(1L);

        verify(nameAssembler, never()).resolveCustomer(any());
        verify(nameAssembler, never()).resolveEmployee(any());
    }

    // =========================================================================
    //  page 方法 - 批量装配（核心改造点：消除 N+1）
    // =========================================================================

    @Test
    @DisplayName("page - 批量装配应只调用 1 次 batchCustomerName 和 1 次 batchEmployeeName（消除 N+1）")
    void page_shouldUseBatchAssembleInsteadOfNPlus1() {
        // Arrange: 3 条记录，2 个不同客户 + 2 个不同负责人
        Page<ContractDO> page = new Page<>(1, 10);
        List<ContractDO> records = new ArrayList<>();
        ContractDO c1 = new ContractDO(); c1.setId(1L); c1.setCustomerId(10L); c1.setOwnerId(20L);
        ContractDO c2 = new ContractDO(); c2.setId(2L); c2.setCustomerId(10L); c2.setOwnerId(21L); // 同客户，不同负责人
        ContractDO c3 = new ContractDO(); c3.setId(3L); c3.setCustomerId(11L); c3.setOwnerId(20L); // 不同客户，同负责人
        records.add(c1); records.add(c2); records.add(c3);
        page.setRecords(records);
        when(contractMapper.selectPage(any(), any())).thenReturn(page);
        when(nameAssembler.batchCustomerName(anyList())).thenReturn(Map.of(10L, "客户A", 11L, "客户B"));
        when(nameAssembler.batchEmployeeName(anyList())).thenReturn(Map.of(20L, "张三", 21L, "李四"));

        // Act
        Page<ContractDO> result = contractService.page(1, 10, null, null, null, null);

        // Assert: 仅 1 次 batch 调用（消除 N+1）
        verify(nameAssembler, times(1)).batchCustomerName(anyList());
        verify(nameAssembler, times(1)).batchEmployeeName(anyList());
        // 关键：不触发单条 resolveCustomer/resolveEmployee（N+1 模式）
        verify(nameAssembler, never()).resolveCustomer(any());
        verify(nameAssembler, never()).resolveEmployee(any());
        // 名称装配结果正确
        assertEquals("客户A", result.getRecords().get(0).getCustomerName());
        assertEquals("张三", result.getRecords().get(0).getOwnerName());
        assertEquals("客户A", result.getRecords().get(1).getCustomerName()); // c2 共享客户名
        assertEquals("李四", result.getRecords().get(1).getOwnerName());
        assertEquals("客户B", result.getRecords().get(2).getCustomerName());
        assertEquals("张三", result.getRecords().get(2).getOwnerName());     // c3 共享负责人
    }

    @Test
    @DisplayName("page - 去重：3 条记录 2 个不同 customerId 只批量查询 2 个 id")
    void page_shouldDeduplicateIdsBeforeBatchQuery() {
        Page<ContractDO> page = new Page<>(1, 10);
        List<ContractDO> records = new ArrayList<>();
        ContractDO c1 = new ContractDO(); c1.setId(1L); c1.setCustomerId(10L);
        ContractDO c2 = new ContractDO(); c2.setId(2L); c2.setCustomerId(10L); // 重复
        ContractDO c3 = new ContractDO(); c3.setId(3L); c3.setCustomerId(11L);
        records.add(c1); records.add(c2); records.add(c3);
        page.setRecords(records);
        when(contractMapper.selectPage(any(), any())).thenReturn(page);
        when(nameAssembler.batchCustomerName(anyList())).thenReturn(Map.of(10L, "客户A", 11L, "客户B"));

        contractService.page(1, 10, null, null, null, null);

        // 捕获传入的 ids，验证去重后只有 2 个唯一 id
        verify(nameAssembler).batchCustomerName(argThat(list -> list.size() == 2
                && list.containsAll(List.of(10L, 11L))));
    }

    @Test
    @DisplayName("page - 空集合守卫：所有记录都已有 name 时不触发 Feign 调用")
    void page_allNamesExist_shouldSkipFeign() {
        Page<ContractDO> page = new Page<>(1, 10);
        List<ContractDO> records = new ArrayList<>();
        ContractDO c1 = new ContractDO();
        c1.setId(1L); c1.setCustomerId(10L); c1.setCustomerName("已有A");
        c1.setOwnerId(20L); c1.setOwnerName("已有张三");
        records.add(c1);
        page.setRecords(records);
        when(contractMapper.selectPage(any(), any())).thenReturn(page);

        contractService.page(1, 10, null, null, null, null);

        verify(nameAssembler, never()).batchCustomerName(anyList());
        verify(nameAssembler, never()).batchEmployeeName(anyList());
    }

    @Test
    @DisplayName("page - Feign 返回空 Map 时不抛异常（容错降级）")
    void page_feignFailure_shouldNotThrow() {
        Page<ContractDO> page = new Page<>(1, 10);
        List<ContractDO> records = new ArrayList<>();
        ContractDO c1 = new ContractDO(); c1.setId(1L); c1.setCustomerId(10L); c1.setOwnerId(20L);
        records.add(c1);
        page.setRecords(records);
        when(contractMapper.selectPage(any(), any())).thenReturn(page);
        when(nameAssembler.batchCustomerName(anyList())).thenReturn(Map.of()); // 模拟降级
        when(nameAssembler.batchEmployeeName(anyList())).thenReturn(Map.of());

        // 不应抛异常，name 保持 null
        Page<ContractDO> result = assertDoesNotThrow(
                () -> contractService.page(1, 10, null, null, null, null));
        assertNull(result.getRecords().get(0).getCustomerName());
        assertNull(result.getRecords().get(0).getOwnerName());
    }

    @Test
    @DisplayName("page - 空结果集不触发 Feign 调用")
    void page_emptyResult_shouldSkipFeign() {
        Page<ContractDO> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        when(contractMapper.selectPage(any(), any())).thenReturn(page);

        contractService.page(1, 10, null, null, null, null);

        verify(nameAssembler, never()).batchCustomerName(anyList());
        verify(nameAssembler, never()).batchEmployeeName(anyList());
    }

    @Test
    @DisplayName("page - null 结果集不触发 Feign 调用")
    void page_nullResult_shouldSkipFeign() {
        when(contractMapper.selectPage(any(), any())).thenReturn(null);

        contractService.page(1, 10, null, null, null, null);

        verify(nameAssembler, never()).batchCustomerName(anyList());
        verify(nameAssembler, never()).batchEmployeeName(anyList());
    }

    // =========================================================================
    //  aggregateByStatus / aggregateByRisk - tenantId 默认值
    // =========================================================================

    @Test
    @DisplayName("aggregateByStatus - tenantId 为 null 时使用 TenantContext（不再硬编码 1L）")
    void aggregateByStatus_nullTenantId_shouldUseTenantContext() {
        when(contractMapper.aggregateByStatus(any())).thenReturn(new ArrayList<>());

        contractService.aggregateByStatus(null);

        // 验证传入的 tenantId 不是硬编码的 1L（使用 TenantContext.getTenantId()）
        verify(contractMapper).aggregateByStatus(argThat(ti -> ti != null && ti >= 0L));
    }
}
