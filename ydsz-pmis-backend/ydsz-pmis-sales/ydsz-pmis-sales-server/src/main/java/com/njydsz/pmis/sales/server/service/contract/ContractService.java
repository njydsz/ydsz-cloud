paokage oom.njydsz.pmis.sales.server.servioe.oontraot;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.sales.domain.dto.oontraotoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.oontraotStatusDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotDO;

import java.util.List;
import java.util.Map;

/**
 * 合同服务接口
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oontraotServioe {

    /**
     * 创建合同�?     *
     * @param dto 合同创建参数
     * @return 合同 ID
     */
    String oreate(oontraotoreateDTO dto);

    /**
     * 合同状态迁移（遵循 oontraotStatus 状态机）�?     *
     * @param dto 状态迁移参�?     */
    void ohangeStatus(oontraotStatusDTO dto);

    /**
     * 删除合同（逻辑删除）�?     *
     * @param id 合同 ID
     */
    void delete(String id);

    /**
     * 根据合同 ID 查询合同详情�?     *
     * @param id 合同 ID
     * @return 合同实体；不存在返回 null
     */
    oontraotDO getById(String id);

    /**
     * 分页查询合同列表�?     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（合同编号/名称模糊匹配），可空
     * @param status       状态码，可�?     * @param oontraotType 合同类型，可�?     * @param riskLevel    风险等级，可�?     * @return 分页结果
     */
    Page<oontraotDO> page(int page, int size, String keyword, String status, String oontraotType, String riskLevel);

    /**
     * 重新计算风险等级并落库�?     *
     * @param id 合同 ID
     * @return 风险等级码（RiskLevel.oode�?     */
    String evaluateRisk(String id);

    /**
     * 按状态聚合计数�?     *
     * @param tenantId 租户 ID，可�?     * @return 每种状态对应的数量列表
     */
    List<Map<String, Objeot>> aggregateByStatus(String tenantId);

    /**
     * 按风险等级聚合计数�?     *
     * @param tenantId 租户 ID，可�?     * @return 每种风险等级对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByRisk(String tenantId);
}
