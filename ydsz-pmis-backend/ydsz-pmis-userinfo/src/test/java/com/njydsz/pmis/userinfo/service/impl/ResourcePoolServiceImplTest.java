package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.userinfo.entity.ResourcePoolDO;
import com.njydsz.pmis.userinfo.mapper.ResourcePoolMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("资源池服务测试")
class ResourcePoolServiceImplTest {

    @Mock
    private ResourcePoolMapper poolMapper;

    @InjectMocks
    private ResourcePoolServiceImpl resourcePoolService;

    @Test
    @DisplayName("创建资源池成功")
    void create_shouldInsertResourcePool() {
        ResourcePoolCreateDTO dto = new ResourcePoolCreateDTO();
        dto.setPoolCode("POOL001");
        dto.setPoolName("测试资源池");
        dto.setPoolType("HQ");

        when(poolMapper.selectByCode("POOL001")).thenReturn(null);
        doAnswer(invocation -> {
            ResourcePoolDO entity = invocation.getArgument(0);
            entity.setId(300L);
            return 1;
        }).when(poolMapper).insert(any(ResourcePoolDO.class));

        Long id = resourcePoolService.create(dto);
        assertNotNull(id);
        assertEquals(300L, id);
        verify(poolMapper).insert(any(ResourcePoolDO.class));
    }

    @Test
    @DisplayName("创建资源池时编码重复抛出异常")
    void create_duplicateCode_shouldThrowException() {
        ResourcePoolCreateDTO dto = new ResourcePoolCreateDTO();
        dto.setPoolCode("POOL001");
        dto.setPoolName("测试资源池");
        dto.setPoolType("HQ");

        ResourcePoolDO existing = new ResourcePoolDO();
        existing.setId(1L);
        existing.setPoolCode("POOL001");

        when(poolMapper.selectByCode("POOL001")).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> resourcePoolService.create(dto));
        assertEquals(10102, ex.getCode());
    }

    @Test
    @DisplayName("根据ID查询资源池")
    void getById_shouldReturnResourcePool() {
        ResourcePoolDO pool = new ResourcePoolDO();
        pool.setId(1L);
        pool.setPoolCode("POOL001");
        pool.setPoolName("测试资源池");

        when(poolMapper.selectById(1L)).thenReturn(pool);

        ResourcePoolDO result = resourcePoolService.getById(1L);
        assertNotNull(result);
        assertEquals("POOL001", result.getPoolCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的资源池时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(poolMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> resourcePoolService.getById(999L));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("根据类型查询资源池列表")
    void listByType_shouldReturnPoolList() {
        ResourcePoolDO pool = new ResourcePoolDO();
        pool.setId(1L);
        pool.setPoolCode("POOL001");
        pool.setPoolType("HQ");

        when(poolMapper.selectByType("HQ")).thenReturn(List.of(pool));

        List<ResourcePoolDO> result = resourcePoolService.listByType("HQ");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("HQ", result.get(0).getPoolType());
    }

    @Test
    @DisplayName("分页查询资源池")
    void page_shouldReturnPagedResult() {
        Page<ResourcePoolDO> mockPage = new Page<>(1, 10);
        ResourcePoolDO pool = new ResourcePoolDO();
        pool.setId(1L);
        pool.setPoolCode("POOL001");
        mockPage.setRecords(List.of(pool));
        mockPage.setTotal(1);

        when(poolMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<ResourcePoolDO> result = resourcePoolService.page(1, 10, "HQ", null);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("更新资源池")
    void update_shouldUpdateResourcePool() {
        ResourcePoolDO existing = new ResourcePoolDO();
        existing.setId(1L);
        existing.setPoolCode("POOL001");
        existing.setPoolName("旧名称");

        when(poolMapper.selectById(1L)).thenReturn(existing);
        when(poolMapper.updateById(any(ResourcePoolDO.class))).thenReturn(1);

        ResourcePoolCreateDTO dto = new ResourcePoolCreateDTO();
        dto.setPoolName("新名称");
        dto.setHeadcount(10);

        assertDoesNotThrow(() -> resourcePoolService.update(1L, dto));
        verify(poolMapper).updateById(any(ResourcePoolDO.class));
    }
}