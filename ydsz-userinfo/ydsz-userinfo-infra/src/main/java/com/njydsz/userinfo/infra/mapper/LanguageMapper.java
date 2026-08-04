package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.Language;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语言配置 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_language}，存储多语言信息（含默认语言唯一性管理）。
 * 用于前端 i18n 国际化与后端消息文案回退链（{@code LocaleContextHolder} 匹配 {@code ydsz_i18n_message} 表）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的
 * {@code LambdaQueryWrapper} 构造。默认语言唯一性由 Service 层在事务内维护。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>{@code uk_language_code} — 语言编码唯一索引（如 {@code zh-CN} / {@code en-US}）</li>
 *   <li>{@code idx_is_default} — 默认语言唯一索引（数据库层保证唯一）</li>
 *   <li>{@code idx_sort_order} — 排序字段索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>删除约束：</b>默认语言（{@code isDefault=true}）<b>禁止删除</b>，避免后端文案回退链断裂。
 * 由 Service 层在删除前校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.Language 语言实体
 */
@Mapper
public interface LanguageMapper extends BaseMapper<Language> {
}
