package com.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.plugin.config.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public final class FTCDashboard {
    private WebSocket socket;
    public Throwable error;
    private final Project project;
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
    public FTCDashboard(Project p) {
        project = p;
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://192.168.43.1:8080"), new WebSocket.Listener() {
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
                }).thenAccept(sock -> socket = sock).exceptionallyAsync(err -> {
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

    private static class Visitor extends PsiRecursiveElementWalkingVisitor {
        private final CustomVariable config;
        private final PsiElementFactory factory;
        public Visitor(CustomVariable config, PsiElementFactory factory) {
            this.config = config;
            this.factory = factory;
        }
        @Override
        protected void elementFinished(PsiElement element) {
            if (element instanceof PsiClass out) {
                String name = out.getName();
                if (config.variables.containsKey(name)) {
                    ConfigVariable<?> _toChange = config.variables.get(name);
                    if (_toChange instanceof BasicVariable) return;
                    CustomVariable toChange = (CustomVariable) _toChange;
                    for (PsiField field: out.getFields()) {
                        if (!field.hasModifierProperty(PsiModifier.STATIC)) continue;
                        String fieldName = field.getName();
                        ConfigVariable<?> fieldValue = toChange.variables.get(fieldName);
                        if (fieldValue instanceof CustomVariable) continue;
                        BasicVariable<?> var = (BasicVariable<?>) fieldValue;
                        Object value = var.getValue();

                        String code;
                        switch (var.getType()) {
                            case INT:
                                if (field.getType() != PsiTypes.intType()) continue;
                                code = Integer.toString((int) value);
                                break;
                            case ENUM:
                                if (field.getType() instanceof PsiPrimitiveType) continue;
                                code = ((Enum<?>) value).name();
                                break;
                            case STRING:
                            case READONLY_STRING:
                                if (field.getType() instanceof PsiPrimitiveType) continue;
                                code = (String) value;
                                break;
                            case DOUBLE:
                                if (field.getType() != PsiTypes.doubleType()) continue;
                                code = Double.toString((double) value);
                                break;
                            case BOOLEAN:
                                if (field.getType() != PsiTypes.booleanType()) continue;
                                code = Boolean.toString((boolean) value);
                                break;
                            case LONG:
                                if (field.getType() != PsiTypes.longType()) continue;
                                code = Long.toString((long) value);
                                break;
                            case FLOAT:
                                if (field.getType() != PsiTypes.floatType()) continue;
                                code = Float.toString((float) value);
                                break;
                            case CUSTOM:
                            default:
                                continue;
                        }

                        field.setInitializer(factory.createExpressionFromText(code, null));
                    }
                }
            }
        }
    }

    private void onText(StringBuffer buffer) {
        CustomVariable config = parse(buffer);
        if (config == null) return;

        // Do something with config
        Visitor visitor = new Visitor(config, PsiElementFactory.getInstance(project));
        PsiDirectory file = PsiManager.getInstance(project).findDirectory(project.getProjectFile());
        WriteCommandAction.runWriteCommandAction(project, () -> visitor.visitDirectory(file));
    }
}