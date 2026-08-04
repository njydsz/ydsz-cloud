package com.njydsz.common.json;

/**
 * 用于 ASM 生效性测试的独立顶层 Bean（top-level，避免 inner class 的 $ 类名干扰 ASM 字节码生成）。
 */
public class TestBean {
    private int id;
    private String name;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
