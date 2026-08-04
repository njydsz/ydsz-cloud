package com.remisoft.common.json.testbean;

/**
 * 多词字段 Bean，用于命名策略测试（camelCase → snake_case 转换可见）。
 */
public class NamingBean {
    private String userName;
    private int userId;

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
}
