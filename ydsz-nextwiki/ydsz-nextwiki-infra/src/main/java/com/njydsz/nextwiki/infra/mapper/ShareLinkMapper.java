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

    /**
     * 按分享码查询分享链接，是外部用户通过分享入口访问文件的鉴权入口。
     *
     * @param shareCode 分享码（对外暴露的唯一标识）
     * @return 命中的分享链接实体；不存在则返回 null
     */
    ShareLink selectByShareCode(@Param("shareCode") String shareCode);

    /**
     * 查询指定文件节点下创建的全部分享链接（含有效与失效）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 分享链接列表
     */
    List<ShareLink> selectByFileNodeId(@Param("fileNodeId") String fileNodeId);

    /**
     * 查询某用户创建的、当前仍有效的分享链接列表（未过期且未撤销），用于"我的分享"管理页。
     *
     * @param userId 分享创建人 ID
     * @return 有效分享链接列表
     */
    List<ShareLink> selectActiveSharesByUserId(@Param("userId") String userId);

    /**
     * 撤销分享链接（将状态置为 revoked 使其立即失效），用于主动终止分享。
     *
     * @param id 分享链接主键
     * @return 受影响行数
     */
    @Update("UPDATE nw_share_link SET status = 'revoked', updated_at = NOW() WHERE id = #{id}")
    int revoke(@Param("id") String id);

    /**
     * 分享链接被访问时访问计数 +1（原子自增），用于热度统计与 maxAccessCount 限流判定。
     *
     * @param id 分享链接主键
     * @return 受影响行数
     */
    @Update("UPDATE nw_share_link SET access_count = access_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementAccessCount(@Param("id") String id);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("shareLink") ShareLink shareLink);
}
