package com.njydsz.pmis.message.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChannelProperties 单元测试：验证默认值与嵌套配置结构。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class ChannelPropertiesTest {

    @Test
    void defaults_areApplied() {
        ChannelProperties properties = new ChannelProperties();

        assertNotNull(properties.getWebhook());
        assertNotNull(properties.getChannel());
        assertEquals("", properties.getWebhook().getDefaultUrl());
        assertEquals(5000, properties.getWebhook().getConnectTimeout());
        assertEquals(10000, properties.getWebhook().getReadTimeout());
    }

    @Test
    void dingtalkConfig_defaultsAndBinding() {
        ChannelProperties properties = new ChannelProperties();
        ChannelProperties.DingTalkConfig dingtalk = properties.getChannel().getDingtalk();

        assertNotNull(dingtalk);
        assertEquals("", dingtalk.getDefaultToken());
        assertEquals("", dingtalk.getSecret());
        assertEquals(5000, dingtalk.getConnectTimeout());
        assertEquals(10000, dingtalk.getReadTimeout());

        dingtalk.setDefaultToken("TOKEN");
        dingtalk.setSecret("SEC");
        assertEquals("TOKEN", dingtalk.getDefaultToken());
        assertEquals("SEC", dingtalk.getSecret());
    }

    @Test
    void wechatWorkConfig_defaultsAndBinding() {
        ChannelProperties properties = new ChannelProperties();
        ChannelProperties.WechatWorkConfig wechatWork = properties.getChannel().getWechatWork();

        assertNotNull(wechatWork);
        assertEquals("", wechatWork.getDefaultKey());
        assertEquals(5000, wechatWork.getConnectTimeout());
        assertEquals(10000, wechatWork.getReadTimeout());

        wechatWork.setDefaultKey("KEY");
        assertEquals("KEY", wechatWork.getDefaultKey());
    }

    @Test
    void feishuConfig_defaultsAndBinding() {
        ChannelProperties properties = new ChannelProperties();
        ChannelProperties.FeishuConfig feishu = properties.getChannel().getFeishu();

        assertNotNull(feishu);
        assertEquals("", feishu.getDefaultHook());
        assertEquals("", feishu.getSecret());
        assertEquals(5000, feishu.getConnectTimeout());
        assertEquals(10000, feishu.getReadTimeout());

        feishu.setDefaultHook("HOOK");
        feishu.setSecret("FS");
        assertEquals("HOOK", feishu.getDefaultHook());
        assertEquals("FS", feishu.getSecret());
    }

    @Test
    void webhookConfig_canOverrideDefaults() {
        ChannelProperties properties = new ChannelProperties();
        properties.getWebhook().setDefaultUrl("https://hook.example.com");
        properties.getWebhook().setConnectTimeout(2000);
        properties.getWebhook().setReadTimeout(4000);

        assertEquals("https://hook.example.com", properties.getWebhook().getDefaultUrl());
        assertEquals(2000, properties.getWebhook().getConnectTimeout());
        assertEquals(4000, properties.getWebhook().getReadTimeout());
    }
}
