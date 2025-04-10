package org.example.backendai.DTO;

import lombok.Data;

import java.util.List;

@Data
public class BookResponse {
    private List<BookItem> items;

    public BookResponse(List<BookItem> items) {
        this.items = items;
    }
}