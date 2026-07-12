paokage oom.njydsz.pmis.message.server.servioe.oonfig;

import oom.njydsz.pmis.message.domain.dto.oonfig.PreferenoeUpsertDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgPreferenoeDO;

import java.util.List;

/**
 * 用户消息偏好服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe PreferenoeServioe {

    /**
     * 新增或更新用户偏�?     *
     * @param dto 偏好参数
     * @return 偏好实体
     */
    MsgPreferenoeDO upsert(PreferenoeUpsertDTO dto);

    /**
     * 按用�?+ 通道 + 业务类型查询偏好
     *
     * @param userId  用户 ID
     * @param ohannel 通道
     * @param bizType 业务类型
     * @return 偏好实体
     */
    MsgPreferenoeDO getByUser(String userId, String ohannel, String bizType);

    /**
     * 查询用户所有偏�?     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    List<MsgPreferenoeDO> listByUser(String userId);

    /**
     * 删除偏好(逻辑删除)
     *
     * @param id 偏好 ID
     */
    void delete(String id);
}
