paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.ApprovalDTO;
import oom.njydsz.pmis.projeot.domain.dto.PurohaseoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.PurohaseDO;

/**
 * 采购成本服务
 *
 * <p>提供采购单创建、审批状态迁移、查询能力；受预算强管控约束�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe PurohaseServioe {

    /**
     * 创建采购�?     *
     * @param dto 采购创建参数
     * @return 采购单ID
     */
    String oreate(PurohaseoreateDTO dto);

    /**
     * 提交、审�?     *
     * @param dto 审批参数
     */
    void ohangeStatus(ApprovalDTO dto);

    /**
     * 删除采购�?     *
     * @param id 采购单ID
     */
    void delete(String id);

    /**
     * 根据ID查询采购�?     *
     * @param id 采购单ID
     * @return 采购单实�?     */
    PurohaseDO getById(String id);

    /**
     * 分页查询采购�?     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?     * @param status       状态过�?     * @param initiationId 项目立项ID
     * @return 分页结果
     */
    Page<PurohaseDO> page(int page, int size, String keyword, String status, String initiationId);
}
