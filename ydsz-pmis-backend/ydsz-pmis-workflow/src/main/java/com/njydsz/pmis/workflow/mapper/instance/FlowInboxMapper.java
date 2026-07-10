package com.njydsz.pmis.workflow.mapper.instance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.instance.FlowInboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内信 Mapper（P2-4）
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Mapper
public interface FlowInboxMapper extends BaseMapper<FlowInboxDO> {

    /**
     * 查询用户站内信列表（真分页）。
     */
    List<FlowInboxDO> selectInboxByUser(@Param("receiverId") String receiverId,
                                         @Param("tenantId") String tenantId,
                                         @Param("onlyUnread") boolean onlyUnread,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 统计用户站内信总数。
     */
    long countInbox(@Param("receiverId") String receiverId,
                    @Param("tenantId") String tenantId,
                    @Param("onlyUnread") boolean onlyUnread);

    /**
     * 批量标记已读。
     */
    int batchMarkRead(@Param("receiverId") String receiverId,
                      @Param("tenantId") String tenantId,
                      @Param("ids") List<String> ids);
}
