package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.user.entity.DictItemDO;
import com.njydsz.pmis.user.entity.DictTypeDO;
import com.njydsz.pmis.user.mapper.DictItemMapper;
import com.njydsz.pmis.user.mapper.DictTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DictServiceImpl 单元测试
 *
 * <p>P2-6 说明：{@code @Cacheable} / {@code @CachePut} 注解仅在 Spring 代理下生效；
 * 单元测试直接 new 实例（无代理），注解被忽略，方法按原始逻辑执行，
 * 因此本测试验证的是「缓存未命中时执行方法体」的行为。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DictServiceImpl 字典服务测试")
class DictServiceImplTest {

    private DictTypeMapper dictTypeMapper;
    private DictItemMapper dictItemMapper;
    private DictServiceImpl service;

    @BeforeEach
    void setUp() {
        dictTypeMapper = mock(DictTypeMapper.class);
        dictItemMapper = mock(DictItemMapper.class);
        service = new DictServiceImpl(dictTypeMapper, dictItemMapper);
    }

    @Test
    @DisplayName("listAllTypes 应透传 Mapper")
    void listAllTypes_shouldDelegateToMapper() {
        DictTypeDO t1 = new DictTypeDO();
        t1.setId(1L);
        t1.setTypeCode("gender");
        DictTypeDO t2 = new DictTypeDO();
        t2.setId(2L);
        t2.setTypeCode("status");
        when(dictTypeMapper.selectList(null)).thenReturn(List.of(t1, t2));

        List<DictTypeDO> result = service.listAllTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTypeCode()).isEqualTo("gender");
        verify(dictTypeMapper).selectList(null);
    }

    @Test
    @DisplayName("listItems 应透传 Mapper 按 typeCode 查询")
    void listItems_shouldQueryByTypeCode() {
        DictItemDO item1 = new DictItemDO();
        item1.setId(1L);
        item1.setItemCode("M");
        item1.setItemValue("男");
        DictItemDO item2 = new DictItemDO();
        item2.setId(2L);
        item2.setItemCode("F");
        item2.setItemValue("女");
        when(dictItemMapper.selectByTypeCode("gender")).thenReturn(List.of(item1, item2));

        List<DictItemDO> result = service.listItems("gender");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getItemCode()).isEqualTo("M");
        assertThat(result.get(1).getItemValue()).isEqualTo("女");
        verify(dictItemMapper).selectByTypeCode("gender");
    }

    @Test
    @DisplayName("listItems 空结果应返回空列表")
    void listItems_emptyResult_shouldReturnEmptyList() {
        when(dictItemMapper.selectByTypeCode("nonexistent")).thenReturn(List.of());

        List<DictItemDO> result = service.listItems("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("refreshCache 应查库并返回最新字典项")
    void refreshCache_shouldQueryDbAndReturnItems() {
        DictItemDO item = new DictItemDO();
        item.setId(1L);
        item.setItemCode("Y");
        item.setItemValue("是");
        when(dictItemMapper.selectByTypeCode("yes_no")).thenReturn(List.of(item));

        List<DictItemDO> result = service.refreshCache("yes_no");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemCode()).isEqualTo("Y");
        verify(dictItemMapper).selectByTypeCode("yes_no");
    }

    @Test
    @DisplayName("CACHE_NAME 常量应为 dict:items")
    void cacheName_constant() {
        assertThat(DictServiceImpl.CACHE_NAME).isEqualTo("dict:items");
    }
}
