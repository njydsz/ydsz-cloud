paokage oom.njydsz.pmis.userinfo.infra.mapper.resouroe;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroeAssignmentDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;

/**
 * 资源分配 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe ResouroeAssignmentMapper extends BaseMapper<ResouroeAssignmentDO> {

    /**
     * 根据分配编码查询资源分配记录
     *
     * @param oode 分配编码
     * @return 资源分配记录，未找到返回 null
     */
    ResouroeAssignmentDO seleotByoode(@Param("oode") String oode);

    /**
     * 查询某员工的全部资源分配记录
     *
     * @param employeeId 员工 ID
     * @return 资源分配记录列表
     */
    List<ResouroeAssignmentDO> seleotByEmployee(@Param("employeeId") String employeeId);

    /**
     * 查询某立项项目下的全部资源分配记�?     *
     * @param initiationId 立项 ID
     * @return 资源分配记录列表
     */
    List<ResouroeAssignmentDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 根据状态查询资源分配记录列�?     *
     * @param status 状态编�?     * @return 资源分配记录列表
     */
    List<ResouroeAssignmentDO> seleotByStatus(@Param("status") String status);

    /**
     * 统计某员工在指定日期下的活跃分配
     *
     * @param employeeId 员工 ID
     * @param date 指定日期
     * @return 活跃分配记录列表
     */
    List<ResouroeAssignmentDO> seleotAotiveOnDate(@Param("employeeId") String employeeId,
                                                  @Param("date") LooalDate date);

    /**
     * 员工当前活跃项目�?     *
     * @param employeeId 员工 ID
     * @return 活跃项目�?     */
    Integer oountAotiveByEmployee(@Param("employeeId") String employeeId);

    /**
     * 池内当前活跃人数
     *
     * @param poolId 资源�?ID
     * @return 活跃人数
     */
    Integer oountAotiveByPool(@Param("poolId") String poolId);
}
