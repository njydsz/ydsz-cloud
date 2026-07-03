package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.userinfo.entity.DictItemDO;
import com.njydsz.pmis.userinfo.entity.DictTypeDO;
import com.njydsz.pmis.userinfo.mapper.DictItemMapper;
import com.njydsz.pmis.userinfo.mapper.DictTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("字典服务测试")
class DictServiceImplTest {

    @Mock
    private DictTypeMapper dictTypeMapper;
    @Mock
    private DictItemMapper dictItemMapper;

    @InjectMocks
    private DictServiceImpl dictService;

    @Test
    @DisplayName("查询所有字典类型")
    void listAllTypes_shouldReturnTypeList() {
        DictTypeDO type1 = new DictTypeDO();
        type1.setId(1L);
        type1.setTypeCode("GENDER");
        type1.setTypeName("性别");

        when(dictTypeMapper.selectList(null)).thenReturn(List.of(type1));

        List<DictTypeDO> result = dictService.listAllTypes();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("GENDER", result.get(0).getTypeCode());
    }

    @Test
    @DisplayName("查询所有字典类型为空时返回空列表")
    void listAllTypes_empty_shouldReturnEmptyList() {
        when(dictTypeMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<DictTypeDO> result = dictService.listAllTypes();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("根据类型编码查询字典项")
    void listItems_shouldReturnItemList() {
        DictItemDO item = new DictItemDO();
        item.setId(1L);
        item.setTypeCode("GENDER");
        item.setItemCode("MALE");
        item.setItemValue("男");

        when(dictItemMapper.selectByTypeCode("GENDER")).thenReturn(List.of(item));

        List<DictItemDO> result = dictService.listItems("GENDER");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("MALE", result.get(0).getItemCode());
    }

    @Test
    @DisplayName("根据类型编码查询字典项为空时返回空列表")
    void listItems_empty_shouldReturnEmptyList() {
        when(dictItemMapper.selectByTypeCode("UNKNOWN")).thenReturn(Collections.emptyList());

        List<DictItemDO> result = dictService.listItems("UNKNOWN");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("刷新字典缓存")
    void refreshCache_shouldReturnLatestItems() {
        DictItemDO item = new DictItemDO();
        item.setId(1L);
        item.setTypeCode("GENDER");
        item.setItemCode("FEMALE");
        item.setItemValue("女");

        when(dictItemMapper.selectByTypeCode("GENDER")).thenReturn(List.of(item));

        List<DictItemDO> result = dictService.refreshCache("GENDER");
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(dictItemMapper).selectByTypeCode("GENDER");
    }
}