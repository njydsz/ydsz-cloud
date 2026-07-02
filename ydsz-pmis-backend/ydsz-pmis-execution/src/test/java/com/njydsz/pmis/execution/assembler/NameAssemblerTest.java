package com.njydsz.pmis.execution.assembler;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.feign.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NameAssembler 名称装配器测试")
class NameAssemblerTest {

    private UserServiceClient client;
    private NameAssembler assembler;

    @BeforeEach
    void setUp() {
        client = mock(UserServiceClient.class);
        assembler = new NameAssembler(client);
    }

    @Test
    @DisplayName("resolveEmployee - 成功")
    void ok() {
        when(client.getEmployee(1L)).thenReturn(Result.ok(Map.of("name", "张三")));
        assertThat(assembler.resolveEmployee(1L)).isEqualTo("张三");
    }

    @Test
    @DisplayName("resolveEmployee - 异常降级")
    void fail() {
        when(client.getEmployee(any())).thenThrow(new RuntimeException("down"));
        assertThat(assembler.resolveEmployee(1L)).isNull();
    }

    @Test
    @DisplayName("resolveEmployee - null id")
    void nullId() {
        assertThat(assembler.resolveEmployee(null)).isNull();
    }
}
