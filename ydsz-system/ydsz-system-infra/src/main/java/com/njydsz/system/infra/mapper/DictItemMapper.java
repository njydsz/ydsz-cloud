package com.njydsz.system.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictItem;

/**
 * 字典项 Mapper 接口
 *
 * <p>提供对 {@code ydsz_dict_item} 表的 CRUD 操作 + 高频查询自定义 SQL。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力；
 * 通过 {@link Select} 注解声明两个高频查询方法。
 *
 * <p><b>自定义 SQL：</b>
 * <ul>
 *   <li>{@link #selectByTypeAndCode} — 按 {@code (typeCode, itemCode)} 单条查询（已过滤启用 + 未删除）</li>
 *   <li>{@link #listEnabledByTypeCode} — 按 {@code typeCode} 批量查询（按 sort_order 升序）</li>
 * </ul>
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>逻辑删除：</b>实体配置了 {@code @TableLogic} 字段 {@code deleted}，删除为逻辑删除（{@code deleted=1}）。
 *
 * <p><b>索引利用：</b>{@code (type_code, item_code)} 命中 {@code idx_type_item} 唯一索引；
 * 启用状态过滤后剩余数据量小（一般 < 100 条），无需额外分页。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictItem 字典项实体
 * @see com.njydsz.system.server.service.DictItemService 字典项 Service
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {

    /**
     * 按类型编码和字典项编码查询启用的字典项
     *
     * <p>走 {@code idx_type_item} 唯一索引，仅返回 {@code status=ENABLED AND deleted=0} 的记录。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 DO；不存在返回 {@code null}
     */
    @Select("SELECT * FROM ydsz_dict_item WHERE type_code = #{typeCode} AND item_code = #{itemCode} "
            + "AND deleted = 0 AND status = 'ENABLED' LIMIT 1")
    DictItem selectByTypeAndCode(@Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

    /**
     * 按类型编码查询所有启用的字典项（按排序号升序）
     *
     * <p>前端下拉框核心数据源；命中 {@code idx_type_code} 索引。
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表（按 {@code sort_order} 升序）
     */
    @Select("SELECT * FROM ydsz_dict_item WHERE type_code = #{typeCode} AND deleted = 0 AND status = 'ENABLED' "
            + "ORDER BY sort_order ASC")
    List<DictItem> listEnabledByTypeCode(@Param("typeCode") String typeCode);

    /**
     * 物理删除指定类型编码下的所有字典项（含逻辑删除标记的记录）
     *
     * <p><b>慎用：</b>本方法绕过 MyBatis-Plus 逻辑删除机制，直接执行物理 DELETE。
     * 仅在「字典回滚」场景使用，实现「清空当前字典 + 从快照重建」的原子操作。
     *
     * @param typeCode 字典类型编码
     * @return 删除的记录数
     */
    @Delete("DELETE FROM ydsz_dict_item WHERE type_code = #{typeCode}")
    int physicalDeleteByTypeCode(@Param("typeCode") String typeCode);

    /**
     * 批量插入字典项（用于回滚重建）
     *
     * <p>直接继承 {@link com.baomidou.mybatisplus.core.mapper.BaseMapper#insert}，
     * 循环单条插入即可（回滚场景数据量一般 < 1000 条）。
     */
}
