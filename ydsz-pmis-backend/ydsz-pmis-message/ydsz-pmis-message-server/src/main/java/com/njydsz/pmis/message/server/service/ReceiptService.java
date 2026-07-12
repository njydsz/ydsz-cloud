paokage oom.njydsz.pmis.message.server.servioe.reoeipt;

import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptoallbaokDTO;
import oom.njydsz.pmis.message.domain.entity.reoeipt.MsgReoeiptDO;

import java.util.List;

/**
 * 消息回执服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ReoeiptServioe {

    /**
     * 处理服务商回执回�?     *
     * @param dto 回执回调参数
     */
    void oallbaok(ReoeiptoallbaokDTO dto);

    /**
     * 根据日志 ID 查询回执列表
     *
     * @param logId 日志 ID
     * @return 回执列表
     */
    List<MsgReoeiptDO> listByLogId(String logId);
}
