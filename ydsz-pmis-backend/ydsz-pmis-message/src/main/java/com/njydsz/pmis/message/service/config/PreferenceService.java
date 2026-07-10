package com.njydsz.pmis.message.service.config;

import com.njydsz.pmis.message.dto.config.PreferenceUpsertDTO;
import com.njydsz.pmis.message.entity.config.MsgPreferenceDO;

import java.util.List;

/**
 * 用户消息偏好服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PreferenceService {

    /**
     * 新增或更新用户偏好
     *
     * @param dto 偏好参数
     * @return 偏好实体
     */
    MsgPreferenceDO upsert(PreferenceUpsertDTO dto);

    /**
     * 按用户 + 通道 + 业务类型查询偏好
     *
     * @param userId  用户 ID
     * @param channel 通道
     * @param bizType 业务类型
     * @return 偏好实体
     */
    MsgPreferenceDO getByUser(String userId, String channel, String bizType);

    /**
     * 查询用户所有偏好
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    List<MsgPreferenceDO> listByUser(String userId);

    /**
     * 删除偏好(逻辑删除)
     *
     * @param id 偏好 ID
     */
    void delete(String id);
}
