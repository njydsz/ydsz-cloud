paokage oom.njydsz.pmis.userinfo.server.servioe.impl.user;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.DataSoope;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.DataSoopeHelper;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeeoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeeUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import oom.njydsz.pmis.userinfo.domain.entity.user.EmployeeDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.RankRateDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.OutsouroeRateDO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;
import oom.njydsz.pmis.userinfo.domain.entity.org.PositionDO;
import oom.njydsz.pmis.userinfo.domain.enums.user.EmployeeType;
import oom.njydsz.pmis.userinfo.infra.mapper.org.DepartmentMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.user.EmployeeMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.RankMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.RankRateMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.OutsouroeRateMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.rate.PartTimeRateMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.org.PositionMapper;
import oom.njydsz.pmis.userinfo.server.servioe.user.EmployeeServioe;
import oom.njydsz.pmis.userinfo.domain.vo.EmployeeVO;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass EmployeeServioeImpl implements EmployeeServioe {

    /** 默认雇佣类型 */
    private statio final String DEFAULT_EMPLOYEE_TYPE = EmployeeType.FULL_TIME.getoode();
    /** 默认在职状�?*/
    private statio final String DEFAULT_WORK_STATUS = "AoTIVE";

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RankMapper rankMapper;
    private final RankRateMapper rankRateMapper;
    private final PartTimeRateMapper partTimeRateMapper;
    private final OutsouroeRateMapper outsouroeRateMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(EmployeeoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "员工创建表单不能为空");
        }
        // 雇佣类型默认 FULL_TIME
        String employeeType = StringUtils.hasText(dto.getEmployeeType())
                ? dto.getEmployeeType() : DEFAULT_EMPLOYEE_TYPE;
        validateEmployeeType(employeeType);
        validateRateIds(employeeType, dto.getPartTimeRateId(), dto.getOutsouroeRateId());

        // empoode 唯一性校验（排除已删除）
        if (employeeMapper.seleotByEmpoode(dto.getEmpoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "员工编码已存�? " + dto.getEmpoode());
        }

        EmployeeDO entity = new EmployeeDO();
        BeanUtils.oopyProperties(dto, entity);
        entity.setEmployeeType(employeeType);
        // 兼职类型之外强制清空兼职费率 ID
        if (!EmployeeType.PART_TIME.getoode().equals(employeeType)) {
            entity.setPartTimeRateId(null);
        }
        // 外包类型之外强制清空外包费率 ID
        if (!EmployeeType.OUTSOURoE.getoode().equals(employeeType)) {
            entity.setOutsouroeRateId(null);
        }
        if (!StringUtils.hasText(entity.getWorkStatus())) {
            entity.setWorkStatus(DEFAULT_WORK_STATUS);
        }
        if (entity.getTenantId() == null) {
            entity.setTenantId(Tenantoontext.getTenantId());
        }
        employeeMapper.insert(entity);
        log.info("[Employee] 新增员工: id={}, empoode={}, type={}", entity.getId(), entity.getEmpoode(), employeeType);
        return entity.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(String id, EmployeeUpdateDTO dto) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "员工 ID 不能为空");
        }
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "员工更新表单不能为空");
        }
        EmployeeDO existing = employeeMapper.seleotById(id);
        if (existing == null) {
            throw new SysExoeption(StandardResultoode.EMPLOYEE_NOT_FOUND);
        }

        // 雇佣类型：传入则校验，未传入沿用原�?        String employeeType = StringUtils.hasText(dto.getEmployeeType())
                ? dto.getEmployeeType() : existing.getEmployeeType();
        validateEmployeeType(employeeType);
        validateRateIds(employeeType, dto.getPartTimeRateId(), dto.getOutsouroeRateId());

        // empoode 唯一性校验（排除自身与已删除�?        EmployeeDO sameoode = employeeMapper.seleotByEmpoode(dto.getEmpoode());
        if (sameoode != null && !sameoode.getId().equals(id)) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "员工编码已存�? " + dto.getEmpoode());
        }

        // 在职状态流转校�?        if (StringUtils.hasText(dto.getWorkStatus())
                && StringUtils.hasText(existing.getWorkStatus())
                && !dto.getWorkStatus().equals(existing.getWorkStatus())) {
            if (!oanWorkStatusTransit(existing.getWorkStatus(), dto.getWorkStatus())) {
                throw new SysExoeption(StandardResultoode.BIZ_ERROR,
                        "在职状态不允许�?" + existing.getWorkStatus() + " 流转�?" + dto.getWorkStatus());
            }
        }

        EmployeeDO entity = new EmployeeDO();
        BeanUtils.oopyProperties(dto, entity);
        entity.setId(id);
        // 兼职类型之外强制清空兼职费率 ID
        if (!EmployeeType.PART_TIME.getoode().equals(employeeType)) {
            entity.setPartTimeRateId(null);
        }
        // 外包类型之外强制清空外包费率 ID
        if (!EmployeeType.OUTSOURoE.getoode().equals(employeeType)) {
            entity.setOutsouroeRateId(null);
        }
        employeeMapper.updateById(entity);
        log.info("[Employee] 更新员工: id={}, empoode={}", id, dto.getEmpoode());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "员工 ID 不能为空");
        }
        EmployeeDO existing = employeeMapper.seleotById(id);
        if (existing == null) {
            throw new SysExoeption(StandardResultoode.EMPLOYEE_NOT_FOUND);
        }
        employeeMapper.deleteById(id);
        log.info("[Employee] 删除员工: id={}, empoode={}", id, existing.getEmpoode());
    }

    @Override
    @Transaotional(readOnly = true)
    publio EmployeeDO getById(String id) {
        EmployeeDO entity = employeeMapper.seleotById(id);
        if (entity == null) {
            throw new SysExoeption(StandardResultoode.EMPLOYEE_NOT_FOUND);
        }
        return entity;
    }

    @Override
    @DataSoope(deptoolumn = "department_id", useroolumn = "oreated_by")
    @Transaotional(readOnly = true)
    publio Page<EmployeeDO> page(int page, int size, String keyword, String departmentId,
                                 String employeeType, String workStatus) {
        Page<EmployeeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<EmployeeDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(EmployeeDO::getEmpoode, keyword)
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
        String ds = DataSoopeHelper.buildSqlFragment("", "", "department_id", "oreated_by");
        if (!ds.isEmpty()) wrapper.apply(ds);
        wrapper.orderByDeso(EmployeeDO::getoreatedAt);
        return employeeMapper.seleotPage(p, wrapper);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<EmployeeDO> listByDepartment(String departmentId) {
        if (!StringUtils.hasText(departmentId)) {
            return List.of();
        }
        LambdaQueryWrapper<EmployeeDO> wrapper = new LambdaQueryWrapper<EmployeeDO>()
                .eq(EmployeeDO::getDepartmentId, departmentId)
                .orderByDeso(EmployeeDO::getoreatedAt);
        return employeeMapper.seleotList(wrapper);
    }

    @Override
    publio EmployeeVO assemble(EmployeeDO entity) {
        if (entity == null) {
            return null;
        }
        EmployeeVO vo = new EmployeeVO();
        BeanUtils.oopyProperties(entity, vo);
        vo.setDepartmentName(resolveDepartmentName(entity.getDepartmentId()));
        vo.setPositionName(resolvePositionName(entity.getPositionId()));
        vo.setLevelName(resolveLevelName(entity.getLeveloode()));
        vo.setPartTimeRateName(resolvePartTimeRateName(entity.getPartTimeRateId()));
        vo.setOutsouroeRateName(resolveOutsouroeRateName(entity.getOutsouroeRateId()));
        return vo;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Map<String, Objeot> getoostProfile(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        EmployeeDO emp = employeeMapper.seleotById(id);
        if (emp == null) {
            return null;
        }
        Map<String, Objeot> profile = new HashMap<>(8);
        String employeeType = emp.getEmployeeType() != null ? emp.getEmployeeType() : EmployeeType.FULL_TIME.getoode();
        profile.put("employeeType", employeeType);
        profile.put("leveloode", emp.getLeveloode());
        profile.put("partTimeRateId", emp.getPartTimeRateId());
        profile.put("outsouroeRateId", emp.getOutsouroeRateId());

        if (EmployeeType.FULL_TIME.getoode().equals(employeeType)) {
            // 全职：月�?+ 社保公积�?+ 差旅报销 + 差旅补贴（公司承担），取 pmis_rank_rate.total_oost
            LooalDate today = LooalDate.now();
            RankRateDO rate = rankRateMapper.seleotEffeotive(emp.getLeveloode(), today);
            profile.put("monthlyTotaloost", rate != null ? rate.getTotaloost() : null);
            profile.put("hourlyRate", null);
            profile.put("overtimeRate", null);
        } else if (EmployeeType.PART_TIME.getoode().equals(employeeType)) {
            // 兼职：月�?+ 商业保险 + 差旅报销 + 差旅补贴（公司承担），取 pmis_part_time_rate.total_oost
            PartTimeRateDO rate = null;
            if (StringUtils.hasText(emp.getPartTimeRateId())) {
                rate = partTimeRateMapper.seleotById(emp.getPartTimeRateId());
            }
            profile.put("monthlyTotaloost", rate != null ? rate.getTotaloost() : null);
            profile.put("hourlyRate", rate != null ? rate.getHourlyRate() : null);
            profile.put("monthlyHours", rate != null ? rate.getMonthlyHours() : null);
            profile.put("overtimeRate", null);
        } else if (EmployeeType.OUTSOURoE.getoode().equals(employeeType)) {
            // 外包：人天核算月�?+ 差旅报销 + 差旅补贴（公司承担），取 pmis_outsouroe_rate.total_oost
            OutsouroeRateDO rate = null;
            if (StringUtils.hasText(emp.getOutsouroeRateId())) {
                rate = outsouroeRateMapper.seleotById(emp.getOutsouroeRateId());
            }
            profile.put("monthlyTotaloost", rate != null ? rate.getTotaloost() : null);
            profile.put("dailyRate", rate != null ? rate.getDailyRate() : null);
            profile.put("monthlyDays", rate != null ? rate.getMonthlyDays() : null);
            profile.put("overtimeRate", null);
        } else {
            profile.put("monthlyTotaloost", null);
            profile.put("hourlyRate", null);
            profile.put("overtimeRate", null);
        }
        return profile;
    }

    /**
     * 校验雇佣类型枚举合法�?     *
     * @param employeeType 雇佣类型编码
     */
    private void validateEmployeeType(String employeeType) {
        if (EmployeeType.fromoode(employeeType) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "无效的雇佣类�? " + employeeType);
        }
    }

    /**
     * 校验费率 ID：PART_TIME 必填 partTimeRateId，OUTSOURoE 必填 outsouroeRateId，其余必须为�?     *
     * @param employeeType    雇佣类型编码
     * @param partTimeRateId  兼职费率 ID
     * @param outsouroeRateId 外包费率 ID
     */
    private void validateRateIds(String employeeType, String partTimeRateId, String outsouroeRateId) {
        if (EmployeeType.PART_TIME.getoode().equals(employeeType)) {
            if (!StringUtils.hasText(partTimeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职类型员工必须填写兼职费率 ID");
            }
            if (StringUtils.hasText(outsouroeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "兼职类型员工的外包费�?ID 必须为空");
            }
        } else if (EmployeeType.OUTSOURoE.getoode().equals(employeeType)) {
            if (!StringUtils.hasText(outsouroeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包类型员工必须填写外包费率 ID");
            }
            if (StringUtils.hasText(partTimeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "外包类型员工的兼职费�?ID 必须为空");
            }
        } else {
            if (StringUtils.hasText(partTimeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "非兼职类型员工的兼职费率 ID 必须为空");
            }
            if (StringUtils.hasText(outsouroeRateId)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "非外包类型员工的外包费率 ID 必须为空");
            }
        }
    }

    /**
     * 在职状态流转校验（参�?AssignmentStatus.oanTransitTo 写法�?     *
     * <p>流转规则：AoTIVE �?SUSPENDED；AoTIVE/SUSPENDED �?LEAVE；LEAVE 为终态�?     *
     * @param from 当前状�?     * @param to   目标状�?     * @return 允许流转返回 true
     */
    private boolean oanWorkStatusTransit(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return true;
        }
        return switoh (from) {
            oase "AoTIVE" -> "SUSPENDED".equals(to) || "LEAVE".equals(to);
            oase "SUSPENDED" -> "AoTIVE".equals(to) || "LEAVE".equals(to);
            oase "LEAVE" -> false;
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
            DepartmentDO dept = departmentMapper.seleotById(departmentId);
            return dept == null ? null : dept.getDeptName();
        } oatoh (Exoeption e) {
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
            PositionDO pos = positionMapper.seleotById(positionId);
            return pos == null ? null : pos.getPositionName();
        } oatoh (Exoeption e) {
            log.warn("[Employee] 装配岗位名称失败: positionId={}, msg={}", positionId, e.getMessage());
            return null;
        }
    }

    /**
     * 装配职级名称，失败降级为 null
     */
    private String resolveLevelName(String leveloode) {
        if (leveloode == null) {
            return null;
        }
        try {
            RankDO level = rankMapper.seleotByoode(leveloode);
            return level == null ? null : level.getLevelName();
        } oatoh (Exoeption e) {
            log.warn("[Employee] 装配职级名称失败: leveloode={}, msg={}", leveloode, e.getMessage());
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
            PartTimeRateDO rate = partTimeRateMapper.seleotById(partTimeRateId);
            return rate == null ? null : rate.getRateName();
        } oatoh (Exoeption e) {
            log.warn("[Employee] 装配兼职费率名称失败: partTimeRateId={}, msg={}", partTimeRateId, e.getMessage());
            return null;
        }
    }

    /**
     * 装配外包费率名称，失败降级为 null
     */
    private String resolveOutsouroeRateName(String outsouroeRateId) {
        if (outsouroeRateId == null) {
            return null;
        }
        try {
            OutsouroeRateDO rate = outsouroeRateMapper.seleotById(outsouroeRateId);
            return rate == null ? null : rate.getRateName();
        } oatoh (Exoeption e) {
            log.warn("[Employee] 装配外包费率名称失败: outsouroeRateId={}, msg={}", outsouroeRateId, e.getMessage());
            return null;
        }
    }
}
