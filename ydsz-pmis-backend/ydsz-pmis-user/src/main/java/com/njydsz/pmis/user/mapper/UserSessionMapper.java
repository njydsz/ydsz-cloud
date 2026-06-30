package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.UserSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSessionDO> {

    UserSessionDO selectBySessionId(@Param("sessionId") String sessionId);

    List<UserSessionDO> selectActiveByUserId(@Param("userId") Long userId);

    int updateStatus(@Param("sessionId") String sessionId,
                     @Param("status") String status,
                     @Param("logoutAt") java.time.LocalDateTime logoutAt,
                     @Param("logoutReason") String logoutReason);

    int kickOtherByUserId(@Param("userId") Long userId,
                          @Param("keepSessionId") String keepSessionId);
}
