package com.njydsz.userinfo.domain.vo;

/**
 * 登录失败原因分布 VO。
 *
 * <p>统计指定日期内各失败原因的分布情况，用于饼图展示。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param reason 失败原因描述
 * @param count 失败次数
 * @param percentage 占比（0.0-1.0）
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public record LoginFailDistributionVO(
    String reason,
    int count,
    double percentage) {
}
