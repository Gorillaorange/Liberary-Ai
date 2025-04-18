package org.example.backendai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI API服务类，负责与AI模型服务通信
 * 
 * <p>主要功能：</p>
 * <ul>
 *   <li>发送流式请求并处理响应</li>
 *   <li>处理多种AI请求类型（聊天、问题分类等）</li>
 *   <li>统一管理API通信</li>
 *   <li>生成AI内容和创建嵌入向量</li>
 * </ul>
 */
@Service
public class AIApiService {

    private final Logger logger = LoggerFactory.getLogger(AIApiService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Value("${ai.forward.url}")
    private String aiForwardUrl;

    @Value("${custom-model.api-url}")
    private String aiApiBaseUrl;

    @Value("${custom-model.api-base}")
    private String aiApiBase;

    private final WebClient webClient;

    @Autowired
    public AIApiService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 分析问题类型
     * 
     * @param userInput 用户输入
     * @return 问题类型的Mono
     */
    public Mono<String> analyzeQuestionType(String userInput) {
        // 创建问题分析请求
        Map<String, Object> analysisRequest = Map.of(
            "text", "你是一个专门负责对用户问题进行分类的AI助手。请分析以下用户输入，将其分类为以下类别之一：\n" +
                    "1. GENERAL - 通用问答\n" +
                    "2. BOOK_SEARCH - 图书查询\n" +
                    "3. BOOK_RECOMMEND - 图书推荐\n" +
                    "4. BOOK_REVIEW - 图书评论\n" +
                    "5. CODE_QUESTION - 编程问题\n" +
                    "6. MATH_PROBLEM - 数学问题\n" +
                    "7. WRITING_HELP - 写作帮助\n" +
                    "8. BookBorrowing - 图书借阅\n" +
                    "9. RULES - 图书馆规则\n" +
                    "10. POINTS - 图书馆积分\n\n" +
                    "请仅返回类别代码，例如 BOOK_SEARCH，不要包含其他解释。\n\n" +
                    "用户输入: " + userInput,
            "system_prompt", "你是一个问题分类器。只输出分类结果，不要有任何其他内容。"
        );
        
        logger.info("开始分析问题类型，用户输入: {}", userInput.substring(0, Math.min(100, userInput.length())));
        logger.info("发送请求到AI服务: {}", aiForwardUrl);
        
        return webClient.post()
                .uri(aiForwardUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(analysisRequest)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSubscribe(subscription -> logger.info("开始订阅AI服务响应"))
                .doOnNext(response -> logger.info("收到AI服务响应: {}", response))
                .map(response -> {
                    response = response.replaceAll("\\\\n+", "<br>");
                    try {
                        if (response == null || response.trim().isEmpty()) {
                            logger.error("分析问题类型返回空响应");
                            throw new RuntimeException("无法从AI服务获取有效的问题类型分析");
                        }
                        
                        logger.info("解析响应JSON: {}", response);
                        
                        // 从响应中提取问题类型
                        String questionType = extractQuestionType(response);
                        if (questionType == null || questionType.trim().isEmpty()) {
                            logger.error("无法从响应中提取问题类型: {}", response);
                            throw new RuntimeException("无法从AI服务响应中提取有效的问题类型");
                        }
                        
                        // 清理问题类型，移除可能的额外字符
                        questionType = questionType.trim().toUpperCase();
                        logger.info("清理后的问题类型: {}", questionType);
                        
                        // 验证是否为有效的问题类型
                        if (!isValidQuestionType(questionType)) {
                            logger.error("提取到无效的问题类型: {}", questionType);
                            throw new RuntimeException("AI服务返回的问题类型'" + questionType + "'不在允许的范围内");
                        }
                        
                        // 记录成功获取的问题类型
                        logger.info("成功分析出问题类型: {}", questionType);
                        return questionType;
                    } catch (RuntimeException e) {
                        // 直接抛出运行时异常
                        throw e;
                    } catch (Exception e) {
                        logger.error("解析问题类型响应失败: {}", e.getMessage());
                        throw new RuntimeException("处理AI服务响应时发生错误: " + e.getMessage(), e);
                    }
                })
                .timeout(Duration.ofSeconds(100))
                .onErrorResume(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.error("分析问题类型超时: {}", error.getMessage());
                        return Mono.error(new RuntimeException("AI服务响应超时，请稍后重试"));
                    } else {
                        logger.error("分析问题类型时发生错误: {}", error.getMessage());
                        return Mono.error(new RuntimeException("AI服务问题分析失败: " + error.getMessage(), error));
                    }
                });
    }
    
    /**
     * 从响应中提取问题类型
     * 
     * @param response AI服务响应
     * @return 提取出的问题类型，如果无法提取则返回null
     */
    private String extractQuestionType(String response) {
        // 查找</think><br><br>后面的内容作为最终分类
        int index = response.lastIndexOf("</think><br><br>");
        if (index != -1) {
            String result = response.substring(index + 16).trim();
            // 清理结果，去除多余的引号和其他字符
            result = cleanQuestionType(result);
            logger.info("从</think><br><br>后提取到分类: {}", result);
            return result;
        }
        
        logger.warn("无法从响应中提取问题类型: {}", response);
        return null;
    }
    
