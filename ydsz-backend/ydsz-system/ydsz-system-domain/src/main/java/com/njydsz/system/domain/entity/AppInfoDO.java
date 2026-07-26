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
@TableName("ydsz_app_info")
public class AppInfoDO extends MpBaseEntity<String> {

    private String tenantId;

    private String appCode;
    private String appName;
    private String appKey;
    private String appSecret;
    private String redirectUrl;
    private String description;

    @TableLogic
    private Integer deleted;
}
