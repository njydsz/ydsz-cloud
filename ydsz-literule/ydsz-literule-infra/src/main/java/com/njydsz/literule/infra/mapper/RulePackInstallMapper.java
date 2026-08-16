package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.njydsz.literule.domain.entity.RulePackInstall;

/**
 * 规则包安装记录 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_pack_install</code>。
 * <p>安装记录追踪每个规则包在每个租户的安装时间、版本、状态（已安装/已卸载/升级失败）。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_tenant_pack — (租户+包+版本) 唯一索引</li>
 *   <li>idx_installed_at — 安装时间排序索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.literule.domain.entity.RulePackInstall 规则包安装实体
 * @see com.njydsz.literule.server.service.RulePackInstallService 规则包安装 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RulePackInstallMapper extends BaseMapper<RulePackInstall> {
}
