package com.remisoft.common.json;

import java.util.Arrays;
import java.util.List;

public class SerializeDebugTest {
    public static void main(String[] args) {
        try {
            TestUser u = new TestUser("Joe", 22, "joe@e.com");
            String json = RemiJson.toJson(u);
            System.out.println("Serialized: " + json);
        } catch (Exception e) {
            System.out.println("SER ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            String input = "{\"name\":\"Tom\",\"age\":40,\"email\":\"tom@e.com\"}";
            TestUser u = RemiJson.toObject(input, TestUser.class);
            System.out.println("Deserialized: " + u);
        } catch (Exception e) {
            System.out.println("DESER ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public record TestUser(String name, int age, String email) {}
}
