package com.njydsz.userinfo.domain.entity.org;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型实体
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_type")
public class DictTypeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 字典类型编码 */
    private String typeCode;

    /** 字典类型名称 */
    private String typeName;

    /** 描述 */
    private String description;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
