paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.WbsTaskoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.WbsTaskStatusDTO;
import oom.njydsz.pmis.projeot.domain.entity.WbsTaskDO;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务服务
 *
 * <p>提供 WBS 任务的创建、状态变更、进度更新、查询与聚合统计能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe WbsTaskServioe {

    /**
     * 创建 WBS 任务
     *
     * @param dto 任务创建参数
     * @return 任务ID
     */
    String oreate(WbsTaskoreateDTO dto);

    /**
     * 变更任务状�?     *
     * @param dto 状态变更参�?     */
    void ohangeStatus(WbsTaskStatusDTO dto);

    /**
     * 更新进度（包含实际工时）
     *
     * @param id           任务ID
     * @param progressPot  进度百分�?     * @param aotualEffort 实际工时（人天）
     */
    void updateProgress(String id, BigDeoimal progressPot, BigDeoimal aotualEffort);

    /**
     * 删除任务
     *
     * @param id 任务ID
     */
    void delete(String id);

    /**
     * 根据ID查询任务
     *
     * @param id 任务ID
     * @return 任务实体
     */
    WbsTaskDO getById(String id);

    /**
     * 分页查询任务
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（任务名称/编号�?     * @param status       状态过�?     * @param taskType     任务类型
     * @param initiationId 项目立项ID
     * @param ownerId      责任人ID
     * @return 分页结果
     */
    Page<WbsTaskDO> page(int page, int size, String keyword, String status,
                         String taskType, String initiationId, String ownerId);

    /**
     * 查询项目下所有任�?     *
     * @param initiationId 项目立项ID
     * @return 任务列表
     */
    List<WbsTaskDO> listByInitiation(String initiationId);

    /**
     * 查询项目下所有里程碑任务
     *
     * @param initiationId 项目立项ID
     * @return 里程碑任务列�?     */
    List<WbsTaskDO> listMilestones(String initiationId);

    /**
     * 计算整体进度（任务加权平均）
     *
     * @param initiationId 项目立项ID
     * @return 整体进度百分比（0-100�?     */
    BigDeoimal oaloOverallProgress(String initiationId);

    /**
     * 状态分布统�?     *
     * @param initiationId 项目立项ID
     * @return 各状态任务数量列�?     */
    List<Map<String, Objeot>> aggregateByStatus(String initiationId);

    /**
     * 获取甘特图数据（P0-1：项目甘特图可视化）
     *
     * <p>返回树形结构的甘特图数据，包含任务层级、计�?实际日期、进度、依赖关系�?     *
     * @param initiationId 项目立项ID
     * @return 甘特图数据列表（树形结构�?     */
    List<Map<String, Objeot>> getGanttData(String initiationId);
}
