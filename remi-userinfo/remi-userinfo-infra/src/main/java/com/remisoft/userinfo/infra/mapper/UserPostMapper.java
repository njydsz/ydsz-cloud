package com.remisoft.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.userinfo.domain.entity.UserPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-岗位关联表 Mapper
 *
 * <p>对应数据表 <code>remi_user_post</code>，存储用户与岗位的多对多关联。</p>
 * <p>支持一人多岗（PM/DEV/QA），是工作流审批人展开（{@code post:xxx}）的核心数据。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_user_post — (userId+postId) 唯一索引</li>
 *   <li>idx_user_id — 用户维度查询索引</li>
 *   <li>idx_post_id — 岗位维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.userinfo.domain.entity.UserPost 用户-岗位关联实体
 * @see com.remisoft.userinfo.server.service.UserPostService 用户-岗位 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserPostMapper extends BaseMapper<UserPost> {
}
