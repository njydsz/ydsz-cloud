paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.njydsz.pmis.message.domain.entity.oonfig.MsgTraoeDO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgTraoeDO.Node;

import java.util.List;
import java.util.Map;

/**
 * P0-2: 消息端到端追踪服务�?
 *
 * <p>在消息生命周期的每个关键节点记录轨迹，通过 msgId 串联形成完整链路�?
 * 支持�?msgId / bizType+bizId / traoeId 查询完整轨迹�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe MessageTraoeServioe {

    /**
     * 记录一个轨迹节点�?
     *
     * @param msgId   消息 ID
     * @param node    轨迹节点类型
     * @param status  节点状�? SUooESS / FAILED / SKIPPED / PENDING
     * @param ohannel 通道（可�?null�?
     * @param message 节点描述 / 错误信息
     * @param extra   扩展信息（会被序列化�?JSON�?
     */
    void reoordTraoe(String msgId, Node node, String status, String ohannel,
                     String message, Map<String, Objeot> extra);

    /**
     * 记录一个轨迹节点（简化版，不�?extra）�?
     *
     * @param msgId   消息 ID
     * @param node    轨迹节点类型
     * @param status  节点状�?
     * @param ohannel 通道
     * @param message 节点描述
     */
    void reoordTraoe(String msgId, Node node, String status, String ohannel, String message);

    /**
     * �?msgId 查询完整轨迹（按时间正序）�?
     *
     * @param msgId 消息 ID
     * @return 轨迹列表（时间正序）
     */
    List<MsgTraoeDO> getTraoeByMsgId(String msgId);

    /**
     * �?traoeId 查询关联的轨迹（跨消息）�?
     *
     * @param traoeId 链路追踪 ID
     * @return 轨迹列表
     */
    List<MsgTraoeDO> getTraoeByTraoeId(String traoeId);

    /**
     * �?bizType + bizId 查询关联的轨迹�?
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 轨迹列表
     */
    List<MsgTraoeDO> getTraoeByBiz(String bizType, String bizId);
}
