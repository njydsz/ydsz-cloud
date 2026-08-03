package com.njydsz.common.json;

import com.njydsz.common.json.provider.DeserializationProvider;
import org.junit.jupiter.api.Test;

class DebugBeanDeserializeTest {
    @Test
    void debugBeanDeserialize() {
        try {
            TestBean b = DeserializationProvider.deserialize("{\"id\":7,\"name\":\"alice\"}", TestBean.class);
            System.out.println("SUCCESS: " + b);
        } catch (Throwable t) {
            System.out.println("EXCEPTION: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(System.out);
            Throwable cause = t.getCause();
            while (cause != null) {
                System.out.println("CAUSED BY: " + cause.getClass().getName() + ": " + cause.getMessage());
                cause.printStackTrace(System.out);
                cause = cause.getCause();
            }
        }
    }
}
