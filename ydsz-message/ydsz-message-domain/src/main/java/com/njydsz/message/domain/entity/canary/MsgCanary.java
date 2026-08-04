package com.njydsz.message.domain.entity.canary;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 灰度桶表: 按 canary_key(template_code/biz_type)做百分比灰度发布
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_canary")
public class MsgCanary extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 灰度键(如 template_code 或 biz_type) */
    private String canaryKey;

    /** 桶总数(默认 100) */
    private Integer bucketTotal;

    /** 命中的桶列表 JSON(如 [0,1,2,...,4] 表示 0-4 号桶命中) */
    private String bucketSelected;

    /** 灰度比例(0-100) */
    private Integer percentage;

    /** 灰度命中后切换的实验模板编码(可空,空则不切换) */
    private String experimentTemplateCode;

    /** 灰度命中后切换的实验通道(可空,空则不切换) */
    private String experimentChannel;

    /** 状态: ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 描述说明 */
    private String description;

}
