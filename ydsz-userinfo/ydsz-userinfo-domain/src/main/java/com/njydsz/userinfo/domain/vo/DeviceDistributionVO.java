package com.njydsz.userinfo.domain.vo;

/**
 * 设备分布 VO。
 *
 * <p>按设备类型（Web/App/API/Unknown）聚合的会话分布。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param deviceType 设备类型
 * @param percentage 占比（0.0-1.0）
 * @param count 会话数量
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public record DeviceDistributionVO(
    String deviceType,
    double percentage,
    int count) {
}