    /**
     * 清理提取的问题类型，去除多余的引号和其他字符
     * 
     * @param questionType 原始提取的问题类型
     * @return 清理后的问题类型
     */
    private String cleanQuestionType(String questionType) {
        if (questionType == null) {
            return null;
        }
        
        // 去除末尾的引号
        questionType = questionType.replaceAll("[\"']$", "");
        
        // 如果有额外文本，取第一个单词
        if (questionType.contains(" ")) {
            questionType = questionType.split("\\s+")[0];
        }
        
        // 转为大写并去除空白
        return questionType.trim().toUpperCase();
    }

    /**
     * 验证问题类型是否有效
     * 
     * @param questionType 问题类型
     * @return 是否为有效类型
     */
    private boolean isValidQuestionType(String questionType) {
        List<String> validTypes = Arrays.asList(
            "GENERAL", "BOOK_SEARCH", "BOOK_RECOMMEND", "BOOK_REVIEW", 
            "CODE_QUESTION", "MATH_PROBLEM", "WRITING_HELP", 
            "BookBorrowing", "RULES", "POINTS"
        );
        return validTypes.contains(questionType);
    }

    /**
     * 处理聊天请求（返回流式响应）
     * 
     * @param requestBody 请求体
     * @param dataHandler 处理每个数据块的函数
     * @param completionHandler 请求完成时的处理函数
     * @param errorHandler 错误处理函数
     * @return Flux<String>，表示请求处理流程
     */
    public Flux<String> processChatStream(
            Map<String, Object> requestBody,
            Consumer<String> dataHandler,
            Runnable completionHandler,
            Consumer<Throwable> errorHandler) {
        
        logger.info("发送AI流式请求到: {}", aiForwardUrl);
        
        // 打印重要字段
        if (requestBody.containsKey("text")) {
            logger.info("请求中包含text字段: {}", 
                    requestBody.get("text").toString().substring(0, 
                            Math.min(100, requestBody.get("text").toString().length())) + "...");
        } else {
            logger.warn("请求中不包含text字段");
        }
        
        if (requestBody.containsKey("messages")) {
            List<Map<String, String>> messages = (List<Map<String, String>>) requestBody.get("messages");
            logger.info("请求中包含{}条历史消息", messages.size());
        }
        
        if (requestBody.containsKey("system_prompt")) {
            logger.info("请求中包含system_prompt字段");
        }
        
        logger.info("请求字段: {}", requestBody.keySet());
        
        return webClient.post()
                .uri(aiForwardUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                    response -> {
                        logger.error("客户端错误: HTTP {}", response.statusCode());
                        return Mono.error(new RuntimeException("API请求失败: " + response.statusCode()));
                    })
                .onStatus(status -> status.is5xxServerError(),
                    response -> {
                        logger.error("服务器错误: HTTP {}", response.statusCode());
                        return Mono.error(new RuntimeException("AI服务暂时不可用: " + response.statusCode()));
                    })
                .bodyToFlux(DataBuffer.class)
                .timeout(java.time.Duration.ofSeconds(600))
                .onErrorResume(error -> {
                    logger.error("AI流式请求出错，使用后备响应: {}", error.getMessage());
                    String fallbackResponse = "data: {\"type\":\"content\",\"data\":\"抱歉，AI服务暂时不可用，请稍后再试。\"}";
                    byte[] bytes = fallbackResponse.getBytes(StandardCharsets.UTF_8);
                    return Flux.just(bufferFactory.wrap(bytes));
                })
                .map(buffer -> {
                    String chunk = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    return chunk;
                })
                .doOnNext(dataHandler)
                .doOnComplete(completionHandler)
                .doOnError(errorHandler);
    }

    /**
     * 设置SSE发射器的处理流程
     * 
     * @param emitter SSE发射器
     * @param requestBody API请求体
     * @param chunkProcessor 处理每个数据块的函数
     * @param completionHandler 请求完成时的处理函数
     * @param errorHandler 错误处理函数
     */
    public void setupSseEmitter(
            SseEmitter emitter,
            Map<String, Object> requestBody,
            Function<String, Void> chunkProcessor,
            Runnable completionHandler,
            Function<Throwable, Void> errorHandler) {
        
        // 设置SSE连接处理器
        emitter.onCompletion(() -> logger.info("客户端断开连接"));
        emitter.onTimeout(() -> logger.warn("SSE连接超时"));
        emitter.onError(error -> logger.error("SSE连接错误: {}", error.getMessage()));
        
        // 处理流式响应
        processChatStream(
            requestBody,
            chunk -> {
                try {
                    chunkProcessor.apply(chunk);
                } catch (Exception e) {
                    logger.error("处理数据块时出错: {}", e.getMessage());
                }
            },
            completionHandler,
            error -> {
                try {
                    errorHandler.apply(error);
                } catch (Exception e) {
                    logger.error("处理错误时出错: {}", e.getMessage());
                }
            }
        ).subscribe();
    }

