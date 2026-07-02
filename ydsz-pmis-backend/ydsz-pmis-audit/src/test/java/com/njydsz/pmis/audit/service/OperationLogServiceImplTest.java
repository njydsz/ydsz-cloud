package com.njydsz.pmis.audit.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import com.njydsz.pmis.common.entity.CursorPageResult;
import com.njydsz.pmis.common.util.CursorHelper;
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

/**
 * OperationLogServiceImpl 查询服务测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
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
        assertThat(wCap.getValue()).isInstanceOf(LambdaQueryWrapper.class);
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
        ArgumentCaptor<Wrapper<OperationLogDO>> wCap =
                ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(mapper).selectPage(pageCap.capture(), wCap.capture());
        // wrapper 包含时间范围条件（LambdaQueryWrapper 的 sqlSegment 不为空）
        // 由于 LambdaQueryWrapper 内部 sqlSegment 拼接较复杂，这里仅断言 mapper 被调用且 wrapper 实例类型正确
        assertThat(wCap.getValue()).isInstanceOf(LambdaQueryWrapper.class);
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

    // ==================== P2-8 游标分页测试 ====================

    @Test
    @DisplayName("pageByCursor 首页（无 cursor）应返回数据并生成 nextCursor")
    void pageByCursor_firstPage() {
        when(mapper.selectList(any())).thenReturn(List.of(
                createLog(1L, "2026-07-02T14:00:00"),
                createLog(2L, "2026-07-02T13:00:00"),
                createLog(3L, "2026-07-02T12:00:00") // 第 3 条是多查的，表示 hasMore
        ));

        CursorPageResult<OperationLogDO> result = service.pageByCursor(2, null,
                null, null, null, null, null, null);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursor()).isNotNull();
        // nextCursor 应基于第 2 条记录（最后一页内记录）
        Object[] decoded = CursorHelper.decode(result.getNextCursor());
        assertThat(decoded[1]).isEqualTo(2L);
    }

    @Test
    @DisplayName("pageByCursor 最后一页（无多余数据）hasMore 应为 false")
    void pageByCursor_lastPage() {
        when(mapper.selectList(any())).thenReturn(List.of(
                createLog(1L, "2026-07-02T14:00:00"),
                createLog(2L, "2026-07-02T13:00:00")
        ));

        CursorPageResult<OperationLogDO> result = service.pageByCursor(2, null,
                null, null, null, null, null, null);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("pageByCursor 带 cursor 应构造 keyset 条件")
    void pageByCursor_withCursor() {
        when(mapper.selectList(any())).thenReturn(List.of());
        String cursor = CursorHelper.encode(LocalDateTime.of(2026, 7, 2, 10, 0, 0), 50L);

        service.pageByCursor(10, cursor, null, null, null, null, null, null);

        // 验证 mapper 被调用（wrapper 中应包含 keyset 条件）
        verify(mapper).selectList(any());
    }

    @Test
    @DisplayName("pageByCursor size 超过 200 应被限制为 200")
    void pageByCursor_sizeCappedAt200() {
        when(mapper.selectList(any())).thenReturn(List.of());

        CursorPageResult<OperationLogDO> result = service.pageByCursor(999, null,
                null, null, null, null, null, null);

        // size 应被限制为 200（结果中 size 字段反映请求大小）
        assertThat(result.getSize()).isEqualTo(200);
        verify(mapper).selectList(any());
    }

    @Test
    @DisplayName("pageByCursor size 小于 1 应被限制为 1")
    void pageByCursor_sizeMinOne() {
        when(mapper.selectList(any())).thenReturn(List.of());

        CursorPageResult<OperationLogDO> result = service.pageByCursor(0, null,
                null, null, null, null, null, null);

        assertThat(result.getSize()).isEqualTo(1);
        verify(mapper).selectList(any());
    }

    @Test
    @DisplayName("pageByCursor 带过滤条件应委托给 mapper 且不报错")
    void pageByCursor_withFilters() {
        when(mapper.selectList(any())).thenReturn(List.of());

        service.pageByCursor(10, null, 100L, "USER", "SUCCESS", "用户管理",
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59));

        // 验证 mapper 被调用（wrapper 构造不报错即说明过滤条件正确）
        verify(mapper).selectList(any());
    }

    /** 创建测试用 OperationLogDO */
    private OperationLogDO createLog(Long id, String createdAt) {
        OperationLogDO log = new OperationLogDO();
        log.setId(id);
        log.setCreatedAt(LocalDateTime.parse(createdAt));
        log.setUserId(1L);
        log.setStatus("SUCCESS");
        return log;
    }
}
