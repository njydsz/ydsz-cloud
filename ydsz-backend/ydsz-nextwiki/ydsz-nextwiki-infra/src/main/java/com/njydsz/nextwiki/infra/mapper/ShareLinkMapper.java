package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.ShareLink;

/**
 * 分享链接 Mapper
 *
 * <p>对应数据表 <code>ydsz_share_link</code>。
 * <p>分享链接是文件的对外可访问入口（含 token/过期时间/访问次数/密码），支持匿名访问与审计。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_link_token — 分享 token 唯一索引</li>
 *   <li>idx_file_id — 文件维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.nextwiki.domain.entity.ShareLink 分享链接实体
 * @see com.njydsz.nextwiki.server.service.ShareLinkService 分享 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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
