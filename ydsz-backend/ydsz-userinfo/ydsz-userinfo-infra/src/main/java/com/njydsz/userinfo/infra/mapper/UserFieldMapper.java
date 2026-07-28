package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserField;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户自定义字段 Mapper
 *
 * <p>对应数据表 <code>ydsz_user_field</code>，存储用户表的扩展字段定义。</p>
 * <p>支持运行时扩展用户属性（不必修改 user 表结构），由 Service 层在用户查询时按 key-value 合并。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_user_field — (userId+fieldKey) 唯一索引</li>
 *   <li>idx_user_id — 用户维度查询索引</li>
 *   <li>idx_field_key — 字段 KEY 过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.UserField 用户扩展字段实体
 * @see com.njydsz.userinfo.server.service.UserFieldService 用户扩展字段 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserFieldMapper extends BaseMapper<UserField> {
}
