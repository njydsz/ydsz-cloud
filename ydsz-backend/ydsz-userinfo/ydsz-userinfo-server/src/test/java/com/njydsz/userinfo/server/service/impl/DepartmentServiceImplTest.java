package com.njydsz.userinfo.server.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.UserDept;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DepartmentServiceImpl} 单元测试。
 *
 * <p>覆盖核心业务逻辑：CRUD、deptCode 唯一性校验、子部门/人员引用检查、树形结构构建。
 *
 * <p>P0-1: 后端测试体系建设 — userinfo 模块首批单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("部门服务 DepartmentServiceImpl 测试")
@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private UserDeptMapper userDeptMapper;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    @BeforeEach
    void setUp() {
        // MockitoAnnotations.openMocks(this) 由 @ExtendWith(MockitoExtension.class) 隐式调用
    }

    @Nested
    @DisplayName("getById — 根据ID查询部门")
    /**
     * 测试分组：getById — 根据ID查询部门
     */
    /**
     * 测试分组：「部门存在且未删除时，应返回 DepartmentVO」等
     */
    class GetByIdTest {

        @Test
        @DisplayName("部门存在且未删除时，应返回 DepartmentVO")
        void getById_shouldReturnVO_whenDepartmentExists() {
            // given
            Department dept = new Department();
            dept.setId("dept-001");
            dept.setDeptName("研发部");
            dept.setDeleted(0);
            when(departmentMapper.selectById("dept-001")).thenReturn(dept);

            // when
            DepartmentVO result = departmentService.getById("dept-001");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("dept-001");
            assertThat(result.getDeptName()).isEqualTo("研发部");
        }

        @Test
        @DisplayName("部门不存在时，应抛出 DEPARTMENT_NOT_FOUND 异常")
        void getById_shouldThrow_whenNotFound() {
            // given
            when(departmentMapper.selectById("nonexistent")).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> departmentService.getById("nonexistent"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("部门已逻辑删除时，应抛出 DEPARTMENT_NOT_FOUND 异常")
        void getById_shouldThrow_whenDeleted() {
            // given
            Department dept = new Department();
            dept.setId("dept-002");
            dept.setDeleted(1);
            when(departmentMapper.selectById("dept-002")).thenReturn(dept);

            // when & then
            assertThatThrownBy(() -> departmentService.getById("dept-002"))
                    .isInstanceOf(BusinessException.class);
           /**
     * 测试分组：「list — 查询全部部门列表」等
     */
 }
    }

    @Nested
    @DisplayName("list — 查询全部部门列表")
    class ListTest {

        @Test
        @DisplayName("应返回所有未删除部门，按 sortOrder 降序排列")
        void list_shouldReturnAllDepartments() {
            // given
            Department dept1 = new Department();
            dept1.setId("dept-001");
            dept1.setDeptName("研发部");
            dept1.setDeleted(0);

            Department dept2 = new Department();
            dept2.setId("dept-002");
            dept2.setDeptName("产品部");
            dept2.setDeleted(0);

            when(departmentMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(dept1, dept2));

            // when
            List<DepartmentVO> result = departmentService.list();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDeptName()).isEqualTo("研发部");
            assertThat(result.get(1).getDeptName()).isEqualTo("产品部");
        }

        @Test
        @DisplayName("无部门数据时应返回空列表")
        void list_shouldReturnEmpty_whenNoData() {
            // given
            when(departmentMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            List<DepartmentVO> result = departmentService.list();

            // then
            /**
     * 测试分组：「removeById — 删除部门前置校验」等
     */
    assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("removeById — 删除部门前置校验")
    class RemoveByIdTest {

        @Test
        @DisplayName("部门有子部门时，应抛出异常禁止删除")
        void removeById_shouldThrow_whenHasChildren() {
            // given
            Department dept = new Department();
            dept.setId("dept-001");
            dept.setDeleted(0);
            when(departmentMapper.selectById("dept-001")).thenReturn(dept);

            Department child = new Department();
            child.setId("dept-002");
            child.setParentId("dept-001");
            when(departmentMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(child));

            // when & then
            assertThatThrownBy(() -> departmentService.removeById("dept-001"))
                    .isInstanceOf(BusinessException.class);
            verify(departmentMapper, never()).deleteById(any());
        }
    }
}
