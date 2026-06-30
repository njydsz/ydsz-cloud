package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.PermissionFormDTO;
import com.njydsz.pmis.user.entity.PermissionDO;
import com.njydsz.pmis.user.mapper.PermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PermissionServiceImpl 单元测试
 */
@DisplayName("PermissionServiceImpl 权限服务测试")
class PermissionServiceImplTest {

    private PermissionMapper mapper;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PermissionMapper.class);
        service = new PermissionServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 权限编码重复应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(mapper.selectByCode("system:user:create")).thenReturn(perm(1L, "system:user:create"));
        PermissionFormDTO dto = new PermissionFormDTO();
        dto.setPermCode("system:user:create");
        dto.setPermName("创建用户");
        dto.setPermType("BUTTON");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 应设置默认 visible=1 parentId=0")
    void create_defaults() {
        when(mapper.selectByCode("system:user:list")).thenReturn(null);
        when(mapper.insert(any(PermissionDO.class))).thenAnswer(inv -> {
            PermissionDO p = inv.getArgument(0);
            p.setId(10L);
            return 1;
        });
        PermissionFormDTO dto = new PermissionFormDTO();
        dto.setPermCode("system:user:list");
        dto.setPermName("用户列表");
        dto.setPermType("MENU");

        Long id = service.create(dto);
        assertThat(id).isEqualTo(10L);

        org.mockito.ArgumentCaptor<PermissionDO> cap = org.mockito.ArgumentCaptor.forClass(PermissionDO.class);
        org.mockito.Mockito.verify(mapper).insert(cap.capture());
        PermissionDO p = cap.getValue();
        assertThat(p.getVisible()).isEqualTo(1);
        assertThat(p.getParentId()).isEqualTo(0L);
        assertThat(p.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("delete 存在子权限应拒绝")
    void delete_hasChild() {
        when(mapper.selectCount(any())).thenReturn(2L);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("delete 无子权限应通过")
    void delete_ok() {
        when(mapper.selectCount(any())).thenReturn(0L);
        service.delete(1L);
        org.mockito.Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("listPermCodesByUserId 应透传 Mapper")
    void listByUser() {
        when(mapper.selectPermCodesByUserId(7L)).thenReturn(java.util.List.of("a", "b"));
        assertThat(service.listPermCodesByUserId(7L)).containsExactly("a", "b");
    }

    private PermissionDO perm(Long id, String code) {
        PermissionDO p = new PermissionDO();
        p.setId(id);
        p.setPermCode(code);
        p.setPermName(code);
        p.setPermType("BUTTON");
        p.setStatus("ENABLED");
        return p;
    }
}
