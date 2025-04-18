package org.example.backendai.DTO;

import lombok.Data;
import java.util.List;

@Data
public class BookDTO {
    private Long id;
    private String title;
    private Double rating;
    private String description;
    private String author;
    private String isbn;
    private String coverUrl;
    private String authorInfo;
    private String category;
    private List<String> tags;
    private String publisher;
    private String originalTitle;
    private String publishYear;
    private String location;
    private Integer quantity;
    private Double similarity;  // 用于存储与查询的相似度
}