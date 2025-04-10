package org.example.backendai.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backendai.DTO.BookDTO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图书向量数据访问接口
 */
@Repository
@Mapper
public interface BookDTOMapper {
    
    /**
     @Repository
     public
      * 根据图书ID查找
     *
     * @param bookId 图书ID
     * @return 图书向量数据
     */
    @Select("SELECT * FROM book_vector WHERE book_id = #{bookId}")
    BookDTO findByBookId(@Param("bookId") String bookId);
    
    /**
     * 获取所有图书向量数据
     *
     * @return 所有图书向量
     */
    @Select("SELECT * FROM book_vector")
    List<BookDTO> findAll();
    
    /**
     * 随机获取指定数量的图书
     *
     * @param limit 数量限制
     * @return 图书列表
     */
    @Select("SELECT * FROM book_vector ORDER BY RAND() LIMIT #{limit}")
    List<BookDTO> findRandomBooks(@Param("limit") int limit);
    
    /**
     * 根据标题或描述关键词搜索图书
     *
     * @param keyword 关键词
     * @return 匹配的图书列表
     */
    @Select("SELECT * FROM book_vector WHERE title LIKE CONCAT('%', #{keyword}, '%') " +
            "OR description LIKE CONCAT('%', #{keyword}, '%') LIMIT 20")
    List<BookDTO> searchByKeyword(@Param("keyword") String keyword);
} 