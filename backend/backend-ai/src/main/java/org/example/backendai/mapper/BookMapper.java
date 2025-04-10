package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.DTO.BookDTO;
import org.example.backendai.entity.Book;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 书籍数据访问接口
 */
@Mapper
@Repository
public interface BookMapper {
    
    /**
     * 根据标题查找书籍
     *
     * @param title 书籍标题
     * @return 书籍实体
     */
    @Select("SELECT * FROM book WHERE title = #{title} LIMIT 1")
    Book findByTitle(@Param("title") String title);
    
    /**
     * 根据ID查找书籍
     *
     * @param id 书籍ID
     * @return 书籍实体
     */
    @Select("SELECT * FROM book WHERE id = #{id}")
    Book findById(@Param("id") String id);
    
    /**
     * 查询用户感兴趣的书籍
     *
     * @param userId 用户ID
     * @return 书籍列表
     */
    @Select("SELECT b.* FROM book b " +
            "JOIN user_book_interest ubi ON b.id = ubi.book_id " +
            "WHERE ubi.user_id = #{userId} " +
            "ORDER BY ubi.interest_level DESC, ubi.last_mention_time DESC")
    List<Book> findUserInterestedBooks(@Param("userId") Long userId);
    
    /**
     * 根据标题关键词搜索书籍
     *
     * @param keyword 标题关键词（含通配符）
     * @return 书籍列表
     */
    @Select("SELECT * FROM book WHERE title LIKE #{keyword} " +
            "ORDER BY created_at DESC LIMIT 20")
    List<Book> searchByTitleKeyword(@Param("keyword") String keyword);
    
    /**
     * 查询所有书籍
     *
     * @return 书籍列表
     */
    @Select("SELECT * FROM book ORDER BY created_at DESC")
    List<Book> findAll();
    
    /**
     * 插入书籍
     *
     * @param book 书籍实体
     * @return 影响行数
     */
    @Insert("INSERT INTO book (id, title, author, category, description, created_at) " +
            "VALUES (#{id}, #{title}, #{author}, #{category}, #{description}, #{createdAt})")
    int insert(Book book);
    
    /**
     * 更新书籍
     */
    @Update("UPDATE book SET title = #{title}, author = #{author}, " +
            "category = #{category}, description = #{description} " +
            "WHERE id = #{id}")
    int update(Book book);
    
    /**
     * 删除书籍
     */
    @Delete("DELETE FROM book WHERE id = #{id}")
    int deleteById(String id);
    
    /**
     * 查询所有书籍DTO列表
     *
     * @return 书籍DTO列表
     */
    @Select("SELECT title, author as authorProfile, category, description, created_at as publishYear FROM book ORDER BY created_at DESC")
    List<BookDTO> findAllBooks();
    
    /**
     * 根据书名搜索书籍DTO列表
     *
     * @param bookName 书名
     * @return 书籍DTO列表
     */
    @Select("SELECT title, author as authorProfile, category, description, created_at as publishYear FROM book WHERE title LIKE CONCAT('%', #{bookName}, '%') ORDER BY created_at DESC")
    List<BookDTO> searchBooks(@Param("bookName") String bookName);
}