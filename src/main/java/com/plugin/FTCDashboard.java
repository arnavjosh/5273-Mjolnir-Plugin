package com.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plugin.config.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;

public class FTCDashboard {
    private WebSocket socket;
    public Throwable error;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .registerTypeAdapter(BasicVariable.class, new ConfigVariableDeserializer())
            .registerTypeAdapter(CustomVariable.class, new ConfigVariableDeserializer())
            .serializeNulls()
            .create();
    private static CustomVariable parse(StringBuffer buffer) {
        Message msg = gson.fromJson(buffer.toString(), Message.class);
        if (msg.getType() != MessageType.RECEIVE_CONFIG) return null;
        return ((ReceiveConfig) msg).configRoot;
    }
    public FTCDashboard() {
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://192.168.43.1:8080/dash"), new WebSocket.Listener() {
                    private final StringBuffer current = new StringBuffer();
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        current.append(data);
                        if (last) {
                            // Do something
                            FTCDashboard.this.onText(current);
                            current.setLength(0);
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        socket = null;
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        FTCDashboard.this.error = error;
                    }
                }).thenAccept(sock -> {
                    socket = sock;
                }).exceptionallyAsync(err -> {
                    error = err;
                    return null;
                });
    }

    public void destroy() {
        if (socket != null) socket.abort();
    }

    public boolean isRunning() {
        return socket != null;
    }

    private void onText(StringBuffer buffer) {
        CustomVariable config = parse(buffer);
        if (config == null) return;

        // Do something with config
    }
}