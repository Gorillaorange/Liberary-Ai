package org.example.backendai.service.impl;

import org.example.backendai.DTO.BookDTO;
import org.example.backendai.mapper.BookMapper;
import org.example.backendai.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class BookServiceImpl implements BookService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookServiceImpl.class);
    
    @Autowired
    private BookMapper bookMapper;

    @Override
    public List<BookDTO> getAllBooks() {
        logger.info("开始获取所有书籍信息");
        List<BookDTO> books = bookMapper.findAllBooks();
        logger.info("获取到 {} 本书", books.size());
        return books;
    }

    @Override
    public Mono<List<BookDTO>> searchBooks(String bookName) {
        logger.info("开始搜索书籍: {}", bookName);
        try {
            List<BookDTO> books = bookMapper.searchBooks(bookName);
            logger.info("搜索完成，找到 {} 本书", books.size());
            return Mono.just(books);
        } catch (Exception e) {
            logger.error("搜索书籍时发生错误: {}", e.getMessage());
            return Mono.error(e);
        }
    }

}