package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.user.entity.ResourcePoolDO;
import com.njydsz.pmis.user.mapper.ResourcePoolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ResourcePoolServiceImpl 测试
 */
@DisplayName("ResourcePoolServiceImpl 资源池")
class ResourcePoolServiceImplTest {

    private ResourcePoolMapper mapper;
    private ResourcePoolServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ResourcePoolMapper.class);
        service = new ResourcePoolServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 编码重复抛 DUPLICATE_KEY")
    void create_duplicate() {
        ResourcePoolCreateDTO dto = baseDto();
        when(mapper.selectByCode("POOL-A")).thenReturn(new ResourcePoolDO());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 池类型无效抛 BAD_REQUEST")
    void create_invalidType() {
        ResourcePoolCreateDTO dto = baseDto();
        dto.setPoolType("WRONG");
        when(mapper.selectByCode("POOL-A")).thenReturn(null);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 必填校验")
    void create_validation() {
        ResourcePoolCreateDTO dto = new ResourcePoolCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功补齐默认值")
    void create_success() {
        ResourcePoolCreateDTO dto = baseDto();
        when(mapper.selectByCode("POOL-A")).thenReturn(null);
        when(mapper.insert(any(ResourcePoolDO.class))).thenAnswer(inv -> {
            ResourcePoolDO p = inv.getArgument(0);
            p.setId(1L);
            return 1;
        });
        Long id = service.create(dto);
        assertThat(id).isEqualTo(1L);
    }

    @Test
    @DisplayName("update 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(1L, baseDto()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("getById null 参数")
    void getById_null() {
        assertThatThrownBy(() -> service.getById(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("listByType 空字符串返回空列表")
    void listByType_empty() {
        assertThat(service.listByType("")).isEmpty();
    }

    @Test
    @DisplayName("listByDept 委托 mapper")
    void listByDept() {
        when(mapper.selectByDept(2L)).thenReturn(List.of(new ResourcePoolDO()));
        assertThat(service.listByDept(2L)).hasSize(1);
    }

    @Test
    @DisplayName("page 按 type 过滤")
    void page_filter() {
        when(mapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        var p = service.page(1, 10, "HQ", "ACTIVE");
        assertThat(p.getCurrent()).isEqualTo(1);
    }

    private ResourcePoolCreateDTO baseDto() {
        ResourcePoolCreateDTO d = new ResourcePoolCreateDTO();
        d.setPoolCode("POOL-A");
        d.setPoolName("池A");
        d.setPoolType("HQ");
        return d;
    }
}
