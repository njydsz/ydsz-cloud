package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.user.EmployeeCreateDTO;
import com.njydsz.pmis.userinfo.dto.user.EmployeeUpdateDTO;
import com.njydsz.pmis.userinfo.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.entity.user.EmployeeDO;
import com.njydsz.pmis.userinfo.entity.rate.JobLevelDO;
import com.njydsz.pmis.userinfo.entity.rate.OutsourceRateDO;
import com.njydsz.pmis.userinfo.entity.rate.PartTimeRateDO;
import com.njydsz.pmis.userinfo.entity.org.PositionDO;
import com.njydsz.pmis.userinfo.mapper.org.DepartmentMapper;
import com.njydsz.pmis.userinfo.mapper.user.EmployeeMapper;
import com.njydsz.pmis.userinfo.mapper.rate.JobLevelMapper;
import com.njydsz.pmis.userinfo.mapper.rate.JobLevelRateMapper;
import com.njydsz.pmis.userinfo.mapper.rate.OutsourceRateMapper;
import com.njydsz.pmis.userinfo.mapper.rate.PartTimeRateMapper;
import com.njydsz.pmis.userinfo.mapper.org.PositionMapper;
import com.njydsz.pmis.userinfo.vo.user.EmployeeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmployeeServiceImpl 单元测试
 *
 * <p>覆盖员工 CRUD 核心行为与业务规则校验：empCode 唯一性、雇佣类型与兼职费率联动、
 * 在职状态流转、默认值填充、外键名称装配降级、成本档案查询（全职/兼职/外包）。使用 Mockito 纯 mock，不启动 Spring 上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeServiceImpl 员工服务测试")
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private JobLevelMapper jobLevelMapper;
    @Mock
    private JobLevelRateMapper jobLevelRateMapper;
    @Mock
    private PartTimeRateMapper partTimeRateMapper;
    @Mock
    private OutsourceRateMapper outsourceRateMapper;

    @InjectMocks
    private EmployeeServiceImpl service;

    // ==================== create ====================

    @Test
    @DisplayName("创建成功: 默认 FULL_TIME / ACTIVE，写入并返回 ID")
    void create_success() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");
        dto.setDepartmentId("D1");
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(null);
        // 模拟 MyBatis-Plus 雪花算法回填主键
        doAnswer(inv -> {
            inv.<EmployeeDO>getArgument(0).setId("EMP-GENERATED");
            return 1;
        }).when(employeeMapper).insert(any(EmployeeDO.class));

        String id = service.create(dto);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeMapper).insert(captor.capture());
        EmployeeDO saved = captor.getValue();
        assertEquals("E001", saved.getEmpCode());
        assertEquals("FULL_TIME", saved.getEmployeeType());
        assertEquals("ACTIVE", saved.getWorkStatus());
        assertNull(saved.getPartTimeRateId());
        assertEquals("EMP-GENERATED", id);
    }

    @Test
    @DisplayName("创建失败: empCode 重复抛 DUPLICATE_KEY")
    void create_duplicateEmpCode() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(new EmployeeDO());

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("创建失败: 兼职类型缺少 partTimeRateId 抛 BAD_REQUEST")
    void create_partTimeWithoutRate() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E002");
        dto.setEmpName("李四");
        dto.setLevelCode("P3");
        dto.setEmployeeType("PART_TIME");

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("创建失败: 非兼职类型携带 partTimeRateId 抛 BAD_REQUEST")
    void create_nonPartTimeWithRate() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E003");
        dto.setEmpName("王五");
        dto.setLevelCode("L4");
        dto.setEmployeeType("FULL_TIME");
        dto.setPartTimeRateId("R1");

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("创建失败: 无效雇佣类型抛 BAD_REQUEST")
    void create_invalidEmployeeType() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E004");
        dto.setEmpName("赵六");
        dto.setLevelCode("L4");
        dto.setEmployeeType("XXX");

        BizException ex = assertThrows(BizException.class, () -> service.create(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("创建成功: 兼职类型携带 partTimeRateId")
    void create_partTimeWithRate_success() {
        EmployeeCreateDTO dto = new EmployeeCreateDTO();
        dto.setEmpCode("E005");
        dto.setEmpName("钱七");
        dto.setLevelCode("P3");
        dto.setEmployeeType("PART_TIME");
        dto.setPartTimeRateId("R1");
        when(employeeMapper.selectByEmpCode("E005")).thenReturn(null);

        service.create(dto);

        ArgumentCaptor<EmployeeDO> captor = ArgumentCaptor.forClass(EmployeeDO.class);
        verify(employeeMapper).insert(captor.capture());
        assertEquals("PART_TIME", captor.getValue().getEmployeeType());
        assertEquals("R1", captor.getValue().getPartTimeRateId());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById 成功返回员工")
    void getById_success() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        emp.setEmpCode("E001");
        when(employeeMapper.selectById("E1")).thenReturn(emp);

        EmployeeDO result = service.getById("E1");
        assertEquals("E001", result.getEmpCode());
    }

    @Test
    @DisplayName("getById 不存在抛 EMPLOYEE_NOT_FOUND")
    void getById_notFound() {
        when(employeeMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getById("X"));
        assertEquals(BizErrorCode.EMPLOYEE_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("page 过滤查询返回分页结果")
    void page_filter() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        Page<EmployeeDO> mockPage = new Page<>(1, 10, 1);
        mockPage.setRecords(List.of(emp));
        when(employeeMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<EmployeeDO> result = service.page(1, 10, "张", "D1", "FULL_TIME", "ACTIVE");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(employeeMapper).selectPage(any(), any());
    }

    // ==================== listByDepartment ====================

    @Test
    @DisplayName("listByDepartment 返回部门下员工")
    void listByDepartment() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        when(employeeMapper.selectList(any())).thenReturn(List.of(emp));

        List<EmployeeDO> result = service.listByDepartment("D1");

        assertEquals(1, result.size());
        verify(employeeMapper).selectList(any());
    }

    @Test
    @DisplayName("listByDepartment 部门 ID 为空返回空列表，不查库")
    void listByDepartment_emptyDeptId() {
        List<EmployeeDO> result = service.listByDepartment("");

        assertTrue(result.isEmpty());
        verify(employeeMapper, never()).selectList(any());
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新成功")
    void update_success() {
        EmployeeDO existing = new EmployeeDO();
        existing.setId("E1");
        existing.setEmpCode("E001");
        existing.setEmployeeType("FULL_TIME");
        existing.setWorkStatus("ACTIVE");
        when(employeeMapper.selectById("E1")).thenReturn(existing);
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(existing);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三丰");
        dto.setLevelCode("L6");
        dto.setWorkStatus("ACTIVE");

        service.update("E1", dto);

        verify(employeeMapper).updateById(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("更新失败: 员工不存在抛 EMPLOYEE_NOT_FOUND")
    void update_notFound() {
        when(employeeMapper.selectById("X")).thenReturn(null);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");

        BizException ex = assertThrows(BizException.class, () -> service.update("X", dto));
        assertEquals(BizErrorCode.EMPLOYEE_NOT_FOUND.getCode(), ex.getCode());
        verify(employeeMapper, never()).updateById(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("更新失败: empCode 被其他员工占用抛 DUPLICATE_KEY")
    void update_duplicateEmpCodeExcludeSelf() {
        EmployeeDO existing = new EmployeeDO();
        existing.setId("E1");
        existing.setEmployeeType("FULL_TIME");
        existing.setWorkStatus("ACTIVE");
        EmployeeDO other = new EmployeeDO();
        other.setId("E2");
        when(employeeMapper.selectById("E1")).thenReturn(existing);
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(other);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");

        BizException ex = assertThrows(BizException.class, () -> service.update("E1", dto));
        assertEquals(BizErrorCode.DUPLICATE_KEY.getCode(), ex.getCode());
        verify(employeeMapper, never()).updateById(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("更新失败: 在职状态 LEAVE → ACTIVE 非法流转抛 BIZ_ERROR")
    void update_invalidWorkStatusTransit() {
        EmployeeDO existing = new EmployeeDO();
        existing.setId("E1");
        existing.setEmployeeType("FULL_TIME");
        existing.setWorkStatus("LEAVE");
        when(employeeMapper.selectById("E1")).thenReturn(existing);
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(existing);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");
        dto.setWorkStatus("ACTIVE");

        BizException ex = assertThrows(BizException.class, () -> service.update("E1", dto));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
        verify(employeeMapper, never()).updateById(any(EmployeeDO.class));
    }

    @Test
    @DisplayName("更新成功: 在职状态 ACTIVE → SUSPENDED 合法流转")
    void update_validWorkStatusTransit() {
        EmployeeDO existing = new EmployeeDO();
        existing.setId("E1");
        existing.setEmployeeType("FULL_TIME");
        existing.setWorkStatus("ACTIVE");
        when(employeeMapper.selectById("E1")).thenReturn(existing);
        when(employeeMapper.selectByEmpCode("E001")).thenReturn(existing);

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setEmpCode("E001");
        dto.setEmpName("张三");
        dto.setLevelCode("L5");
        dto.setWorkStatus("SUSPENDED");

        service.update("E1", dto);

        verify(employeeMapper).updateById(any(EmployeeDO.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("删除成功")
    void delete_success() {
        EmployeeDO existing = new EmployeeDO();
        existing.setId("E1");
        existing.setEmpCode("E001");
        when(employeeMapper.selectById("E1")).thenReturn(existing);

        service.delete("E1");

        verify(employeeMapper).deleteById("E1");
    }

    @Test
    @DisplayName("删除失败: 员工不存在抛 EMPLOYEE_NOT_FOUND")
    void delete_notFound() {
        when(employeeMapper.selectById("X")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete("X"));
        assertEquals(BizErrorCode.EMPLOYEE_NOT_FOUND.getCode(), ex.getCode());
        verify(employeeMapper, never()).deleteById(any());
    }

    // ==================== assemble ====================

    @Test
    @DisplayName("assemble 装配部门 / 岗位 / 职级名称")
    void assemble_fillsNames() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        emp.setEmpCode("E001");
        emp.setDepartmentId("D1");
        emp.setPositionId("P1");
        emp.setLevelCode("L5");

        DepartmentDO dept = new DepartmentDO();
        dept.setDeptName("研发部");
        PositionDO pos = new PositionDO();
        pos.setPositionName("Java 工程师");
        JobLevelDO level = new JobLevelDO();
        level.setLevelName("中级 L5");

        when(departmentMapper.selectById("D1")).thenReturn(dept);
        when(positionMapper.selectById("P1")).thenReturn(pos);
        when(jobLevelMapper.selectByCode("L5")).thenReturn(level);

        EmployeeVO vo = service.assemble(emp);

        assertNotNull(vo);
        assertEquals("研发部", vo.getDepartmentName());
        assertEquals("Java 工程师", vo.getPositionName());
        assertEquals("中级 L5", vo.getLevelName());
        assertEquals("E001", vo.getEmpCode());
    }

    @Test
    @DisplayName("assemble 入参为 null 返回 null")
    void assemble_nullEntity() {
        assertNull(service.assemble(null));
    }

    @Test
    @DisplayName("assemble 装配失败时降级为 null 不抛异常")
    void assemble_degradeOnFailure() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        emp.setDepartmentId("D1");
        emp.setLevelCode("L5");
        when(departmentMapper.selectById("D1")).thenThrow(new RuntimeException("db error"));
        when(jobLevelMapper.selectByCode("L5")).thenReturn(null);

        EmployeeVO vo = service.assemble(emp);

        assertNotNull(vo);
        assertNull(vo.getDepartmentName());
        assertNull(vo.getLevelName());
    }

    // ==================== getCostProfile ====================

    @Test
    @DisplayName("getCostProfile 全职: 返回 JobLevelRate.totalCost")
    void getCostProfile_fullTime() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E1");
        emp.setEmployeeType("FULL_TIME");
        emp.setLevelCode("L5");
        when(employeeMapper.selectById("E1")).thenReturn(emp);

        com.njydsz.pmis.userinfo.entity.JobLevelRateDO rate = new com.njydsz.pmis.userinfo.entity.JobLevelRateDO();
        rate.setTotalCost(new BigDecimal("10360"));
        when(jobLevelRateMapper.selectEffective(eq("L5"), any())).thenReturn(rate);

        Map<String, Object> profile = service.getCostProfile("E1");

        assertNotNull(profile);
        assertEquals("FULL_TIME", profile.get("employeeType"));
        assertEquals(new BigDecimal("10360"), profile.get("monthlyTotalCost"));
    }

    @Test
    @DisplayName("getCostProfile 兼职: 返回 PartTimeRate.totalCost")
    void getCostProfile_partTime() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E2");
        emp.setEmployeeType("PART_TIME");
        emp.setLevelCode("P3");
        emp.setPartTimeRateId("R1");
        when(employeeMapper.selectById("E2")).thenReturn(emp);

        PartTimeRateDO rate = new PartTimeRateDO();
        rate.setTotalCost(new BigDecimal("4050"));
        when(partTimeRateMapper.selectById("R1")).thenReturn(rate);

        Map<String, Object> profile = service.getCostProfile("E2");

        assertNotNull(profile);
        assertEquals("PART_TIME", profile.get("employeeType"));
        assertEquals(new BigDecimal("4050"), profile.get("monthlyTotalCost"));
    }

    @Test
    @DisplayName("getCostProfile 外包: 返回 OutsourceRate.totalCost")
    void getCostProfile_outsource() {
        EmployeeDO emp = new EmployeeDO();
        emp.setId("E3");
        emp.setEmployeeType("OUTSOURCE");
        emp.setOutsourceRateId("OR1");
        when(employeeMapper.selectById("E3")).thenReturn(emp);

        OutsourceRateDO rate = new OutsourceRateDO();
        rate.setTotalCost(new BigDecimal("5800"));
        when(outsourceRateMapper.selectById("OR1")).thenReturn(rate);

        Map<String, Object> profile = service.getCostProfile("E3");

        assertNotNull(profile);
        assertEquals("OUTSOURCE", profile.get("employeeType"));
        assertEquals("OR1", profile.get("outsourceRateId"));
        assertEquals(new BigDecimal("5800"), profile.get("monthlyTotalCost"));
    }

    @Test
    @DisplayName("getCostProfile 员工不存在返回 null")
    void getCostProfile_notFound() {
        when(employeeMapper.selectById("X")).thenReturn(null);

        assertNull(service.getCostProfile("X"));
    }
}
