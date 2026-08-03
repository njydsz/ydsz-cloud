package com.njydsz.workflow.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用字符串包装视图对象（VO）。
 *
 * <p>用于接口返回单个字符串结果（如合并组 ID、用户 ID 列表等），
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
