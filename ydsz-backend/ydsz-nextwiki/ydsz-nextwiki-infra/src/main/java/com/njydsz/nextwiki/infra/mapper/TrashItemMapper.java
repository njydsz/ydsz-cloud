package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.TrashItem;

/**
 * 回收站 MyBatis Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
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
