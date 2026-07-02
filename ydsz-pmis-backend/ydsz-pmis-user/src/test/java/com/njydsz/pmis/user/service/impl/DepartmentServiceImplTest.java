package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.DepartmentFormDTO;
import com.njydsz.pmis.user.entity.DepartmentDO;
import com.njydsz.pmis.user.mapper.DepartmentMapper;
import com.njydsz.pmis.user.vo.DepartmentTreeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DepartmentServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DepartmentServiceImpl 部门服务测试")
class DepartmentServiceImplTest {

    private DepartmentMapper mapper;
    private DepartmentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(DepartmentMapper.class);
        service = new DepartmentServiceImpl(mapper);
    }

    @Test
    @DisplayName("tree 列表构建")
    void tree() {
        DepartmentDO root = dept(1L, "ROOT", 0L, "/1");
        DepartmentDO child1 = dept(2L, "C1", 1L, "/1/2");
        DepartmentDO child2 = dept(3L, "C2", 1L, "/1/3");
        DepartmentDO grand = dept(4L, "G1", 2L, "/1/2/4");
        when(mapper.selectAllEnabled()).thenReturn(List.of(root, child1, child2, grand));

        List<DepartmentTreeVO> tree = service.tree();
        assertThat(tree).hasSize(1);
        DepartmentTreeVO rootNode = tree.get(0);
        assertThat(rootNode.getDepartment().getId()).isEqualTo(1L);
        assertThat(rootNode.getChildren()).hasSize(2);
        DepartmentTreeVO child1Node = rootNode.getChildren().stream()
                .filter(n -> n.getDepartment().getId().equals(2L)).findFirst().orElseThrow();
        assertThat(child1Node.getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("getById 不存在应抛 DEPARTMENT_NOT_FOUND")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DEPARTMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("create 编码重复应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(mapper.selectByCode("C1")).thenReturn(dept(1L, "C1", 0L, "/1"));
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("C1");
        dto.setDeptName("C1");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 父部门不存在应抛 DEPARTMENT_NOT_FOUND")
    void create_parentNotFound() {
        when(mapper.selectByCode("C1")).thenReturn(null);
        when(mapper.selectById(99L)).thenReturn(null);
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("C1");
        dto.setDeptName("C1");
        dto.setParentId(99L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DEPARTMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("create 顶层部门 deptPath 应为 /id")
    void create_root() {
        when(mapper.selectByCode("NEW")).thenReturn(null);
        when(mapper.selectById(any())).thenAnswer(inv -> {
            if (inv.getArgument(0) == null) return null;
            return null;
        });
        // 模拟插入后 id 自增
        when(mapper.insert(any(DepartmentDO.class))).thenAnswer(inv -> {
            DepartmentDO d = inv.getArgument(0);
            d.setId(10L);
            return 1;
        });
        when(mapper.selectById(10L)).thenReturn(null);

        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("NEW");
        dto.setDeptName("新部门");
        dto.setParentId(0L);

        Long id = service.create(dto);
        assertThat(id).isEqualTo(10L);

        ArgumentCaptor<DepartmentDO> cap = ArgumentCaptor.forClass(DepartmentDO.class);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.atLeastOnce()).updateById(cap.capture());
        DepartmentDO last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getDeptPath()).isEqualTo("/10");
    }

    @Test
    @DisplayName("create 父部门存在应拼路径")
    void create_withParent() {
        when(mapper.selectByCode("C2")).thenReturn(null);
        when(mapper.selectById(2L)).thenReturn(dept(2L, "PARENT", 1L, "/1/2"));
        when(mapper.insert(any(DepartmentDO.class))).thenAnswer(inv -> {
            DepartmentDO d = inv.getArgument(0);
            d.setId(20L);
            return 1;
        });

        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("C2");
        dto.setDeptName("子部门");
        dto.setParentId(2L);

        Long id = service.create(dto);
        assertThat(id).isEqualTo(20L);

        ArgumentCaptor<DepartmentDO> cap = ArgumentCaptor.forClass(DepartmentDO.class);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.atLeastOnce()).updateById(cap.capture());
        DepartmentDO last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getDeptPath()).isEqualTo("/1/2/20");
    }

    @Test
    @DisplayName("update ID 为空应抛 BAD_REQUEST")
    void update_noId() {
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("X");
        dto.setDeptName("X");
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("update ID 不存在应抛 DEPARTMENT_NOT_FOUND")
    void update_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setId(99L);
        dto.setDeptCode("X");
        dto.setDeptName("X");
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DEPARTMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("update 父部门是自身应抛 BAD_REQUEST")
    void update_selfAsParent() {
        when(mapper.selectById(1L)).thenReturn(dept(1L, "C1", 0L, "/1"));
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setId(1L);
        dto.setDeptCode("C1");
        dto.setDeptName("C1");
        dto.setParentId(1L);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("delete 存在子部门应拒绝")
    void delete_hasChild() {
        when(mapper.selectById(1L)).thenReturn(dept(1L, "C1", 0L, "/1"));
        when(mapper.selectByParentId(1L)).thenReturn(List.of(dept(2L, "C2", 1L, "/1/2")));
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("delete 无子部门应通过")
    void delete_ok() {
        when(mapper.selectById(1L)).thenReturn(dept(1L, "C1", 0L, "/1"));
        when(mapper.selectByParentId(1L)).thenReturn(List.of());
        service.delete(1L);
        org.mockito.Mockito.verify(mapper).deleteById(1L);
    }

    private DepartmentDO dept(Long id, String code, Long parentId, String path) {
        DepartmentDO d = new DepartmentDO();
        d.setId(id);
        d.setDeptCode(code);
        d.setDeptName(code + "name");
        d.setParentId(parentId);
        d.setDeptPath(path);
        d.setStatus("ENABLED");
        return d;
    }
}