    /**
     * 执行自定义AI请求
     * 
     * @param requestBody 请求体
     * @return 原始响应文本
     */
    public String executeCustomRequest(Map<String, Object> requestBody) {
        logger.info("执行自定义AI请求");
        try {
            return webClient.post()
                .uri(aiForwardUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(30));
        } catch (Exception e) {
            logger.error("执行自定义AI请求失败", e);
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
    
    /**
     * 分析用户兴趣标签
     * 
     * @param requestBody 请求体，包含用户ID和聊天内容
     * @return AI分析结果，包含兴趣标签列表
     */
    public Map<String, Object> analyzeUserInterests(Map<String, Object> requestBody) {
        logger.info("开始分析用户兴趣标签");
        try {
            // 提取用户ID和聊天内容
            Long userId = (Long) requestBody.get("userId");
            String chatContent = (String) requestBody.get("chatContent");
            
            // 构建提示词
            String prompt = "作为一个阅读兴趣分析专家，请基于以下用户与AI助手的聊天记录，分析出用户可能感兴趣的阅读主题和图书类型。"
                    + "请提取5-10个用户最可能感兴趣的标签，每个标签不超过4个汉字。"
                    + "仅返回兴趣标签列表，格式为：标签1，标签2，标签3，……";
            
            // 创建请求体
            Map<String, Object> aiRequestBody = Map.of(
                "text", chatContent,
                "system_prompt", prompt
            );
            
            // 发送请求到AI服务并获取原始文本响应
            String responseText = executeCustomRequest(aiRequestBody);
            logger.info("AI返回的原始文本: {}", responseText);
            responseText = responseText.replaceAll("\\\\n", "<br>");
            // 提取<br></think><br><br>后的文字
            Pattern pattern = Pattern.compile("</think><br><br>(.+)");
            Matcher matcher = pattern.matcher(responseText);
            
            if (matcher.find()) {
                String tagsText = matcher.group(1).trim();
                // 清理标签文本中的多余引号
                tagsText = tagsText.replaceAll("^\"", "").replaceAll("\"$", "");
                logger.info("提取的标签文本: {}", tagsText);
                
                // 按逗号分隔并构建结果
                List<String> interests = java.util.Arrays.stream(tagsText.split("[，,]"))
                    .map(String::trim)
                    .map(tag -> tag.replaceAll("^\"", "").replaceAll("\"$", "")) // 清理每个标签中的引号
                    .filter(tag -> !tag.isEmpty())
                    .distinct() // 去除重复标签
                    .collect(java.util.stream.Collectors.toList());
                
                if (!interests.isEmpty()) {
                    // 不再在这里保存到数据库，由UserProfileService负责
                    Map<String, Object> result = Map.of("interests", interests);
                    return result;
                }
            }
            
            // 如果提取失败，返回空列表
            return Map.of(
                "interests", new java.util.ArrayList<>(),
                "generated", false,
                "message", "目前还无兴趣，多与助手聊天吧"
            );
            
        } catch (Exception e) {
            logger.error("分析用户兴趣标签失败", e);
            return Map.of(
                "interests", new java.util.ArrayList<>(),
                "generated", false,
                "message", "目前还无兴趣，多与助手聊天吧"
            );
        }
    }

    /**
     * 使用向量搜索查找相似图书
     *
     * @param query 搜索查询文本
     * @param limit 返回结果数量限制
     * @return 搜索结果
     */
    public Map<String, Object> searchSimilarBooks(String query, int limit) {
        try {
            String url = aiApiBase + "/search";
            logger.info("发送向量搜索请求到: {}", url);
            logger.info("查询内容: {}", query);
            logger.info("限制数量: {}", limit);
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("limit", limit);
            
            // 使用WebClient发送请求
            String responseJson = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(30));
            
            if (responseJson == null) {
                logger.error("向量搜索返回空结果");
                return Map.of("error", "搜索服务返回空结果");
            }
            
            logger.info("收到响应: {}", responseJson);
            
            // 将JSON字符串转换为Map
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            
            // 验证响应格式
            if (!response.containsKey("hits")) {
                logger.error("响应中缺少hits字段: {}", response);
                return Map.of("error", "响应格式不正确");
            }
            
            List<Map<String, Object>> hits = (List<Map<String, Object>>) response.get("hits");
            if (hits == null || hits.isEmpty()) {
                logger.warn("搜索结果为空");
                return Map.of("hits", Collections.emptyList());
            }
            
            // 验证每个hit的格式 - 调整为匹配实际返回的格式
            for (Map<String, Object> hit : hits) {
                if (!hit.containsKey("book_id") || !hit.containsKey("text")) {
                    logger.error("hit中缺少必要字段(book_id或text): {}", hit);
                    return Map.of("error", "响应格式不正确");
                }
                
                // 确保metadata字段存在
                if (!hit.containsKey("metadata")) {
                    logger.error("hit中缺少metadata字段: {}", hit);
                    return Map.of("error", "响应格式不正确");
                }
            }
            
            return response;
        } catch (Exception e) {
            logger.error("向量搜索失败: {}", e.getMessage(), e);
            return Map.of("error", "搜索服务调用失败: " + e.getMessage());
        }
    }
} 