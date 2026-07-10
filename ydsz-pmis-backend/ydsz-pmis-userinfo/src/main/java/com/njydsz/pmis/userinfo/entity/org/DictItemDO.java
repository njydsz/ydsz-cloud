package com.njydsz.pmis.userinfo.entity.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典项实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_dict_item")
public class DictItemDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属字典类型编码 */
    private String typeCode;

    /** 字典项编码 */
    private String itemCode;

    /** 字典项值 */
    private String itemValue;

    /** 排序号 */
    private Integer sortOrder;

    /** 父项 ID（0=根） */
    private String parentId;

    /** 描述 */
    private String description;

    /** 扩展属性 JSON */
    @TableField(jdbcType = org.apache.ibatis.type.JdbcType.OTHER)
    private String extJson;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
