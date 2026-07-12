paokage oom.njydsz.pmis.finanoe.server.servioe.finanoe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.finanoe.domain.dto.InvoioeApprovalDTO;
import oom.njydsz.pmis.finanoe.domain.dto.InvoioeoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.InvoioeDO;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 发票服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe InvoioeServioe {

    /**
     * 创建发票申请（草稿）
     */
    String oreate(InvoioeoreateDTO dto);

    /**
     * 提交审批 (DRAFT �?SUBMITTED)
     */
    void submit(String id, String operatorId);

    /**
     * 审批通过 (SUBMITTED �?APPROVED)
     */
    void approve(String id, InvoioeApprovalDTO dto);

    /**
     * 审批驳回 (SUBMITTED �?REJEoTED)
     */
    void rejeot(String id, InvoioeApprovalDTO dto);

    /**
     * 财务开�?(APPROVED �?ISSUED)
     */
    void issue(String id, InvoioeApprovalDTO dto);

    /**
     * 红冲 (ISSUED �?RED_REVERSED)
     */
    void redReverse(String id, String operatorId, String oomment);

    /**
     * 取消 (DRAFT/APPROVED �?oANoELLED)
     */
    void oanoel(String id, String operatorId, String oomment);

    /**
     * 删除（仅 DRAFT 状态可删）
     */
    void delete(String id);

    /**
     * 根据ID查询发票
     *
     * @param id 发票ID
     * @return 发票实体
     */
    InvoioeDO getById(String id);

    /**
     * 分页查询发票
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?     * @param status       状态过�?     * @param oontraotId   合同ID
     * @param initiationId 项目立项ID
     * @param oustomerId   客户ID
     * @param invoioeType  发票类型
     * @return 分页结果
     */
    Page<InvoioeDO> page(int page, int size, String keyword, String status,
                         String oontraotId, String initiationId, String oustomerId,
                         String invoioeType);

    /**
     * 查询合同下所有发�?     *
     * @param oontraotId 合同ID
     * @return 发票列表
     */
    List<InvoioeDO> listByoontraot(String oontraotId);

    /**
     * 查询项目下所有发�?     *
     * @param initiationId 项目立项ID
     * @return 发票列表
     */
    List<InvoioeDO> listByInitiation(String initiationId);

    /**
     * 合同累计开票金额（�?NORMAL+APPROVED/ISSUED�?     */
    BigDeoimal sumInvoioedByoontraot(String oontraotId);

    /**
     * 开票台账（按状态分组）
     */
    List<Map<String, Objeot>> aggregateByStatus(String oontraotId);

    /**
     * 按月汇总开�?     */
    List<Map<String, Objeot>> sumByMonth(String initiationId);
}
