package com.njydsz.pmis.project.assembler;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NameAssembler 名称装配器测试")
class NameAssemblerTest {

    private UserServiceClient userServiceClient;
    private NameAssembler assembler;

    @BeforeEach
    void setUp() {
        userServiceClient = mock(UserServiceClient.class);
        assembler = new NameAssembler(userServiceClient);
    }

    @Test
    @DisplayName("resolveEmployee - 成功获取 name")
    void resolveEmployeeName() {
        when(userServiceClient.getEmployee(1L))
                .thenReturn(Result.ok(Map.of("name", "张三", "userId", 1)));
        String n = assembler.resolveEmployee(1L);
        assertThat(n).isEqualTo("张三");
    }

    @Test
    @DisplayName("resolveEmployee - 兼容 realName 字段")
    void resolveEmployeeRealName() {
        when(userServiceClient.getEmployee(2L))
                .thenReturn(Result.ok(Map.of("realName", "李四")));
        String n = assembler.resolveEmployee(2L);
        assertThat(n).isEqualTo("李四");
    }

    @Test
    @DisplayName("resolveEmployee - Feign 异常返回 null")
    void resolveEmployeeFail() {
        when(userServiceClient.getEmployee(any()))
                .thenThrow(new RuntimeException("down"));
        String n = assembler.resolveEmployee(1L);
        assertThat(n).isNull();
    }

    @Test
    @DisplayName("resolveEmployee - null id 返回 null")
    void resolveEmployeeNull() {
        assertThat(assembler.resolveEmployee(null)).isNull();
    }

    @Test
    @DisplayName("resolveCustomer - 成功")
    void resolveCustomer() {
        when(userServiceClient.getCustomerName(100L)).thenReturn(Result.ok("客户甲"));
        String n = assembler.resolveCustomer(100L);
        assertThat(n).isEqualTo("客户甲");
    }

    @Test
    @DisplayName("resolveCustomer - 失败降级")
    void resolveCustomerFail() {
        when(userServiceClient.getCustomerName(any()))
                .thenThrow(new RuntimeException("down"));
        assertThat(assembler.resolveCustomer(100L)).isNull();
    }

    @Test
    @DisplayName("resolveCustomer - null id 返回 null")
    void resolveCustomerNull() {
        assertThat(assembler.resolveCustomer(null)).isNull();
    }

    @Test
    @DisplayName("batchEmployeeName - 成功")
    void batchEmployeeNameOk() {
        when(userServiceClient.batchEmployeeName(List.of(1L, 2L)))
                .thenReturn(Result.ok(Map.of(1L, "A", 2L, "B")));
        Map<Long, String> m = assembler.batchEmployeeName(List.of(1L, 2L));
        assertThat(m).containsEntry(1L, "A").containsEntry(2L, "B");
    }

    @Test
    @DisplayName("batchEmployeeName - null/empty 返回空 map")
    void batchEmployeeNameEmpty() {
        assertThat(assembler.batchEmployeeName(null)).isEmpty();
        assertThat(assembler.batchEmployeeName(List.of())).isEmpty();
    }

    @Test
    @DisplayName("batchEmployeeName - 异常降级")
    void batchEmployeeNameFail() {
        when(userServiceClient.batchEmployeeName(any()))
                .thenThrow(new RuntimeException("down"));
        assertThat(assembler.batchEmployeeName(List.of(1L))).isEmpty();
    }
}
