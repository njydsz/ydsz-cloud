package com.njydsz.system.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 字典类型 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_dict_type} 表的展示视图和写入参数，是「字典中心」列表 / 详情 / 创建 / 更新接口的通用载体。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.DictType} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码，租户内唯一，业务存储 / 接口传输主键
 *   <li>{@code typeName} — 字典类型名称（前端展示文本，如「订单状态」「行业类型」）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>字典中心列表 / 详情 / 编辑回显
 *   <li>前端「字典类型选择器」数据源（管理员配置字典项前的下拉框）
 *   <li>字典项管理页的「按类型筛选」下拉框
 * </ul>
 *
 * <p><b>关联关系：</b>1 个 {@code DictTypeVO} 对应 N 个 {@link DictItemVO}，构成「类型 → 项」两级结构。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see DictItemVO 字典项 VO
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class DictTypeVO {

  private String id;

  @NotBlank(message = "字典类型编码不能为空")
  @Size(max = 64, message = "字典类型编码长度不能超过64")
  @Xss(message = "字典类型编码包含非法内容")
  private String typeCode;

  @NotBlank(message = "字典类型名称不能为空")
  @Size(max = 128, message = "字典类型名称长度不能超过128")
  @Xss(message = "字典类型名称包含非法内容")
  private String typeName;

  @Xss(message = "字典类型业务说明包含非法内容")
  private String description;

  private String status;
}
