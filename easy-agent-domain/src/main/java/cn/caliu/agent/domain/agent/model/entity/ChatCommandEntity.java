package cn.caliu.agent.domain.agent.model.entity;

import com.google.genai.types.Part;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatCommandEntity {

    private String agentId;

    private String userId;

    private String sessionId;

    private List<Content.Text> texts;

    private List<Content.File> files;

    private List<Content.InlineData> inlineDatas;

    @Data
    public static class Content {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Text {
            private String message;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class File {
            private String fileUri;
            private String mimeType;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class InlineData {
            private byte[] bytes;
            private String mimeType;
        }

    }

    public ChatCommandEntity buildSessionCommand(String agentId, String userId) {
        ChatCommandEntity chatCommandEntity = new ChatCommandEntity();
        chatCommandEntity.setAgentId(agentId);
        chatCommandEntity.setUserId(userId);
        return chatCommandEntity;
    }

    public ChatCommandEntity buildChatCommand(String agentId, String userId, String message) {
        ChatCommandEntity chatCommandEntity = new ChatCommandEntity();
        chatCommandEntity.setAgentId(agentId);
        chatCommandEntity.setUserId(userId);

        List<Content.Text> texts = new ArrayList<>();
        texts.add(new Content.Text(message));

        chatCommandEntity.setTexts(texts);

        return chatCommandEntity;
    }

    public com.google.genai.types.Content toUserContent() {
        return com.google.genai.types.Content.builder()
                .role("user")
                .parts(toParts())
                .build();
    }

    public List<Part> toParts() {
        List<Part> parts = new ArrayList<>();
        appendTextParts(parts);
        appendFileParts(parts);
        appendInlineDataParts(parts);
        return parts;
    }

    private void appendTextParts(List<Part> parts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        for (Content.Text text : texts) {
            if (text == null) {
                continue;
            }
            parts.add(Part.fromText(text.getMessage()));
        }
    }

    private void appendFileParts(List<Part> parts) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (Content.File file : files) {
            if (file == null) {
                continue;
            }
            parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
        }
    }

    private void appendInlineDataParts(List<Part> parts) {
        if (inlineDatas == null || inlineDatas.isEmpty()) {
            return;
        }
        for (Content.InlineData inlineData : inlineDatas) {
            if (inlineData == null) {
                continue;
            }
            parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
        }
    }

}
