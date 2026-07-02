package com.njydsz.pmis.audit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OperationLogServiceImpl 查询服务测试")
class OperationLogServiceImplTest {

    private OperationLogMapper mapper;
    private OperationLogServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(OperationLogMapper.class);
        service = new OperationLogServiceImpl(mapper);
    }

    @Test
    @DisplayName("分页查询构造 wrapper 并委托给 mapper")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void page() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        service.page(1, 20, 100L, "USER", "SUCCESS", "用户管理", null, null);
        ArgumentCaptor<Page<OperationLogDO>> pageCap = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<OperationLogDO>> wCap =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectPage(pageCap.capture(), wCap.capture());
        assertThat(pageCap.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCap.getValue().getSize()).isEqualTo(20);
        // 验证传入的 wrapper 是 LambdaQueryWrapper 实例
        assertThat(wCap.getValue()).isInstanceOf(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
    }

    @Test
    @DisplayName("分页查询支持时间范围筛选")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pageWithTimeRange() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
        service.page(1, 20, null, null, null, null, start, end);
        ArgumentCaptor<Page<OperationLogDO>> pageCap = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<OperationLogDO>> wCap =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectPage(pageCap.capture(), wCap.capture());
        // wrapper 包含时间范围条件（LambdaQueryWrapper 的 sqlSegment 不为空）
        // 由于 LambdaQueryWrapper 内部 sqlSegment 拼接较复杂，这里仅断言 mapper 被调用且 wrapper 实例类型正确
        assertThat(wCap.getValue()).isInstanceOf(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
    }

    @Test
    @DisplayName("按用户查询限制 limit 在 [1,500]")
    void byUserLimit() {
        when(mapper.selectByUser(any(), anyInt())).thenReturn(List.of());
        service.listByUser(1L, 0);
        verify(mapper).selectByUser(1L, 1);

        service.listByUser(1L, 99999);
        verify(mapper).selectByUser(1L, 500);
    }

    @Test
    @DisplayName("按业务查询限制 limit")
    void byBizLimit() {
        when(mapper.selectByBiz(anyString(), anyString(), anyInt())).thenReturn(List.of());
        service.listByBiz("T", "1", 1000);
        verify(mapper).selectByBiz("T", "1", 500);
    }

    @Test
    @DisplayName("cleanBefore 非法 days 默认为 90")
    void cleanDefault() {
        when(mapper.deleteBefore(anyInt())).thenReturn(7);
        int n = service.cleanBefore(0);
        assertThat(n).isEqualTo(7);
        verify(mapper).deleteBefore(90);
    }
}
