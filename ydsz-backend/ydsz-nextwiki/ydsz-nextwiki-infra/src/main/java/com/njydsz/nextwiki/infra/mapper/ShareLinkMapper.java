package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.ShareLink;

/**
 * 分享链接 MyBatis Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ShareLinkMapper extends BaseMapper<ShareLink> {

    ShareLink selectByShareCode(@Param("shareCode") String shareCode);

    List<ShareLink> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    List<ShareLink> selectActiveSharesByUserId(@Param("userId") String userId);

    @Update("UPDATE nw_share_link SET status = 'revoked', updated_at = NOW() WHERE id = #{id}")
    int revoke(@Param("id") String id);

    @Update("UPDATE nw_share_link SET access_count = access_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementAccessCount(@Param("id") String id);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("shareLink") ShareLink shareLink);
}
