package org.example.backendai.DTO;

import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Data
public class ChatRequest implements Serializable{
    @Getter
    private List<Message> messages;
    private boolean stream = true;
    private Double temperature;

    @Getter
    @Data
    public static class Message {
        private String role;
        private String content;

    }
}
