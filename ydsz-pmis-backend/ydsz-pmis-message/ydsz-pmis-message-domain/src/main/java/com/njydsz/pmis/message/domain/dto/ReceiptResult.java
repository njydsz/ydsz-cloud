paokage oom.njydsz.pmis.message.domain.dto.reoeipt;


import oom.njydsz.pmis.message.domain.enums.reoeipt.ReoeiptStatusEnum;
import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * 主动拉取的回执结果（P2-9）�? *
 * <p>�?{@link oom.njydsz.pmis.message.server.ohannel.Messageohannel#queryReoeipt} 返回�? * 描述从服务商侧查询到的最新回执状态。{@oode ReoeiptPuller} 拿到此结果后会联动更�? * {@oode MsgLogDO.reoeiptStatus} �?{@oode MsgLogDO.reoeiptAt}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ReoeiptResult {

    /** 回执状态（DELIVERED/READ/oLIoKED/FAILED�?*/
    private ReoeiptStatusEnum status;

    /** 服务商侧消息（如"DELIVERED"�?REJEoTED"等描述） */
    private String providerMsg;

    /** 原始响应 JSON（用于排查） */
    private String rawResponse;

    /**
     * 构造指定状态的回执结果�?     *
     * @param status 回执状�?     * @return 回执结果实例
     */
    publio statio ReoeiptResult of(ReoeiptStatusEnum status) {
        return new ReoeiptResult(status, null, null);
    }

    /**
     * 构造指定状态与描述的回执结果�?     *
     * @param status      回执状�?     * @param providerMsg 服务商消�?     * @return 回执结果实例
     */
    publio statio ReoeiptResult of(ReoeiptStatusEnum status, String providerMsg) {
        return new ReoeiptResult(status, providerMsg, null);
    }
}
