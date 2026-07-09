package com.njydsz.pmis.userinfo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.dto.EmployeeCreateDTO;
import com.njydsz.pmis.userinfo.dto.EmployeeUpdateDTO;
import com.njydsz.pmis.userinfo.entity.EmployeeDO;
import com.njydsz.pmis.userinfo.vo.EmployeeVO;

import java.util.List;
import java.util.Map;

/**
 * 员工服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface EmployeeService {

    /**
     * 创建员工
     *
     * @param dto 员工创建表单
     * @return 新建员工 ID
     */
    String create(EmployeeCreateDTO dto);

    /**
     * 更新员工
     *
     * @param id  员工 ID
     * @param dto 员工更新表单
     */
    void update(String id, EmployeeUpdateDTO dto);

    /**
     * 删除员工（逻辑删除）
     *
     * @param id 员工 ID
     */
    void delete(String id);

    /**
     * 根据 ID 获取员工
     *
     * @param id 员工 ID
     * @return 员工实体，不存在时抛出业务异常
     */
    EmployeeDO getById(String id);

    /**
     * 分页查询员工
     *
     * @param page         当前页（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键字（匹配工号 / 姓名，可空）
     * @param departmentId 部门 ID（可空）
     * @param employeeType 雇佣类型（可空）
     * @param workStatus   在职状态（可空）
     * @return 员工分页结果
     */
    Page<EmployeeDO> page(int page, int size, String keyword, String departmentId, String employeeType, String workStatus);

    /**
     * 按部门查询员工列表
     *
     * @param departmentId 部门 ID
     * @return 员工列表
     */
    List<EmployeeDO> listByDepartment(String departmentId);

    /**
     * 将员工实体装配为视图对象（填充部门 / 岗位 / 职级名称，失败降级为 null）
     *
     * @param entity 员工实体
     * @return 员工视图对象；entity 为 null 时返回 null
     */
    EmployeeVO assemble(EmployeeDO entity);

    /**
     * 查询员工成本档案（用于跨模块成本核算）
     *
     * @param id 员工 ID
     * @return 成本档案 Map；员工不存在返回 null。
     *   Map 含字段：
     *   - employeeType: String (FULL_TIME/PART_TIME/OUTSOURCE)
     *   - levelCode: String
     *   - partTimeRateId: String
     *   - monthlyTotalCost: BigDecimal（全职=JobLevelRate.totalCost；兼职=PartTimeRate.totalCost；外包=null）
     *   - hourlyRate: null（预留）
     *   - overtimeRate: null（预留）
     */
    Map<String, Object> getCostProfile(String id);
}
