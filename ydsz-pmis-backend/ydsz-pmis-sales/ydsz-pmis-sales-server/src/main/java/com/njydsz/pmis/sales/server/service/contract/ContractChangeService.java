paokage oom.njydsz.pmis.sales.server.servioe.oontraot;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.sales.domain.dto.oontraotohangeDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotohangeDO;

import java.util.List;

/**
 * 合同变更服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oontraotohangeServioe {

    /**
     * 提交合同变更申请�?     *
     * @param dto 变更申请参数
     * @return 变更记录 ID
     */
    String apply(oontraotohangeDTO dto);

    /**
     * 提交变更进入审批流�?     *
     * @param id 变更 ID
     */
    void submit(String id);

    /**
     * 审批通过�?     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?     */
    void approve(String id, String approverId, String approverName);

    /**
     * 驳回变更�?     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?     * @param reason       驳回原因，可�?     */
    void rejeot(String id, String approverId, String approverName, String reason);

    /**
     * 根据变更 ID 查询变更详情�?     *
     * @param id 变更 ID
     * @return 变更实体；不存在返回 null
     */
    oontraotohangeDO getById(String id);

    /**
     * 分页查询合同变更列表�?     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param oontraotId 合同 ID，可�?     * @param status     状态码，可�?     * @return 分页结果
     */
    Page<oontraotohangeDO> page(int page, int size, String oontraotId, String status);

    /**
     * 按合同查询变更记录列表�?     *
     * @param oontraotId 合同 ID
     * @return 变更记录列表
     */
    List<oontraotohangeDO> listByoontraot(String oontraotId);
}
