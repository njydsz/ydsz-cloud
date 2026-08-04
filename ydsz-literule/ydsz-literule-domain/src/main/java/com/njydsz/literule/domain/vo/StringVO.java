package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用字符串包装视图对象（VO）。
 *
 * <p>用于接口返回单个字符串结果（如导出的规则文本、DSL 文本、简单回显等），
 * 避免直接返回裸 String 导致 JSON 反序列化歧义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StringVO {
    /** 包装的字符串值 */
    private String value;
}
