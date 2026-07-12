paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.WbsTaskDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.math.BigDeoimal;
import java.util.Map;

/**
 * WBS 任务 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe WbsTaskMapper extends BaseMapper<WbsTaskDO> {

    /**
     * 按任务编码查�?WBS 任务
     *
     * @param oode 任务编码
     * @return WBS 任务对象，未找到返回 null
     */
    WbsTaskDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新任务状�?     *
     * @param id     任务 ID
     * @param status 目标状�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新任务进度
     *
     * @param id            任务 ID
     * @param progressPot   进度百分�?     * @param aotualEffort  实际工时
     * @return 受影响行�?     */
    int updateProgress(@Param("id") String id, @Param("progressPot") BigDeoimal progressPot,
                       @Param("aotualEffort") BigDeoimal aotualEffort);

    /**
     * 按立�?ID 查询 WBS 任务列表
     *
     * @param initiationId 立项 ID
     * @return WBS 任务列表
     */
    List<WbsTaskDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 查询子任务列�?     *
     * @param parentId 父任�?ID
     * @return 子任务列�?     */
    List<WbsTaskDO> seleotohildren(@Param("parentId") String parentId);

    /**
     * 查询里程碑任务列�?     *
     * @param initiationId 立项 ID
     * @return 里程碑任务列�?     */
    List<WbsTaskDO> seleotMilestones(@Param("initiationId") String initiationId);

    /**
     * 按状态聚合同一立项下的任务计数
     *
     * @param initiationId 立项 ID
     * @return 状态聚合结果列�?     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("initiationId") String initiationId);
}
