package com.njydsz.pmis.project.assembler;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.feign.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("名称装配器测试")
class NameAssemblerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private NameAssembler nameAssembler;

    @Test
    @DisplayName("解析员工姓名 - 成功")
    void shouldResolveEmployeeName() {
        when(userServiceClient.getEmployee(1L))
                .thenReturn(Result.ok(Map.of("name", "张三")));

        String name = nameAssembler.resolveEmployee(1L);
        assertEquals("张三", name);
    }

    @Test
    @DisplayName("解析员工姓名 - id 为 null 返回 null")
    void shouldReturnNullWhenEmployeeIdIsNull() {
        String name = nameAssembler.resolveEmployee(null);
        assertNull(name);
    }

    @Test
    @DisplayName("解析员工姓名 - 接口返回 null 时返回 null")
    void shouldReturnNullWhenResultIsNull() {
        when(userServiceClient.getEmployee(1L)).thenReturn(null);

        String name = nameAssembler.resolveEmployee(1L);
        assertNull(name);
    }

    @Test
    @DisplayName("解析员工姓名 - 接口异常时返回 null（降级）")
    void shouldReturnNullOnException() {
        when(userServiceClient.getEmployee(1L))
                .thenThrow(new RuntimeException("服务不可用"));

        String name = nameAssembler.resolveEmployee(1L);
        assertNull(name);
    }

    @Test
    @DisplayName("解析客户名称 - 成功")
    void shouldResolveCustomerName() {
        when(userServiceClient.getCustomerName(1L))
                .thenReturn(Result.ok("客户A"));

        String name = nameAssembler.resolveCustomer(1L);
        assertEquals("客户A", name);
    }

    @Test
    @DisplayName("解析客户名称 - id 为 null 返回 null")
    void shouldReturnNullWhenCustomerIdIsNull() {
        String name = nameAssembler.resolveCustomer(null);
        assertNull(name);
    }

    @Test
    @DisplayName("批量解析员工姓名")
    void shouldResolveBatchEmployeeNames() {
        Map<Long, String> expected = Map.of(1L, "张三", 2L, "李四");
        when(userServiceClient.batchEmployeeName(List.of(1L, 2L)))
                .thenReturn(Result.ok(expected));

        Map<Long, String> result = nameAssembler.batchEmployeeName(List.of(1L, 2L));
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("批量解析员工姓名 - 空列表返回空 Map")
    void shouldReturnEmptyMapWhenIdsIsEmpty() {
        Map<Long, String> result = nameAssembler.batchEmployeeName(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("批量解析客户名称")
    void shouldResolveBatchCustomerNames() {
        Map<Long, String> expected = Map.of(1L, "客户A", 2L, "客户B");
        when(userServiceClient.batchCustomerName(List.of(1L, 2L)))
                .thenReturn(Result.ok(expected));

        Map<Long, String> result = nameAssembler.batchCustomerName(List.of(1L, 2L));
        assertEquals(expected, result);
    }
}