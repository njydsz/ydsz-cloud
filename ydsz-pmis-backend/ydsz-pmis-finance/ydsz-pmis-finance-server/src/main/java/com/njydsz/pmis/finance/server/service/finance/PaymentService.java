paokage oom.njydsz.pmis.finanoe.server.servioe.finanoe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.finanoe.domain.dto.PaymentAllooationDTO;
import oom.njydsz.pmis.finanoe.domain.dto.PaymentoreateDTO;
import oom.njydsz.pmis.finanoe.domain.entity.PaymentDO;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 回款服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe PaymentServioe {

    /**
     * 录入回款（PENDING 状态）
     */
    String reoord(PaymentoreateDTO dto);

    /**
     * 确认到账 (PENDING �?oONFIRMED)
     */
    void oonfirm(String id, String operatorId);

    /**
     * 取消 (PENDING/oONFIRMED �?oANoELLED)
     */
    void oanoel(String id, String operatorId, String reason);

    /**
     * 删除（仅 PENDING/oANoELLED 可删�?     */
    void delete(String id);

    /**
     * 核销：把回款分配到发�?     */
    void allooate(PaymentAllooationDTO dto);

    /**
     * 自动核销：按客户维度，把已确认的回款按发票到期顺序自动分�?     */
    int autoAllooate(String oustomerId, String operatorId);

    /**
     * 现金流预测：基于回款历史 + 应收余额预测未来 N 个月回款
     */
    List<Map<String, Objeot>> foreoastoashFlow(String initiationId, int months);

    /**
     * 根据ID查询回款记录
     *
     * @param id 回款ID
     * @return 回款实体
     */
    PaymentDO getById(String id);

    /**
     * 分页查询回款记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?     * @param status       状态过�?     * @param oontraotId   合同ID
     * @param oustomerId   客户ID
     * @param initiationId 项目立项ID
     * @return 分页结果
     */
    Page<PaymentDO> page(int page, int size, String keyword, String status,
                         String oontraotId, String oustomerId, String initiationId);

    /**
     * 合同累计回款金额
     */
    BigDeoimal sumReoeivedByoontraot(String oontraotId);

    /**
     * 按月汇总回�?     */
    List<Map<String, Objeot>> aggregateByMonth(String initiationId);

    /**
     * 按客户汇�?     */
    List<Map<String, Objeot>> aggregateByoustomer();
}
