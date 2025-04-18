package org.example.backendai.DTO;

import lombok.Data;

@Data
public class BookItem {
    private String title;
    private String author;
    private String isbn;
    private Double rating;
    private String summary;
    private Double relevance;

    public BookItem(String title, String author, String isbn,
                    Double rating, String summary, Double relevance) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.rating = rating;
        this.summary = summary;
        this.relevance = relevance;
    }
}