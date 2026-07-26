package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 语言 VO（不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LanguageVO {

    private String id;
    private String languageCode;
    private String languageName;
    private Integer isDefault;
    private Integer sortOrder;
    private String status;
}
