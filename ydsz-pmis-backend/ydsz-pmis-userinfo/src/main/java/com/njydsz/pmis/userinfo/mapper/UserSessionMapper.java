package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.UserSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户会话 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSessionDO> {

    /**
     * 根据 sessionId 查询会话
     *
     * @param sessionId 会话 ID
     * @return 会话记录，未找到返回 null
     */
    UserSessionDO selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询某用户当前活跃的会话列表
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    List<UserSessionDO> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 更新会话状态（登出/失效）
     *
     * @param sessionId    会话 ID
     * @param status       目标状态
     * @param logoutAt     登出时间
     * @param logoutReason 登出原因
     * @return 受影响行数
     */
    int updateStatus(@Param("sessionId") String sessionId,
                     @Param("status") String status,
                     @Param("logoutAt") java.time.LocalDateTime logoutAt,
                     @Param("logoutReason") String logoutReason);

    /**
     * 踢掉某用户除保留会话外的其他活跃会话（单点登录场景）
     *
     * @param userId        用户 ID
     * @param keepSessionId 保留的会话 ID
     * @return 被踢掉的会话数
     */
    int kickOtherByUserId(@Param("userId") Long userId,
                          @Param("keepSessionId") String keepSessionId);
}
