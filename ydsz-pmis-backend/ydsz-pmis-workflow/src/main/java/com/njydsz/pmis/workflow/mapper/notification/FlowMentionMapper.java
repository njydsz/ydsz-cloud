package com.njydsz.pmis.workflow.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.notification.FlowMentionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 审批 @提及 Mapper（P2-3）
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Mapper
public interface FlowMentionMapper extends BaseMapper<FlowMentionDO> {

    /**
     * 查询用户被提及的列表（关联实例标题）。
     */
    List<Map<String, Object>> selectMentionsForUser(@Param("userId") String userId,
                                                     @Param("tenantId") String tenantId,
                                                     @Param("onlyUnread") boolean onlyUnread);

    /**
     * 统计用户未读提及数。
     */
    long countUnread(@Param("userId") String userId, @Param("tenantId") String tenantId);
}
