package com.njydsz.system.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictVersion;

/**
 * 字典版本 Mapper 接口
 *
 * <p>提供对 {@code ydsz_dict_version} 表的 CRUD 操作 + 版本历史查询自定义 SQL。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力；
 * 通过 {@link Select} 注解声明按 {@code typeCode} 查询版本历史的方法。
 *
 * <p><b>自定义 SQL：</b>
 * <ul>
 *   <li>{@link #listByTypeCode} — 按 {@code typeCode} 查询版本历史（按 {@code effective_date} 倒序）</li>
 * </ul>
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>逻辑删除：</b>实体配置了 {@code @TableLogic} 字段 {@code deleted}，删除为逻辑删除。
 *
 * <p><b>索引利用：</b>{@code (type_code, effective_date)} 命中 {@code idx_type_code_version} 复合索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 * @see com.njydsz.system.server.service.DictVersionService 字典版本 Service
 */
@Mapper
public interface DictVersionMapper extends BaseMapper<DictVersion> {

    /**
     * 按类型编码查询版本历史（按生效时间倒序）
     *
     * <p>走 {@code idx_type_code_version} 复合索引；返回该 typeCode 下所有有效版本（{@code deleted=0}），
     * 最新版本排首位。
     *
     * @param typeCode 字典类型编码
     * @return 版本列表（按 {@code effective_date} 倒序）
     */
    @Select("SELECT * FROM ydsz_dict_version WHERE type_code = #{typeCode} AND deleted = 0 "
            + "ORDER BY effective_date DESC")
    List<DictVersion> listByTypeCode(@Param("typeCode") String typeCode);
}
