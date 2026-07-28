package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账号 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_user_account}，存储用户账号基本信息。
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作（insert/update/selectById/selectList/deleteById 等）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有复杂查询通过 Service 层使用
 * MyBatis-Plus 的 {@code LambdaQueryWrapper} 构造，避免 XML 维护成本。
 * 跨服务名称富化（{@code batchUserNames}）通过 XML 写在 {@code UserAccountMapper.xml} 中。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>{@code uk_username} — 用户名唯一索引</li>
 *   <li>{@code idx_phone} — 手机号查询索引</li>
 *   <li>{@code idx_email} — 邮箱查询索引</li>
 *   <li>{@code idx_dept_id} — 部门查询索引（按部门查用户）</li>
 *   <li>{@code idx_tenant_status} — 租户+状态复合索引（多租户隔离）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.UserAccount 用户实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
