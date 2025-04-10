package org.example.backendai.DTO;

import lombok.Data;

import java.util.List;
@Data
public class SpringChatRequest {
    private List<ChatRequest.Message> messages;
    private boolean stream;
    private Double temperature;
}
