package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_dict_type")
public class DictTypeDO extends MpBaseEntity<String> {

    private String tenantId;

    private String typeCode;
    private String typeName;
    private String description;

    @TableLogic
    private Integer deleted;
}
