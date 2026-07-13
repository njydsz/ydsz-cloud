package com.njydsz.pmis.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.config.MsgOfflineDO;

/**
 * P0-3: 离线消息持久化 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface MsgOfflineMapper extends BaseMapper<MsgOfflineDO> {

    /**
     * 批量标记已推送。
     *
     * @param userId 用户 ID
     * @return 更新行数
     */
    @Update("UPDATE pmis_msg_offline SET status = 'PUSHED', pushed_at = NOW() " +
            "WHERE user_id = #{userId} AND status = 'PENDING' AND deleted = 0")
    int markPushedByUser(@Param("userId") String userId);

    /**
     * 清理过期消息（状态改为 EXPIRED）。
     *
     * @return 更新行数
     */
    @Update("UPDATE pmis_msg_offline SET status = 'EXPIRED' " +
            "WHERE status = 'PENDING' AND expired_at < NOW() AND deleted = 0")
    int markExpired();
}
