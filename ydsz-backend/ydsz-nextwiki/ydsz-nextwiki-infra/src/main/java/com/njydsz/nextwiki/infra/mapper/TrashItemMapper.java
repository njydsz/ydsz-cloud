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

    TrashItem findByFileNodeId(@Param("fileNodeId") String fileNodeId);

    List<TrashItem> findActiveTrash(@Param("userId") String userId);

    List<TrashItem> findExpiredItems(@Param("limit") int limit);

    int countActiveTrash(@Param("userId") String userId);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("trashItem") TrashItem trashItem);
}
