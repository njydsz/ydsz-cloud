package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.TrashItem;

/**
 * 回收站 Mapper
 *
 * <p>对应数据表 <code>ydsz_trash_item</code>。
 * <p>回收站支持文件恢复/彻底删除/过期自动清理，是文件删除的软删除层。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_trash_id — 回收项 ID 唯一索引</li>
 *   <li>idx_user_id — 用户维度查询索引</li>
 *   <li>idx_deleted_at — 删除时间排序索引（用于过期清理）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.nextwiki.domain.entity.TrashItem 回收项实体
 * @see com.njydsz.nextwiki.server.service.TrashItemService 回收站 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TrashItemMapper extends BaseMapper<TrashItem> {

    /**
     * 按原文件节点 ID 查询其对应的回收站条目。
     *
     * @param fileNodeId 原文件节点 ID
     * @return 命中的回收站实体；不存在则返回 null
     */
    TrashItem findByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询某用户的活跃回收站条目列表（未过期、未彻底删除），用于回收站页面展示。
     *
     * @param userId 用户 ID
     * @return 活跃回收站条目列表
     */
    List<TrashItem> findActiveTrash(@Param("userId") String userId);

    /**
     * 查询已过期的回收站条目（用于定时清理任务），limit 限制单次批处理量以避免长事务。
     *
     * @param limit 返回数量上限
     * @return 已过期待清理的回收站条目列表
     */
    List<TrashItem> findExpiredItems(@Param("limit") int limit);

    /**
     * 统计某用户的活跃回收站条目数量（未过期、未彻底删除），用于回收站角标提示。
     *
     * @param userId 用户 ID
     * @return 活跃回收站条目数
     */
    int countActiveTrash(@Param("userId") String userId);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("trashItem") TrashItem trashItem);
}
