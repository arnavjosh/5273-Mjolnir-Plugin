package com.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plugin.config.*;

public class Test {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .registerTypeAdapter(BasicVariable.class, new ConfigVariableDeserializer())
            .registerTypeAdapter(CustomVariable.class, new ConfigVariableDeserializer())
            .serializeNulls()
            .create();
    private static Message parse(StringBuffer buffer) {
        return gson.fromJson(buffer.toString(), Message.class);
    }
    private static CustomVariable onText(StringBuffer buffer) {
        Message msg = parse(buffer);
        if (msg.getType() != MessageType.RECEIVE_CONFIG) return null;
        return ((ReceiveConfig) msg).configRoot;
    }

    public static void main(String[] args) {
        String text = """
            {
                "type": "RECEIVE_CONFIG",
                "configRoot": {
                    "__type": "custom",
                    "__value": {
                        "hi there": {
                            "__type": "boolean",
                            "__value": 1
                        }
                    }
                }
            }
            """;
        CustomVariable result = onText(new StringBuffer(text));
        System.out.println(result.entrySet().iterator().next().getKey());
    }
}
