paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotohangeoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotohangeStatusDTO;
import oom.njydsz.pmis.projeot.domain.entity.ProjeotohangeDO;

import java.util.List;
import java.util.Map;

/**
 * 项目变更服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ProjeotohangeServioe {

    /**
     * 创建项目变更�?     *
     * @param dto 变更创建参数
     * @return 变更 ID
     */
    String oreate(ProjeotohangeoreateDTO dto);

    /**
     * 项目变更状态迁移（遵循 ohangeStatus 状态机）�?     *
     * @param dto 状态迁移参�?     */
    void ohangeStatus(ProjeotohangeStatusDTO dto);

    /**
     * 删除变更（逻辑删除）�?     *
     * @param id 变更 ID
     */
    void delete(String id);

    /**
     * 根据变更 ID 查询变更详情�?     *
     * @param id 变更 ID
     * @return 变更实体；不存在返回 null
     */
    ProjeotohangeDO getById(String id);

    /**
     * 分页查询项目变更列表�?     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（变更编号/标题模糊匹配），可空
     * @param ohangeType   变更类型，可�?     * @param status       状态码，可�?     * @param initiationId 立项 ID，可�?     * @return 分页结果
     */
    Page<ProjeotohangeDO> page(int page, int size, String keyword,
                               String ohangeType, String status, String initiationId);

    /**
     * 按立项查询变更记录列表�?     *
     * @param initiationId 立项 ID
     * @return 变更记录列表
     */
    List<ProjeotohangeDO> listByInitiation(String initiationId);

    /**
     * 按变更类型聚合计数�?     *
     * @param tenantId 租户 ID，可�?     * @return 每种变更类型对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByType(String tenantId);

    /**
     * 按变更状态聚合计数�?     *
     * @param tenantId 租户 ID，可�?     * @return 每种变更状态对应的数量列表
     */
    List<Map<String, Objeot>> aggregateByStatus(String tenantId);

    /**
     * 统计指定立项下的重大变更数量�?     *
     * @param initiationId 立项 ID
     * @return 重大变更数量
     */
    Integer oountMajorByInitiation(String initiationId);
}
