package com.njydsz.pmis.userinfo.server.service.impl.user;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeCreateDTO;
import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeUpdateDTO;
import com.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.domain.entity.org.PositionDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.OutsourceRateDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.RankDO;
import com.njydsz.pmis.userinfo.domain.entity.rate.RankRateDO;
import com.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import com.njydsz.pmis.userinfo.domain.enums.user.EmployeeType;
import com.njydsz.pmis.userinfo.domain.vo.EmployeeVO;
import com.njydsz.pmis.userinfo.infra.mapper.org.DepartmentMapper;
import com.njydsz.pmis.userinfo.infra.mapper.org.PositionMapper;
import com.njydsz.pmis.userinfo.infra.mapper.rate.OutsourceRateMapper;
import com.njydsz.pmis.userinfo.infra.mapper.rate.PartTimeRateMapper;
import com.njydsz.pmis.userinfo.infra.mapper.rate.RankMapper;
import com.njydsz.pmis.userinfo.infra.mapper.rate.RankRateMapper;
import com.njydsz.pmis.userinfo.infra.mapper.user.EmployeeMapper;
import com.njydsz.pmis.userinfo.server.service.user.EmployeeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 员工服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    /** 默认雇佣类型 */
    private static final String DEFAULT_EMPLOYEE_TYPE = EmployeeType.FULL_TIME.getCode();
    /** 默认在职状态 */
    private static final String DEFAULT_WORK_STATUS = "ACTIVE";

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RankMapper rankMapper;
    private final RankRateMapper rankRateMapper;
    private final PartTimeRateMapper partTimeRateMapper;
    private final OutsourceRateMapper outsourceRateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(EmployeeCreateDTO dto) {
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "员工创建表单不能为空");
        }
        // 雇佣类型默认 FULL_TIME
        String employeeType = StringUtils.hasText(dto.getEmployeeType())
                ? dto.getEmployeeType() : DEFAULT_EMPLOYEE_TYPE;
        validateEmployeeType(employeeType);
        validateRateIds(employeeType, dto.getPartTimeRateId(), dto.getOutsourceRateId());

        // empCode 唯一性校验（排除已删除）
        if (employeeMapper.selectByEmpCode(dto.getEmpCode()) != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "员工编码已存在: " + dto.getEmpCode());
        }

        EmployeeDO entity = new EmployeeDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setEmployeeType(employeeType);
        // 兼职类型之外强制清空兼职费率 ID
        if (!EmployeeType.PART_TIME.getCode().equals(employeeType)) {
            entity.setPartTimeRateId(null);
        }
        // 外包类型之外强制清空外包费率 ID
        if (!EmployeeType.OUTSOURCE.getCode().equals(employeeType)) {
            entity.setOutsourceRateId(null);
        }
        if (!StringUtils.hasText(entity.getWorkStatus())) {
            entity.setWorkStatus(DEFAULT_WORK_STATUS);
        }
        if (entity.getTenantId() == null) {
            entity.setTenantId(TenantContext.getTenantId());
        }
        employeeMapper.insert(entity);
        log.info("[Employee] 新增员工: id={}, empCode={}, type={}", entity.getId(), entity.getEmpCode(), employeeType);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, EmployeeUpdateDTO dto) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "员工 ID 不能为空");
        }
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "员工更新表单不能为空");
        }
        EmployeeDO existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new SysException(BaseResultCode.EMPLOYEE_NOT_FOUND);
        }

        // 雇佣类型：传入则校验，未传入沿用原值
        String employeeType = StringUtils.hasText(dto.getEmployeeType())
                ? dto.getEmployeeType() : existing.getEmployeeType();
        validateEmployeeType(employeeType);
        validateRateIds(employeeType, dto.getPartTimeRateId(), dto.getOutsourceRateId());

        // empCode 唯一性校验（排除自身与已删除）
        EmployeeDO sameCode = employeeMapper.selectByEmpCode(dto.getEmpCode());
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "员工编码已存在: " + dto.getEmpCode());
        }

        // 在职状态流转校验
        if (StringUtils.hasText(dto.getWorkStatus())
                && StringUtils.hasText(existing.getWorkStatus())
                && !dto.getWorkStatus().equals(existing.getWorkStatus())) {
            if (!canWorkStatusTransit(existing.getWorkStatus(), dto.getWorkStatus())) {
                throw new SysException(BaseResultCode.BIZ_ERROR,
                        "在职状态不允许从 " + existing.getWorkStatus() + " 流转到 " + dto.getWorkStatus());
            }
        }

        EmployeeDO entity = new EmployeeDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        // 兼职类型之外强制清空兼职费率 ID
        if (!EmployeeType.PART_TIME.getCode().equals(employeeType)) {
            entity.setPartTimeRateId(null);
        }
        // 外包类型之外强制清空外包费率 ID
        if (!EmployeeType.OUTSOURCE.getCode().equals(employeeType)) {
            entity.setOutsourceRateId(null);
        }
        employeeMapper.updateById(entity);
        log.info("[Employee] 更新员工: id={}, empCode={}", id, dto.getEmpCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "员工 ID 不能为空");
        }
        EmployeeDO existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new SysException(BaseResultCode.EMPLOYEE_NOT_FOUND);
        }
        employeeMapper.deleteById(id);
        log.info("[Employee] 删除员工: id={}, empCode={}", id, existing.getEmpCode());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDO getById(String id) {
        EmployeeDO entity = employeeMapper.selectById(id);
        if (entity == null) {
            throw new SysException(BaseResultCode.EMPLOYEE_NOT_FOUND);
        }
        return entity;
    }

    @Override
    @DataScope(deptColumn = "department_id", userColumn = "created_by")
    @Transactional(readOnly = true)
    public Page<EmployeeDO> page(int page, int size, String keyword, String departmentId,
                                 String employeeType, String workStatus) {
        Page<EmployeeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<EmployeeDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(EmployeeDO::getEmpCode, keyword)
                    .or().like(EmployeeDO::getEmpName, keyword));
        }
        if (StringUtils.hasText(departmentId)) {
            wrapper.eq(EmployeeDO::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(employeeType)) {
            wrapper.eq(EmployeeDO::getEmployeeType, employeeType);
        }
        if (StringUtils.hasText(workStatus)) {
            wrapper.eq(EmployeeDO::getWorkStatus, workStatus);
        }
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "department_id", "created_by");
        if (!ds.isEmpty()) wrapper.apply(ds);
        wrapper.orderByDesc(EmployeeDO::getCreatedAt);
        return employeeMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDO> listByDepartment(String departmentId) {
        if (!StringUtils.hasText(departmentId)) {
            return List.of();
        }
        LambdaQueryWrapper<EmployeeDO> wrapper = new LambdaQueryWrapper<EmployeeDO>()
                .eq(EmployeeDO::getDepartmentId, departmentId)
                .orderByDesc(EmployeeDO::getCreatedAt);
        return employeeMapper.selectList(wrapper);
    }

    @Override
    public EmployeeVO assemble(EmployeeDO entity) {
        if (entity == null) {
            return null;
        }
        EmployeeVO vo = new EmployeeVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setDepartmentName(resolveDepartmentName(entity.getDepartmentId()));
        vo.setPositionName(resolvePositionName(entity.getPositionId()));
        vo.setLevelName(resolveLevelName(entity.getLevelCode()));
        vo.setPartTimeRateName(resolvePartTimeRateName(entity.getPartTimeRateId()));
        vo.setOutsourceRateName(resolveOutsourceRateName(entity.getOutsourceRateId()));
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCostProfile(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        EmployeeDO emp = employeeMapper.selectById(id);
        if (emp == null) {
            return null;
        }
        Map<String, Object> profile = new HashMap<>(8);
        String employeeType = emp.getEmployeeType() != null ? emp.getEmployeeType() : EmployeeType.FULL_TIME.getCode();
        profile.put("employeeType", employeeType);
        profile.put("levelCode", emp.getLevelCode());
        profile.put("partTimeRateId", emp.getPartTimeRateId());
        profile.put("outsourceRateId", emp.getOutsourceRateId());

        if (EmployeeType.FULL_TIME.getCode().equals(employeeType)) {
            // 全职：月薪 + 社保公积金 + 差旅报销 + 差旅补贴（公司承担），取 pmis_rank_rate.total_cost
            LocalDate today = LocalDate.now();
            RankRateDO rate = rankRateMapper.selectEffective(emp.getLevelCode(), today);
            profile.put("monthlyTotalCost", rate != null ? rate.getTotalCost() : null);
            profile.put("hourlyRate", null);
            profile.put("overtimeRate", null);
        } else if (EmployeeType.PART_TIME.getCode().equals(employeeType)) {
            // 兼职：月薪 + 商业保险 + 差旅报销 + 差旅补贴（公司承担），取 pmis_part_time_rate.total_cost
            PartTimeRateDO rate = null;
            if (StringUtils.hasText(emp.getPartTimeRateId())) {
                rate = partTimeRateMapper.selectById(emp.getPartTimeRateId());
            }
            profile.put("monthlyTotalCost", rate != null ? rate.getTotalCost() : null);
            profile.put("hourlyRate", rate != null ? rate.getHourlyRate() : null);
            profile.put("monthlyHours", rate != null ? rate.getMonthlyHours() : null);
            profile.put("overtimeRate", null);
        } else if (EmployeeType.OUTSOURCE.getCode().equals(employeeType)) {
            // 外包：人天核算月薪 + 差旅报销 + 差旅补贴（公司承担），取 pmis_outsource_rate.total_cost
            OutsourceRateDO rate = null;
            if (StringUtils.hasText(emp.getOutsourceRateId())) {
                rate = outsourceRateMapper.selectById(emp.getOutsourceRateId());
            }
            profile.put("monthlyTotalCost", rate != null ? rate.getTotalCost() : null);
            profile.put("dailyRate", rate != null ? rate.getDailyRate() : null);
            profile.put("monthlyDays", rate != null ? rate.getMonthlyDays() : null);
            profile.put("overtimeRate", null);
        } else {
            profile.put("monthlyTotalCost", null);
            profile.put("hourlyRate", null);
            profile.put("overtimeRate", null);
        }
        return profile;
    }

    /**
     * 校验雇佣类型枚举合法性
     *
     * @param employeeType 雇佣类型编码
     */
    private void validateEmployeeType(String employeeType) {
        if (EmployeeType.fromCode(employeeType) == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "无效的雇佣类型: " + employeeType);
        }
    }

    /**
     * 校验费率 ID：PART_TIME 必填 partTimeRateId，OUTSOURCE 必填 outsourceRateId，其余必须为空
     *
     * @param employeeType    雇佣类型编码
     * @param partTimeRateId  兼职费率 ID
     * @param outsourceRateId 外包费率 ID
     */
    private void validateRateIds(String employeeType, String partTimeRateId, String outsourceRateId) {
        if (EmployeeType.PART_TIME.getCode().equals(employeeType)) {
            if (!StringUtils.hasText(partTimeRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "兼职类型员工必须填写兼职费率 ID");
            }
            if (StringUtils.hasText(outsourceRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "兼职类型员工的外包费率 ID 必须为空");
            }
        } else if (EmployeeType.OUTSOURCE.getCode().equals(employeeType)) {
            if (!StringUtils.hasText(outsourceRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "外包类型员工必须填写外包费率 ID");
            }
            if (StringUtils.hasText(partTimeRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "外包类型员工的兼职费率 ID 必须为空");
            }
        } else {
            if (StringUtils.hasText(partTimeRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "非兼职类型员工的兼职费率 ID 必须为空");
            }
            if (StringUtils.hasText(outsourceRateId)) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "非外包类型员工的外包费率 ID 必须为空");
            }
        }
    }

    /**
     * 在职状态流转校验（参考 AssignmentStatus.canTransitTo 写法）
     *
     * <p>流转规则：ACTIVE ↔ SUSPENDED；ACTIVE/SUSPENDED → LEAVE；LEAVE 为终态。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 允许流转返回 true
     */
    private boolean canWorkStatusTransit(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "ACTIVE" -> "SUSPENDED".equals(to) || "LEAVE".equals(to);
            case "SUSPENDED" -> "ACTIVE".equals(to) || "LEAVE".equals(to);
            case "LEAVE" -> false;
            default -> false;
        };
    }

    /**
     * 装配部门名称，失败降级为 null
     */
    private String resolveDepartmentName(String departmentId) {
        if (departmentId == null) {
            return null;
        }
        try {
            DepartmentDO dept = departmentMapper.selectById(departmentId);
            return dept == null ? null : dept.getDeptName();
        } catch (Exception e) {
            log.warn("[Employee] 装配部门名称失败: deptId={}, msg={}", departmentId, e.getMessage());
            return null;
        }
    }

    /**
     * 装配岗位名称，失败降级为 null
     */
    private String resolvePositionName(String positionId) {
        if (positionId == null) {
            return null;
        }
        try {
            PositionDO pos = positionMapper.selectById(positionId);
            return pos == null ? null : pos.getPositionName();
        } catch (Exception e) {
            log.warn("[Employee] 装配岗位名称失败: positionId={}, msg={}", positionId, e.getMessage());
            return null;
        }
    }

    /**
     * 装配职级名称，失败降级为 null
     */
    private String resolveLevelName(String levelCode) {
        if (levelCode == null) {
            return null;
        }
        try {
            RankDO level = rankMapper.selectByCode(levelCode);
            return level == null ? null : level.getLevelName();
        } catch (Exception e) {
            log.warn("[Employee] 装配职级名称失败: levelCode={}, msg={}", levelCode, e.getMessage());
            return null;
        }
    }

    /**
     * 装配兼职费率名称，失败降级为 null
     */
    private String resolvePartTimeRateName(String partTimeRateId) {
        if (partTimeRateId == null) {
            return null;
        }
        try {
            PartTimeRateDO rate = partTimeRateMapper.selectById(partTimeRateId);
            return rate == null ? null : rate.getRateName();
        } catch (Exception e) {
            log.warn("[Employee] 装配兼职费率名称失败: partTimeRateId={}, msg={}", partTimeRateId, e.getMessage());
            return null;
        }
    }

    /**
     * 装配外包费率名称，失败降级为 null
     */
    private String resolveOutsourceRateName(String outsourceRateId) {
        if (outsourceRateId == null) {
            return null;
        }
        try {
            OutsourceRateDO rate = outsourceRateMapper.selectById(outsourceRateId);
            return rate == null ? null : rate.getRateName();
        } catch (Exception e) {
            log.warn("[Employee] 装配外包费率名称失败: outsourceRateId={}, msg={}", outsourceRateId, e.getMessage());
            return null;
        }
    }
}
