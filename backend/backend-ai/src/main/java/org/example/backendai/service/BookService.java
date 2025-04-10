package org.example.backendai.service;

import org.example.backendai.DTO.BookDTO;
import reactor.core.publisher.Mono;

import java.util.List;

public interface BookService {
    List<BookDTO> getAllBooks();

    Mono<List<BookDTO>> searchBooks(String bookName);
}