package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowNotifyOutboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流通知外发箱 Mapper（P2-1）
 *
 * <p>对应表 {@code pmis_flow_notify_outbox}，与全局事件外发表解耦，
 * 仅服务于工作流模块的可靠通知投递。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface FlowNotifyOutboxMapper extends BaseMapper<FlowNotifyOutboxDO> {

    /**
     * 扫描待投递事件（status=PENDING AND next_retry_at <= now）
     *
     * <p>使用 FOR UPDATE SKIP LOCKED 避免多实例并发抢占（PostgreSQL 14+）。
     *
     * @param now    当前时间
     * @param limit  最大扫描条数
     * @return 待投递事件列表
     */
    @Select("""
            SELECT * FROM pmis_flow_notify_outbox
            WHERE deleted = 0
              AND status = 'PENDING'
              AND next_retry_at <= #{now}
            ORDER BY id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<FlowNotifyOutboxDO> selectPendingForSend(@Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    /**
     * 标记事件投递成功
     *
     * @param id      事件 ID
     * @param sentAt  投递时间
     * @return 影响行数
     */
    @Update("""
            UPDATE pmis_flow_notify_outbox
            SET status = 'SENT',
                sent_at = #{sentAt},
                error_msg = NULL,
                updated_at = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int markSent(@Param("id") String id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 标记事件投递失败，增加重试计数
     *
     * @param id           事件 ID
     * @param errorMsg     错误信息
     * @param nextRetryAt  下次重试时间
     * @return 影响行数
     */
    @Update("""
            UPDATE pmis_flow_notify_outbox
            SET retry_count = retry_count + 1,
                error_msg = #{errorMsg},
                next_retry_at = #{nextRetryAt},
                updated_at = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int markRetry(@Param("id") String id,
                  @Param("errorMsg") String errorMsg,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * 标记事件为死信（重试次数超阈值）
     *
     * @param id       事件 ID
     * @param errorMsg 错误信息
     * @return 影响行数
     */
    @Update("""
            UPDATE pmis_flow_notify_outbox
            SET status = 'DEAD',
                error_msg = #{errorMsg},
                updated_at = NOW()
            WHERE id = #{id} AND deleted = 0
            """)
    int markDead(@Param("id") String id, @Param("errorMsg") String errorMsg);
}
