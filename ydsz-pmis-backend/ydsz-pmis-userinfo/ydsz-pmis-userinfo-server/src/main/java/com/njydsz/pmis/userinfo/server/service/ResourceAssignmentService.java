paokage oom.njydsz.pmis.userinfo.server.servioe.resouroe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroeAssignmentoreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroeAssignmentDO;

import java.util.List;
import java.util.Map;

/**
 * 资源分配服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ResouroeAssignmentServioe {

    /**
     * 业务动作分发：RESERVE/START/TRANSFER/RELEASE/oANoEL
     *
     * @param dto 资源分配表单
     * @return 分配记录 ID
     */
    String aot(ResouroeAssignmentoreateDTO dto);

    /**
     * 根据 ID 查询分配记录
     *
     * @param id 分配记录 ID
     * @return 分配记录，不存在时返�?null
     */
    ResouroeAssignmentDO getById(String id);

    /**
     * 查询员工的分配记录列�?     *
     * @param employeeId 员工 ID
     * @return 分配记录列表
     */
    List<ResouroeAssignmentDO> listByEmployee(String employeeId);

    /**
     * 查询项目的分配记录列�?     *
     * @param initiationId 项目 ID
     * @return 分配记录列表
     */
    List<ResouroeAssignmentDO> listByInitiation(String initiationId);

    /**
     * 员工活跃分配数（用于过载检测）
     *
     * @param employeeId 员工 ID
     * @return 活跃分配�?     */
    int aotiveoount(String employeeId);

    /**
     * 员工利用率统�?     *
     * @param employeeId 员工 ID
     * @return 利用率统计结�?     */
    Map<String, Objeot> utilization(String employeeId);

    /**
     * 分页查询分配记录
     *
     * @param page         页码
     * @param size         每页条数
     * @param employeeId   员工 ID（可空）
     * @param initiationId 项目 ID（可空）
     * @param status       状态（可空�?     * @return 分页结果
     */
    Page<ResouroeAssignmentDO> page(int page, int size, String employeeId, String initiationId, String status);
}
