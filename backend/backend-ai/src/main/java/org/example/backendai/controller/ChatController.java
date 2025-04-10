package org.example.backendai.controller;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.backendai.DTO.ChatRequest;
import org.example.backendai.DTO.BookDTO;
import org.example.backendai.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.backendai.DTO.ChatMessageDTO;
import org.example.backendai.DTO.ChatSessionDTO;
import org.example.backendai.service.ChatMessageService;
import org.example.backendai.service.ChatSessionService;
import org.example.backendai.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.io.IOException;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Calendar;
import org.example.backendai.entity.User;
import org.example.backendai.entity.ChatSession;
import org.example.backendai.service.UserService;
import org.example.backendai.service.AIApiService;
import org.example.backendai.service.BookRecommendationService;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Date;
import java.util.Optional;

/**
 * 聊天请求核心控制器
 *
 * <p>职责说明：
 * 1. 处理SSE（Server-Sent Events）流式响应
 * 2. 实现书籍信息实时查询功能
 * 3. 管理FastAPI服务通信及响应分块处理
 * 4. 错误处理及缓冲区资源管理</p>
 *
 * @author AI助手
 * @version 1.4
 */

@Data
@RestController
@RequestMapping("/api")
public class ChatController {

    private final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private BookService bookService;
    
    @Value("${ai.forward.url}")
    private String aiForwardUrl;

    @Autowired
    private ChatSessionService chatSessionService;
    
    @Autowired
    private ChatMessageService chatMessageService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserService userService;

    @Autowired
    private AIApiService aiApiService;

    @Autowired
    private BookRecommendationService bookRecommendationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * WebClient配置（FastAPI服务通信）
     * <ul>
     *   <li>baseUrl: 目标服务地址</li>
     *   <li>默认连接超时：5秒</li>
     *   <li>响应超时：30秒</li>
     * </ul>
     */
    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://10.100.1.92:6080")
            // 增加连接超时设置 (10秒)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB 缓冲区大小
            .filter((request, next) -> {
                logger.info("发送请求: {} {}", request.method(), request.url());
                return next.exchange(request)
                    .doOnError(error -> {
                        logger.error("请求失败: {}", error.getMessage());
                    });
            })
            .build();

    /**
     * 书籍名称匹配正则表达式
     * <p>匹配规则：中文书名号包裹的任意字符（非贪婪模式）</p>
     * <p>示例：</p>
     * <code>《Java编程思想》 → 匹配分组"Java编程思想"</code>
     */
    private static final Pattern BOOK_PATTERN = Pattern.compile("《([^》]+)》");

    /**
     * 全局布尔变量跟踪是否已经出现过"</think>"
     */
    private boolean hasSeenThink = false;

    /**
     * 系统提示词模板类型
     */
    private enum PromptTemplateType {
        BookBorrowing,//图书借阅模板
        Points,//图书馆积分模板
        DEFAULT,           // 默认通用模板
        BOOK_SEARCH,       // 图书查询模板
        BOOK_RECOMMEND,    // 图书推荐模板
        BOOK_REVIEW,       // 图书评论模板
        CODE_ASSISTANT,    // 代码助手模板
        MATH_ASSISTANT,    // 数学助手模板
        WRITING_ASSISTANT,  // 写作助手模板
        GENERAL, RULES
    }
    
    /**
     * 问题分类枚举
     */
    private enum QuestionType {
        BookBorrowing("图书借阅"),
        GENERAL("通用问答"),
        Points("图书馆积分"),
        BOOK_SEARCH("图书查询"),
        BOOK_RECOMMEND("图书推荐"),
        BOOK_REVIEW("图书评论"),
        CODE_QUESTION("编程问题"),
        MATH_PROBLEM("数学问题"),
        WRITING_HELP("写作帮助"),

        RULES("规则类问题"),
        UNKNOWN("未知类型");
        
        private final String description;
        
        QuestionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 获取系统提示词模板
     * 
     * @param type 模板类型
     * @return 对应类型的提示词模板
     */
    private String getPromptTemplate(PromptTemplateType type) {
        switch (type) {
            case BookBorrowing:
                return """
                        图书馆图书外借管理办法（修订）（20221202）
                        为满足学校教学和科研的需要，充分发挥图书馆图书资源的保障作用，维护图书馆图书外借秩序，依据《普通高等学校图书馆规程》（教高〔2015〕14号），结合图书馆工作实际，特修订图书馆图书外借管理办法。
                        第一章 借书权限
                        第一条 本校教职工、全日制研究生、全日制本/专科生已办理的校园卡，经图书馆开通借书权限后可在图书馆借书。
                        第二条 读者离开学校（如毕业、退学、结业、休学、兵役、工作调动、辞职、退休等），将被注销借书权限。
                        第二章 图书外借及归还
                        第三条 外借
                        1．读者须持本人校园卡到图书馆在服务台或自助借还书机办理图书出借手续。不得代借或转借，因代借或转借而造成的后果由校园卡所有人负责。
                        2．全校教职工、全日制研究生、全日制本/专科生每人均可同时外借30册图书，外借期限为60天。
                        3．读者外借图书时应当场检查，如发现污损等情况，应及时请工作人员记录处理，以分清责任。读者对所借图书应妥加爱护保管，如有污损、缺页、遗失等情况，按规定赔偿。
                        4．图书馆特藏图书、外文图书仅供阅览，不予外借。
                        第四条 归还和续借
                        1．读者外借图书应按期归还，可在自助借还书机或服务台办理归还手续。
                        2．图书逾期前如需继续使用，可办理续借手续，可续借 2 次，逾期的图书不得续借。续借手续可自行在网上或自助借还机办理，也可持校园卡到服务台办理。续借周期和原外借周期相同，从续借之日起计算。
                        3．借出图书到期日如为法定节假日、寒暑假期间及因特殊情况闭馆期间的，则借期顺延至假期结束或开馆之后。
                        4．读者离校前，应还清全部所借图书。学生毕业离校的，还清图书后才能通过离校审核。持人事部门所发的离校单读者，还清图书后才能到图书馆服务台办理盖章手续。
                        第五条 催还\s
                        借出图书到期前 3 天和逾期后，图书馆通过短信或微信发送催还通知，读者收到催还通知后应尽快归还所借图书。
                   """;
            case Points:
                return """
                        关于积分的问题，引用如下规则进行回答：
                        图书馆（档案馆）关于读者积分的管理办法
                                 为了鼓励读者主动学习和阅读，自觉爱护阅览室环境，图书馆特推出读者积分管理办法。图书馆通过系统实时记录读者行为，并自动为读者分配相应积分。
                                 第一条 加分项。图书馆根据读者的以下正面行为给予读者积分加分鼓励。
                                 1.图书借阅
                                 2.在馆学习
                                 3.提交阅读心得或书评（原创、300字以上）
                                 4.参加图书馆举办的活动
                                 5.选修并通过《文献信息检索与论文写作》课程
                                 6.参加图书馆义务劳动
                                 7.关注图书馆微信公众号
                                 8.合理的资源推荐
                                 9.合理的管理和服务建议
                                 10.拾金不昧、助人为乐等正面行为
                                 11.图书馆根据需要临时拟定的其他加分项
                                 第二条 减分项。图书馆根据读者的以下负面行为给予读者积分减分惩罚。
                                 1.图书逾期
                                 2.遗失、盗窃、污损图书
                                 3.其他共享物品和公共设施（如雨伞、桌椅等）逾期、遗失盗窃等
                                 4.预约系统违约（如考研座位）
                                 5.不遵守图书馆规章（如在阅览室进食、衣冠不整、抽烟、高声喧哗等）
                                 6.图书馆根据需要临时拟定的其他减分项
                                 读者积分将作为图书馆分配自助学习室座位、储物柜等资源的重要依据。读者还可以参加图书馆在每年的读书周和服务月期间的推出积分换礼、积分抽奖等活动。
                                 第三条 图书馆读者积分的项目和分值设定根据实际情况可动态调整。
               """;
            case RULES:
                return """
                        你是专业的图书馆的助手，参考如下规则进行回答：
                        图书馆文明读者公约
                        图书馆是传播人类科学文化知识的场所，是弘扬精神文明的重要阵地。读者既是图书馆资源的使用者，又是图书馆环境的维护者。广大读者应该自觉遵守以下公共道德和秩序。
                        第一条 凭校园卡进出馆
                        读者须凭本人有效校园卡或微信电子校园卡刷卡进出图书馆。
                        第二条 爱护书刊和公共设施
                        爱护图书馆的书刊资料，文明借阅。不折叠、涂画、撕页、污损书刊，不随意标注。书刊报取阅后放回原处，未办理借阅手续的书刊报请勿带出馆。
                        爱护馆内设施设备，不随意涂抹刻画和破坏设备，不随意挪动桌椅。
                        未经许可，禁止在馆内张贴或散发广告及其它宣传品。
                        第三条 保持安静
                        轻拿轻放，轻声细语，不在室内大声讨论问题或制造影响他人的噪音，不在馆内喧哗。请将手机开到静音或震动状态，接听电话请到室外。
                        第四条 按时还书
                        遵守借阅制度，按时还书，加快流通，提高资源利用率。
                        第五条 安全防火
                        图书馆是重点防火单位，馆内任何地方严禁吸烟、用火。禁止将任何危险和高耗电电器带入馆内使用或充电。禁带易燃易爆等危险物品入馆。
                        第六条 注重形象，讲究卫生
                        注意自身的形象，进入图书馆时衣着大方得体，举止文雅。不穿背心、拖鞋或赤膊入馆。不做有碍观瞻的行为。
                        保持图书馆环境整洁，不随地吐痰、不乱扔废弃物，文明使用卫生间。
                        不叫外卖进图书馆，不在馆内二楼茶歇区以外区域进食，不在室内喝有气味的饮料。
                        第七条 遵守秩序
                        遵守图书馆相关规章制度，维护图书馆的工作秩序。
                        座位预约阅览室使用座位预约系统选择座位，预约者优先使用。
                        预约自助学习室座位者有优先使用权，本人不在座位时其他读者有权利使用。
                        不用物品抢占座位。不在公共区域乱放个人物品。
                        第八条 互相尊重，共创和谐
                        使用文明用语，遇到问题及时和工作人员沟通，彼此友善，互相尊重。服从图书馆工作人员管理，接受其他读者监督。共创和谐阅读的氛围，享受温馨快乐的读书生活。
                        第九条 诚信在馆
                        诚信使用图书馆资源，包括爱心伞、自助复印打印、座位预约、寄存柜、寄存箱、电子资源等。
                        第十条 对违反上述文明行为规范的读者，任何人都有权批评和制止其行为。同时，图书馆将依据《图书馆读者积分管理办法》等规章制度进行处理。
                        第十一条 之前的相关条例或规定如与本公约相冲突，以本公约为准。
                        第十二条 本公约由图书馆办公室负责解释，2019年12月修订，2020年1月6日图书馆（档案馆）党政联席会议通过后施行。
                        """;
            case BOOK_SEARCH:
                return "你是一个专业的图书查询助手，擅长帮助用户查找书籍信息。" +
                       "当用户询问关于书籍的问题时，你应该尽可能详细地提供图书的相关信息，" +
                       "包括但不限于作者、出版社、内容简介、评分等。" +
                       "如果用户查询的书籍在数据库中没有，请礼貌地告知并推荐类似的书籍。" +
                       "请用简洁专业的语言回答用户问题。";
                
            case BOOK_RECOMMEND:
                return """
                        你是一个专业的图书推荐助手，每次推荐3-5本书，擅长推荐用户喜欢的图书。识别用户的问题意图，如果是模糊的图书推荐，则根据用户的专业和喜好进行推荐，如果是具体的
                        图书则具体推荐，优先使用本地的图书数据内容，没有则推荐相关的图书。
                        1.每次推荐图书不超过五本，书名需要用《》包裹。然后介绍20字以上50字一下的该书的介绍。
                      
                        2.嵌入两天外界规则正确引导用户借书，讲述规矩人性化不死板：外借
                                 1．读者须持本人校园卡到图书馆在服务台或自助借还书机办理图书出借手续。不得代借或转借，因代借或转借而造成的后果由校园卡所有人负责。
                                 2．全校教职工、全日制研究生、全日制本/专科生每人均可同时外借30册图书，外借期限为60天。
                                 3．读者外借图书时应当场检查，如发现污损等情况，应及时请工作人员记录处理，以分清责任。读者对所借图书应妥加爱护保管，如有污损、缺页、遗失等情况，按规定赔偿。
                                 4．图书馆特藏图书、外文图书仅供阅览，不予外借。
                        """;
                
            case BOOK_REVIEW:
                return "你是一个专业的图书评论助手，擅长分析和评价图书的内容、写作风格和价值。" +
                       "当用户询问关于书籍的评价时，请提供客观、深入的分析，包括但不限于：" +
                       "1. 内容概述（不透露关键情节）\n" +
                       "2. 写作风格和语言特点\n" +
                       "3. 主题和思想价值\n" +
                       "4. 适合的读者群体\n" +
                       "5. 在文学史或专业领域中的地位\n" +
                       "请基于文学批评和专业知识进行评价，避免过于主观的判断。";
                
            case CODE_ASSISTANT:
                return "你是一个专业的编程助手，擅长解答各类编程问题和提供代码解决方案。" +
                       "在回答问题时，请遵循以下原则：\n" +
                       "1. 提供简洁、高效、易于理解的代码\n" +
                       "2. 解释代码的关键部分和工作原理\n" +
                       "3. 考虑代码的性能、安全性和最佳实践\n" +
                       "4. 适当提供相关的API文档或学习资源\n" +
                       "请根据用户的编程水平调整回答的详细程度，对初学者提供更多解释，对专业人士可以更加简洁。" +
                       "如果用户的问题不清晰，应主动询问更多细节以提供更准确的帮助。";
                
            case MATH_ASSISTANT:
                return "你是一个专业的数学辅导助手，擅长解答各类数学问题。" +
                       "在回答问题时，请遵循以下原则：\n" +
                       "1. 提供清晰的解题步骤和思路\n" +
                       "2. 说明使用的数学概念和公式\n" +
                       "3. 如有多种解法，可以介绍不同方法\n" +
                       "4. 对于复杂问题，可以分解为更简单的子问题\n" +
                       "请确保答案正确，并根据用户的数学水平调整解释的深度。" +
                       "鼓励用户理解概念而不仅仅是记住答案。";
                
            case WRITING_ASSISTANT:
                return "你是一个专业的写作助手，擅长提供各类写作帮助，包括创意写作、学术写作、应用文写作等。" +
                       "在提供帮助时，请注意以下几点：\n" +
                       "1. 保持用户的写作风格和意图\n" +
                       "2. 提供具体的修改建议和例子\n" +
                       "3. 解释修改的理由和写作原则\n" +
                       "4. 针对不同类型的写作提供相应的专业建议\n" +
                       "请尊重用户的创意，帮助他们提升表达能力而不是完全替代他们的思考。";
                
            case DEFAULT:
            default:
                return """
                       若返回图书则返回3-5本
                       你是图书馆助手，语气要符合图书馆问话的口吻。初次询问时参考回答两条注意事项：图书馆文明读者公约
                                                              图书馆是传播人类科学文化知识的场所，是弘扬精神文明的重要阵地。读者既是图书馆资源的使用者，又是图书馆环境的维护者。广大读者应该自觉遵守以下公共道德和秩序。
                                                              第一条 凭校园卡进出馆
                                                              读者须凭本人有效校园卡或微信电子校园卡刷卡进出图书馆。
                                                              第二条 爱护书刊和公共设施
                                                              爱护图书馆的书刊资料，文明借阅。不折叠、涂画、撕页、污损书刊，不随意标注。书刊报取阅后放回原处，未办理借阅手续的书刊报请勿带出馆。
                                                              爱护馆内设施设备，不随意涂抹刻画和破坏设备，不随意挪动桌椅。
                                                              未经许可，禁止在馆内张贴或散发广告及其它宣传品。
                                                              第三条 保持安静
                                                              轻拿轻放，轻声细语，不在室内大声讨论问题或制造影响他人的噪音，不在馆内喧哗。请将手机开到静音或震动状态，接听电话请到室外。
                                                              第四条 按时还书
                                                              遵守借阅制度，按时还书，加快流通，提高资源利用率。
                                                              第五条 安全防火
                                                              图书馆是重点防火单位，馆内任何地方严禁吸烟、用火。禁止将任何危险和高耗电电器带入馆内使用或充电。禁带易燃易爆等危险物品入馆。
                                                              第六条 注重形象，讲究卫生
                                                              注意自身的形象，进入图书馆时衣着大方得体，举止文雅。不穿背心、拖鞋或赤膊入馆。不做有碍观瞻的行为。
                                                              保持图书馆环境整洁，不随地吐痰、不乱扔废弃物，文明使用卫生间。
                                                              不叫外卖进图书馆，不在馆内二楼茶歇区以外区域进食，不在室内喝有气味的饮料。
                                                              第七条 遵守秩序
                                                              遵守图书馆相关规章制度，维护图书馆的工作秩序。
                                                              座位预约阅览室使用座位预约系统选择座位，预约者优先使用。
                                                              预约自助学习室座位者有优先使用权，本人不在座位时其他读者有权利使用。
                                                              不用物品抢占座位。不在公共区域乱放个人物品。
                                                              第八条 互相尊重，共创和谐
                                                              使用文明用语，遇到问题及时和工作人员沟通，彼此友善，互相尊重。服从图书馆工作人员管理，接受其他读者监督。共创和谐阅读的氛围，享受温馨快乐的读书生活。
                                                              第九条 诚信在馆
                                                              诚信使用图书馆资源，包括爱心伞、自助复印打印、座位预约、寄存柜、寄存箱、电子资源等。
                                                              第十条 对违反上述文明行为规范的读者，任何人都有权批评和制止其行为。同时，图书馆将依据《图书馆读者积分管理办法》等规章制度进行处理。
                                                              第十一条 之前的相关条例或规定如与本公约相冲突，以本公约为准。
                                                              第十二条 本公约由图书馆办公室负责解释，2019年12月修订，2020年1月6日图书馆（档案馆）党政联席会议通过后施行。
                       """;
        }
    }
    
    /**
     * 根据问题类型选择合适的提示词模板
     * 
     * @param questionType 问题类型
     * @return 提示词模板
     */
    private String selectPromptTemplate(String questionType) {
        PromptTemplateType templateType;
        
        switch (questionType) {
            case "BOOK_SEARCH":
                templateType = PromptTemplateType.BOOK_SEARCH;
                break;
            case "BOOK_RECOMMEND":
                templateType = PromptTemplateType.BOOK_RECOMMEND;
                break;
            case "BOOK_REVIEW":
                templateType = PromptTemplateType.BOOK_REVIEW;
                break;
            case "CODE_QUESTION":
                templateType = PromptTemplateType.CODE_ASSISTANT;
                break;
            case "MATH_PROBLEM":
                templateType = PromptTemplateType.MATH_ASSISTANT;
                break;
            case "WRITING_HELP":
                templateType = PromptTemplateType.WRITING_ASSISTANT;
                break;
            case "BookBorrowing":
                templateType = PromptTemplateType.BookBorrowing;
                break;
            case "RULES":
                templateType = PromptTemplateType.RULES;
                break;
            case "Points":
                templateType = PromptTemplateType.Points;
                break;
            case "GENERAL":
                templateType = PromptTemplateType.GENERAL;
                break;
            case "UNKNOWN":
            default:
                templateType = PromptTemplateType.DEFAULT;
                break;
        }
        
        return getPromptTemplate(templateType);
    }

    /**
     * 处理聊天请求（统一入口）
     */
    @PostMapping("/chat")
    public SseEmitter handleChatRequest(@RequestHeader(value = "Authorization", required = false) String token, 
                                        @RequestParam(value = "sessionId", required = false) String sessionId,
                                        @RequestBody(required = false) String messageContent) {
        // 创建SSE发射器，设置超时时间30分钟
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        
        // 初始化变量
        final long startTime = System.currentTimeMillis();
        final AtomicInteger chunkCounter = new AtomicInteger(0);
        final StringBuilder fullContent = new StringBuilder();
        final Set<String> bookNames = ConcurrentHashMap.newKeySet();
        final Map<String, BookDTO> foundBooks = new ConcurrentHashMap<>();
        
        // 重置think标记
        hasSeenThink = false;
        
        // 记录原始请求内容
        logger.info("接收到对话请求。会话ID: {}, 消息内容: {}", 
                sessionId, 
                messageContent != null ? 
                    (messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent) 
                    : "null");
        
        // 提取并保存token，用于后续获取用户信息
        String cleanToken = null;
        
        try {
            // 获取用户ID
            Long userId;
            if (token != null && token.startsWith("Bearer ")) {
                cleanToken = token.substring(7);
                userId = jwtUtil.getUserIdFromToken(cleanToken);
                if (userId == null) {
                    handleError(emitter, new IllegalArgumentException("无效的授权令牌"));
                    return emitter;
                }
            } else {
                handleError(emitter, new IllegalArgumentException("缺少授权令牌"));
                return emitter;
            }
            
            // 保存当前token为final变量供Lambda表达式使用
            final String finalCleanToken = cleanToken;
            
            // 会话ID处理 - 如果没有提供会话ID，则创建新会话
            String finalSessionId = sessionId;
            if (finalSessionId == null || finalSessionId.trim().isEmpty()) {
                try {
                    String title = "新对话";
                    logger.info("未提供会话ID，为用户{}创建新会话：{}", userId, title);
                    ChatSessionDTO newSession = chatSessionService.createSession(userId, title);
                    if (newSession != null) {
                        finalSessionId = newSession.getId();
                        logger.info("成功创建新会话，ID：{}", finalSessionId);
                    } else {
                        handleError(emitter, new IllegalArgumentException("创建新会话失败"));
                        logger.error("处理对话请求失败：无法创建新会话");
                            return emitter;
                        }
                } catch (Exception e) {
                    handleError(emitter, new IllegalArgumentException("创建新会话时发生错误：" + e.getMessage()));
                    logger.error("处理对话请求失败：创建新会话时出错", e);
                    return emitter;
                }
            }
            
            // 保存当前会话ID作为final变量供后续使用
            final String currentFinalSessionId = finalSessionId;
            
            // 处理消息内容 - 确保有有效内容
            if (messageContent == null || messageContent.trim().isEmpty()) {
                handleError(emitter, new IllegalArgumentException("消息内容不能为空"));
                logger.error("处理对话请求失败：消息内容为空");
                return emitter;
            }
            
            // 清理消息内容，移除多余的格式
            String cleanedMessageContent = messageContent.trim();
            try {
                // 尝试解析JSON，如果是JSON格式的消息
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(cleanedMessageContent);
                if (node.has("content")) {
                    // 如果是{"content":"实际内容"}格式，提取实际内容
                    cleanedMessageContent = node.get("content").asText();
                    logger.info("从JSON中提取实际消息内容: {}", cleanedMessageContent);
            }
        } catch (Exception e) {
                // 如果不是JSON格式，则保持原样
                logger.info("消息内容不是JSON格式，保持原样");
            }
            
            // 保存用户消息到会话
            logger.info("保存用户消息到会话: sessionId={}, userId={}, content={}", 
                    currentFinalSessionId, userId, 
                    cleanedMessageContent.length() > 50 ? 
                        cleanedMessageContent.substring(0, 50) + "..." : cleanedMessageContent);
            
            chatMessageService.addMessage(currentFinalSessionId, userId, "user", cleanedMessageContent);
            
            // 获取会话的历史消息（包含刚保存的消息）
            List<ChatMessageDTO> recentMessages = chatMessageService.getSessionMessages(finalSessionId, userId);
            if (recentMessages == null) {
                handleError(emitter, new IllegalArgumentException("会话不存在或您没有访问权限"));
                logger.error("处理对话请求失败：无法获取会话消息，会话ID={}, 用户ID={}", finalSessionId, userId);
        return emitter;
    }

            // 按时间顺序排序并限制历史消息数量，只取最近的对话
            int maxHistoryMessages = 10; // 最大历史消息数量
            if (recentMessages.size() > 1) {
                // 确保所有消息都有创建时间，避免排序时的空指针异常
                recentMessages.forEach(msg -> {
                    if (msg.getCreateTime() == null) {
                        msg.setCreateTime(new Date());
                        logger.warn("为消息 {} 设置了默认的创建时间", msg.getId());
                    }
                });
                
                recentMessages = recentMessages.stream()
                        .sorted((m1, m2) -> m2.getCreateTime().compareTo(m1.getCreateTime())) // 按时间降序
                        .limit(maxHistoryMessages) // 限制数量
                        .sorted((m1, m2) -> m1.getCreateTime().compareTo(m2.getCreateTime())) // 再按时间升序，确保顺序正确
                        .collect(Collectors.toList());
            }
            
            logger.info("会话 {} 使用 {} 条历史消息进行AI对话", 
                    finalSessionId, recentMessages.size());
            
            // 提取最后一条用户消息（当前用户输入）
            final String lastUserMessage = cleanedMessageContent.trim();
            logger.info("处理对话，当前用户输入: {}", 
                    lastUserMessage.length() > 100 ? lastUserMessage.substring(0, 100) + "..." : lastUserMessage);
            
            // 保存到final变量以供Lambda表达式使用
            final List<ChatMessageDTO> finalRecentMessages = recentMessages;
            
            // 分析问题类型 - 使用最新的用户输入而不是历史消息
            aiApiService.analyzeQuestionType(lastUserMessage)
            .subscribe(questionType -> {
                logger.info("问题类型分析结果: {}", questionType);
                
                // 加强检查 - 如果返回的是默认值GENERAL，需要确认是真正分析的结果还是因为出错返回的默认值
                if (questionType == null || questionType.trim().isEmpty()) {
                    logger.error("模型未返回有效问题类型，中断处理");
                    handleError(emitter, new RuntimeException("AI服务暂时不可用，未能分析问题类型"));
                    return;
                }
                
                // 将问题类型转为final变量供lambda表达式使用
                final String finalQuestionType = questionType;
                        
                // 选择合适的系统提示词模板
                String systemPrompt = selectPromptTemplate(finalQuestionType);
                logger.info("选择的提示词模板: {}", systemPrompt);
                
                // 构造历史消息格式
                Map<String, Object> requestBody = getStringObjectMap(finalRecentMessages, systemPrompt, finalCleanToken);

                // 确保text字段包含当前问题 - 这是必需的字段
                if (!requestBody.containsKey("text") || requestBody.get("text") == null || 
                    requestBody.get("text").toString().trim().isEmpty()) {
                    logger.error("请求体中缺少text字段，这是必需的，中断处理");
                    handleError(emitter, new RuntimeException("系统错误：请求参数不完整"));
                    return;
                }
                
                // 记录最终的请求体
                logger.info("最终请求体结构: {}", requestBody.keySet());
                
                    // 设置SSE处理器
                aiApiService.setupSseEmitter(
                    emitter,
                        requestBody,
                    chunk -> {
                            try {
                                int currentChunk = chunkCounter.incrementAndGet();
                                
                                // 累积完整内容
                                fullContent.append(chunk);
                                
                                // 记录分块日志
                                logger.info("收到分块 {}: {}", currentChunk, chunk);
                                
                                // 处理每个分块并发送到客户端
                            processAndSendChunk(chunk, emitter, bookNames, fullContent, finalQuestionType);
                        } catch (Exception e) {
                            logger.error("处理分块时出错: {}", e.getMessage());
                        }
                        return null;
                    },
                    () -> {
                            long elapsedTime = System.currentTimeMillis() - startTime;
                            logger.info("对话流传输完成，耗时：{}ms", elapsedTime);
                            
                            // 如果内容长度足够，添加到会话历史
                            if (!fullContent.isEmpty()) {
                                String aiResponse = fullContent.toString();
                                // 异步保存AI回复到会话
                                chatMessageService.addMessage(currentFinalSessionId, userId, "assistant", aiResponse);
                                logger.info("AI回复已保存到会话历史");
                            }
                            
                            // 只在特定问题类型下查询并发送书籍信息
                                    if (shouldSendBookSummary(finalQuestionType)) {
                                logger.info("问题类型为{}，查询并发送书籍信息", finalQuestionType);
                                sendBookInfo(bookNames, emitter, foundBooks, fullContent)
                                    .doFinally(signal -> {
                                        // 发送书籍摘要
                                    sendBookSummary(bookNames, foundBooks, emitter, fullContent)
                                            .doFinally(signal2 -> {
                                                // 完成请求
                                                completeRequest(emitter, fullContent, chunkCounter, bookNames, foundBooks, startTime);
                                            })
                                            .subscribe();
                                            })
                                            .subscribe();
                                    } else {
                                // 如果不是书籍相关问题，直接完成请求
                                logger.info("问题类型为{}，跳过发送书籍信息", finalQuestionType);
                                        completeRequest(emitter, fullContent, chunkCounter, bookNames, foundBooks, startTime);
                                    }
                    },
                    error -> {
                            logger.error("处理对话请求发生错误: {}", error.getMessage());
                        handleError(emitter, error);
                        return null;
                    }
                );
            }, error -> {
                logger.error("问题类型分析失败: {}", error.getMessage());
                // 使用详细的错误消息
                String errorMessage = "AI服务分析问题类型失败: " + error.getMessage();
                logger.error(errorMessage);
                handleError(emitter, new RuntimeException(errorMessage));
            });
        } catch (Exception e) {
            logger.error("处理请求时发生错误: {}", e.getMessage());
            handleError(emitter, e);
        }
        
        return emitter;
    }

    /**
     * 查询并发送书籍信息
     *
     * @param bookNames 书籍名称集合
     * @param emitter 事件流发射器
     * @param foundBooks 成功查询到的书籍映射，用于记录查询结果
     * @param fullContent 完整内容StringBuilder
     * @return Mono<Void> 返回一个Mono表示完成
     */
    private Mono<Void> sendBookInfo(Set<String> bookNames, SseEmitter emitter, 
                                   Map<String, BookDTO> foundBooks, StringBuilder fullContent) {
        if (bookNames.isEmpty()) {
            logger.info("未发现书籍引用，跳过书籍信息查询");
            return Mono.empty();
        }

        logger.info("发现书籍引用: {}", bookNames);

        // 获取完整内容的字符串表示
        String fullContentStr = fullContent != null ? fullContent.toString() : "";
        logger.debug("获取到的完整内容长度: {}", fullContentStr.length());
        
        // 从完整内容中提取</think>后的部分
        String filteredContent = "";
        int thinkEndIndex = fullContentStr.lastIndexOf("</think>");
        if (thinkEndIndex >= 0) {
            filteredContent = fullContentStr.substring(thinkEndIndex + 8);
            logger.info("从完整内容中提取到</think>后的部分，长度: {}", filteredContent.length());
        } else {
            filteredContent = fullContentStr;
            logger.info("未找到</think>标签，使用完整内容，长度: {}", filteredContent.length());
        }
        
        // 重新从过滤后的内容中提取书籍名称
        Set<String> filteredBookNames = new HashSet<>();
        Matcher matcher = BOOK_PATTERN.matcher(filteredContent);
        while (matcher.find()) {
            String bookName = matcher.group(1);
            if (!bookName.isEmpty()) {
                filteredBookNames.add(bookName);
                logger.info("从</think>后的内容中提取到书籍名称: 《{}》", bookName);
            }
        }
        
        // 如果过滤后没有书籍名称，则跳过查询
        if (filteredBookNames.isEmpty()) {
            logger.info("</think>后的内容中未发现有效书籍引用，跳过查询");
            return Mono.empty();
        }

        return Flux.fromIterable(filteredBookNames)
                .flatMap(bookName -> {
                    logger.info("正在查询书籍信息: {}", bookName);
                    
                    // 直接查询tushu表
                    return searchBooksInTushuTable(bookName)
                            .flatMapMany(tushuBooks -> {
                                if (!tushuBooks.isEmpty()) {
                                    logger.info("在tushu表中找到书籍: {}, 数量: {}", bookName, tushuBooks.size());
                                    tushuBooks.forEach(book -> foundBooks.put(bookName, book));
                                    return Flux.fromIterable(tushuBooks);
                                } else {
                                    logger.warn("在tushu表中未找到书籍: {}", bookName);
                                    return Flux.empty();
                                }
                            })
                            .onErrorResume(error -> {
                                logger.error("查询tushu表时发生错误: {}, 书籍名称: {}", error.getMessage(), bookName);
                                return Flux.empty();
                            });
                })
                .flatMap(book -> {
                    try {
                        Map<String, Object> event = createBookInfoEvent(book);
                        return Mono.just(event);
                    } catch (Exception e) {
                        logger.error("创建书籍事件失败: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .delayElements(Duration.ofMillis(100))
                .then();
    }

    /**
     * 从tushu表中查询书籍
     * @param bookName 书籍名称
     * @return 书籍列表
     */
    private Mono<List<BookDTO>> searchBooksInTushuTable(String bookName) {
        return Mono.fromCallable(() -> {
            try {
                String sql = "SELECT id, title, pingfen as rating, chubanshe as publisher, " +
                        "neirong_jianjie as description, zuozhe_jianjie as authorInfo, " +
                        "chubannian as publishYear " +
                        "FROM tushu WHERE title LIKE ? ORDER BY pingfen DESC LIMIT 5";
                
                List<BookDTO> books = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> {
                        BookDTO book = new BookDTO();
                        book.setId(rs.getLong("id"));
                        book.setTitle(rs.getString("title"));
                        
                        try { book.setRating(rs.getDouble("rating")); } 
                        catch (Exception e) { /* 忽略获取评分错误 */ }
                        
                        try { book.setPublisher(rs.getString("publisher")); } 
                        catch (Exception e) { /* 忽略获取出版社错误 */ }
                        
                        try { book.setDescription(rs.getString("description")); } 
                        catch (Exception e) { /* 忽略获取描述错误 */ }
                        
                        try { book.setAuthorInfo(rs.getString("authorInfo")); } 
                        catch (Exception e) { /* 忽略获取作者信息错误 */ }
                        
                        try { book.setPublishYear(rs.getString("publishYear")); } 
                        catch (Exception e) { /* 忽略获取出版年份错误 */ }
                        
                        return book;
                    },
                    "%" + bookName + "%"
                );
                
                logger.info("从tushu表中查询到 {} 本书匹配 '{}'", books.size(), bookName);
                return books;
            } catch (Exception e) {
                logger.error("从tushu表查询时发生错误: {}", e.getMessage());
                throw e;
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 构建并发送书籍摘要信息
     *
     * @param bookNames 所有检测到的书籍名称
     * @param foundBooks 成功查询到的书籍
     * @param emitter 事件流发射器
     * @param fullContent 完整内容StringBuilder，用于追加书籍摘要
     * @return Mono<Void> 返回一个Mono表示完成
     */
    private Mono<Void> sendBookSummary(Set<String> bookNames, Map<String, BookDTO> foundBooks,
                                       SseEmitter emitter, StringBuilder fullContent) {
        if (bookNames.isEmpty()) {
            return Mono.empty();
        }

        try {
            StringBuilder summaryContent = new StringBuilder("<br><br>");
            summaryContent.append("书籍信息查询结果：<br />");

            for (String bookName : bookNames) {
                if (foundBooks.containsKey(bookName)) {
                    BookDTO book = foundBooks.get(bookName);
                    String authorProfile = getFieldValueSafely(book, "authorProfile", "未知作者");
                    String publisher = getFieldValueSafely(book, "publisher", "未知出版社");
                    Double rating = getFieldValueSafely(book, "rating", 0.0);
                    Integer quantity = getFieldValueSafely(book, "quantity", 0);

                    summaryContent.append("《").append(bookName).append("》");
                    
                    // 只添加非空信息
                    if (authorProfile != null && !authorProfile.isEmpty()) {
                        summaryContent.append(" - ").append(authorProfile);
                    }
                    if (publisher != null && !publisher.isEmpty()) {
                        summaryContent.append("，出版社：").append(publisher);
                    }
                    if (rating != null && rating > 0) {
                        summaryContent.append("，评分：").append(rating);
                    }
                    if (quantity != null && quantity > 0) {
                        summaryContent.append("，馆藏数量：").append(quantity);
                    }
                    
                    summaryContent.append("<br>");
                } else {
                    summaryContent.append("《").append(bookName).append("》").append(" - 未被馆藏收录<br>");
                }
            }

            // 追加到完整内容
            fullContent.append(summaryContent);

            // 发送摘要内容
            Map<String, Object> summaryEvent = new HashMap<>();
            summaryEvent.put("type", "content");
            summaryEvent.put("data", summaryContent.toString());

            emitter.send(SseEmitter.event()
                    .data(summaryEvent)
                    .name("chunk"));

            logger.info("已发送书籍摘要信息");
            logger.info(summaryContent.toString());

            return Mono.empty();
        } catch (IOException e) {
            logger.error("发送书籍摘要失败: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * 创建书籍信息事件对象
     *
     * @param book 书籍数据传输对象
     * @return 包含标准化字段的事件Map
     *
     * <p>事件结构：</p>
     * <pre>
     * {
     *   "type": "book_info",
     *   "title": "Java编程思想",
     *   "authorProfile": "Bruce Eckel",
     *   "publisher": "机械工业出版社",
     *   "rating": 9.5,
     *   "quantity": 10
     * }
     * </pre>
     */
    private Map<String, Object> createBookInfoEvent(BookDTO book) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "book_info");

        try {
            String title = getFieldValueSafely(book, "title", "未知书名");
            String authorProfile = getFieldValueSafely(book, "authorProfile", "未知作者");
            String publisher = getFieldValueSafely(book, "publisher", "未知出版社");
            Double rating = getFieldValueSafely(book, "rating", 0.0);
            Integer quantity = getFieldValueSafely(book, "quantity", 0);

            // 只添加非空字段
            event.put("title", title);
            if (authorProfile != null && !authorProfile.isEmpty()) {
                event.put("authorProfile", authorProfile);
            }
            if (publisher != null && !publisher.isEmpty()) {
                event.put("publisher", publisher);
            }
            if (rating != null && rating > 0) {
                event.put("rating", rating);
            }
            if (quantity != null && quantity > 0) {
                event.put("quantity", quantity);
            }
        } catch (Exception e) {
            logger.warn("获取书籍字段时出错: {}", e.getMessage());
            event.put("title", "数据获取失败");
        }

        return event;
    }

    /**
     * 安全获取对象字段值的辅助方法
     *
     * @param object 目标对象
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValueSafely(Object object, String fieldName, T defaultValue) {
        try {
            // 尝试直接调用getter方法
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

            try {
                // 尝试调用getter方法
                return (T) object.getClass().getMethod(getterName).invoke(object);
            } catch (NoSuchMethodException e) {
                // 如果没有getter方法，直接访问字段
                return (T) object.getClass().getDeclaredField(fieldName).get(object);
            }
        } catch (Exception e) {
            logger.warn("无法获取字段 " + fieldName + ": " + e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 处理请求错误的方法
     */
    private void handleError(SseEmitter emitter, Throwable error) {
            logger.error("发生错误: {}", error.getMessage());
        try {
            // 构建错误响应
            String errorMessage = "抱歉，服务暂时不可用，请稍后再试。";
            if (error.getMessage().contains("timeout") || error.getMessage().contains("TimeoutException")) {
                errorMessage = "请求超时，请稍后再试。";
            } else if (error.getMessage().contains("403")) {
                errorMessage = "未授权，请检查您的登录状态。";
            } else if (error.getMessage().contains("5")) {
                errorMessage = "AI服务暂时不可用，请稍后再试。";
            }
            
            // 发送错误消息
            String jsonError = "{\"type\":\"error\",\"data\":\"" + errorMessage + "\"}";
            emitter.send(jsonError, MediaType.APPLICATION_JSON);
            
            // 延迟500ms后完成发射器
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {}
            
            // 完成发射器
            emitter.complete();
        } catch (Exception e) {
            logger.error("发送错误响应失败: {}", e.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 完成请求，发送最终的统计信息和[DONE]信号
     */
    private void completeRequest(SseEmitter emitter, StringBuilder fullContent, AtomicInteger chunkCounter, 
                                Set<String> bookNames, Map<String, BookDTO> foundBooks, long startTime) {
        try {
            long elapsedTime = System.currentTimeMillis() - startTime;
            int totalChunks = chunkCounter.get();
            int bookCount = bookNames.size();
            int foundBookCount = foundBooks.size();
            
            logger.info("请求完成 - 统计: 处理了{}个分块, 耗时{}ms, 检测到{}本书, 找到{}本书信息", 
                    totalChunks, elapsedTime, bookCount, foundBookCount);
            
            // 发送完成信息
            String completionMessage = String.format("生成完成");
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data("{\"type\":\"content\",\"data\":\"" + completionMessage + "\"}"));
            
            // 明确发送[DONE]信号，告知前端流已结束
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            
            // 完成请求
            emitter.complete();
            
            logger.info("SSE流已关闭");
        } catch (Exception e) {
            logger.error("完成请求时出错: {}", e.getMessage());
            handleError(emitter, e);
        }
    }

    /**
     * 判断当前是否应该搜索书籍信息（基于问题类型）
     */
    private boolean shouldSearchForBooks() {
        return true; // 默认总是搜索，可以基于配置或其他条件调整
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestHeader("Authorization") String token,
                                          @RequestParam(value = "title", defaultValue = "新对话") String title) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        logger.info("创建会话: userId={}, title={}", userId, title);
        try {
            ChatSessionDTO session = chatSessionService.createSession(userId, title);
            if (session != null) {
                return ResponseEntity.ok(session);
            } else {
                return ResponseEntity.status(500).body("Failed to create session");
            }
        } catch (Exception e) {
            logger.error("创建会话异常", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户所有会话
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        List<ChatSessionDTO> sessions = chatSessionService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }
    
    /**
     * 获取会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@RequestHeader("Authorization") String token,
                                      @PathVariable String sessionId) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        ChatSessionDTO session = chatSessionService.getSessionById(sessionId, userId);
        if (session == null) {
            return ResponseEntity.status(404).body("Session not found");
        }
        
        return ResponseEntity.ok(session);
    }
    
    /**
     * 更新会话标题
     */
    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@RequestHeader("Authorization") String token,
                                         @PathVariable String sessionId,
                                         @RequestParam String title) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            logger.warn("更新会话标题失败：未授权，sessionId={}, title={}", sessionId, title);
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        logger.info("尝试更新会话标题：userId={}, sessionId={}, title={}", userId, sessionId, title);
        
        boolean updated = chatSessionService.updateSession(sessionId, title, userId);
        if (!updated) {
            logger.warn("更新会话标题失败：会话不存在或无权限，userId={}, sessionId={}", userId, sessionId);
            return ResponseEntity.status(404).body("Session not found");
        }
        
        logger.info("会话标题更新成功：userId={}, sessionId={}, title={}", userId, sessionId, title);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@RequestHeader("Authorization") String token,
                                         @PathVariable String sessionId) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            logger.warn("删除会话失败：未授权，sessionId={}", sessionId);
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        logger.info("尝试删除会话：userId={}, sessionId={}", userId, sessionId);
        
        boolean deleted = chatSessionService.deleteSession(sessionId, userId);
        if (!deleted) {
            logger.warn("删除会话失败：会话不存在或无权限，userId={}, sessionId={}", userId, sessionId);
            return ResponseEntity.status(404).body("Session not found");
        }
        
        logger.info("会话删除成功：userId={}, sessionId={}", userId, sessionId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 清空所有会话
     */
    @DeleteMapping("/sessions")
    public ResponseEntity<?> clearSessions(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            logger.warn("清空会话失败：未授权");
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        logger.info("尝试清空所有会话：userId={}", userId);
        
        boolean cleared = chatSessionService.clearUserSessions(userId);
        
        if (cleared) {
            logger.info("清空所有会话成功：userId={}", userId);
        } else {
            logger.warn("清空所有会话失败：userId={}", userId);
        }
        
        return ResponseEntity.ok(Map.of("success", cleared));
    }
    
    /**
     * 获取会话的所有消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getSessionMessages(@RequestHeader("Authorization") String token,
                                              @PathVariable String sessionId) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        List<ChatMessageDTO> messages = chatMessageService.getSessionMessages(sessionId, userId);
        if (messages == null) {
            return ResponseEntity.status(404).body("Session not found");
        }
        
        return ResponseEntity.ok(messages);
    }
    
    /**
     * 发送消息
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> sendMessage(@RequestHeader("Authorization") String token,
                                       @PathVariable String sessionId,
                                       @RequestBody Map<String, String> payload) {
        Long userId = getUserIdFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        String role = payload.getOrDefault("role", "user");
        String content = payload.get("content");
        
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Message content is required");
        }
        
        // 先保存用户消息
        ChatMessageDTO message = chatMessageService.addMessage(sessionId, userId, role, content);
        if (message == null) {
            return ResponseEntity.status(404).body("Session not found");
        }
        
        // 获取历史消息，为AI回复做准备
        List<ChatMessageDTO> historyMessages = chatMessageService.getSessionMessages(sessionId, userId);
        if (historyMessages == null || historyMessages.isEmpty()) {
            return ResponseEntity.ok(message);
        }
        
        // 记录历史消息的使用情况
        logger.info("会话 {} 获取到 {} 条历史消息，将用于AI回复", sessionId, historyMessages.size());
        
        return ResponseEntity.ok(message);
    }

    /**
     * 从授权头获取用户ID
     */
    private Long getUserIdFromToken(String bearerToken) {
        try {
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);
                return jwtUtil.getUserIdFromToken(token);
            }
            return null;
        } catch (Exception e) {
            logger.error("解析token异常: {}", e.getMessage());
            return null;
        }
    }
    /**
     * 根据入学年份计算年级信息
     * 
     * @param grade 入学年份，例如"2021"
     * @param major 专业名称
     * @return 年级信息字符串，例如"该同学为大二学生，网络工程专业，回答时需考虑用户身份"
     */
    private String getUserGradeInfo(String grade, String major) {
        if (grade == null || grade.trim().isEmpty()) {
            return "";
        }
        
        try {
            int enrollYear = Integer.parseInt(grade);
            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1; // 月份从0开始
            
            // 如果当前月份小于9月，学年未更新
            if (currentMonth < 9) {
                currentYear -= 1;
            }
            
            int yearDiff = currentYear - enrollYear + 1;
            String gradeName;
            
            switch (yearDiff) {
                case 1:
                    gradeName = "大一";
                    break;
                case 2:
                    gradeName = "大二";
                    break;
                case 3:
                    gradeName = "大三";
                    break;
                case 4:
                    gradeName = "大四";
                    break;
                default:
                    if (yearDiff <= 0) {
                        gradeName = "准大学生";
            } else {
                        gradeName = "毕业生";
                    }
            }
            
            major = (major != null && !major.trim().isEmpty()) ? major : "未知专业";
            
            return "用户为" + gradeName + "学生，" + major + "专业。"; // 修改文本格式，使其更适合作为系统提示词的一部分
        } catch (NumberFormatException e) {
            logger.warn("年级格式不正确: {}", grade);
            return "";
        }
    }

    @NotNull
    private Map<String, Object> getStringObjectMap(List<ChatMessageDTO> finalRecentMessages, String systemPrompt, String cleanToken) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessageDTO msg : finalRecentMessages) {
            // 确保每个消息的createTime不为null，避免排序出现空指针异常
            if (msg.getCreateTime() == null) {
                msg.setCreateTime(new Date());
            }
            
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("role", msg.getRole());
            messageMap.put("content", msg.getContent());
            messages.add(messageMap);
        }

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messages", messages);
        requestBody.put("max_length", 2000);
        requestBody.put("with_history", true);
        
        // 获取当前用户信息，添加到系统提示词中
        try {
            String username = jwtUtil.getUsernameFromToken(cleanToken);
            if (username != null) {
                User user = userService.findByUsername(username);
                if (user != null && user.getGrade() != null && user.getMajor() != null) {
                    String userInfo = getUserGradeInfo(user.getGrade(), user.getMajor());
                    if (!userInfo.isEmpty()) {
                        // 将用户信息添加到系统提示词
                        systemPrompt = userInfo + "\n\n" + systemPrompt;
                        logger.info("添加用户身份信息到系统提示词: {}", userInfo);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("获取用户信息失败: {}", e.getMessage());
        }
        
        requestBody.put("system_prompt", systemPrompt);
        
        // 明确添加最后一条用户消息作为当前问题，确保问题分析能够正确处理
        String currentUserQuestion = "";
        if (!finalRecentMessages.isEmpty()) {
            // 查找最后一条用户消息
            for (int i = finalRecentMessages.size() - 1; i >= 0; i--) {
                ChatMessageDTO msg = finalRecentMessages.get(i);
                if ("user".equals(msg.getRole())) {
                    currentUserQuestion = msg.getContent();
                    logger.info("将最后一条用户消息设置为当前问题: {}", 
                            currentUserQuestion.substring(0, Math.min(50, currentUserQuestion.length())) + 
                            (currentUserQuestion.length() > 50 ? "..." : ""));
                    break;
                }
            }
        }
        
        // 确保当前问题被正确添加到请求体中
        if (!currentUserQuestion.isEmpty()) {
            requestBody.put("text", currentUserQuestion);
            // 验证text字段是否被成功添加
            logger.info("当前问题已添加到请求体中，长度: {}", currentUserQuestion.length());
        } else {
            logger.warn("未找到用户消息，无法设置当前问题");
        }
        
        return requestBody;
    }

    /**
     * 判断是否应该发送书籍摘要
     * 
     * @param questionType 问题类型
     * @return 如果是图书查询或推荐类型，返回true
     */
    private boolean shouldSendBookSummary(String questionType) {
        return "BOOK_RECOMMEND".equals(questionType) || 
               "BOOK_SEARCH".equals(questionType);
    }

    /**
     * 处理并发送分块，如果识别到图书推荐请求，则调用相关服务
     */
    private void processAndSendChunk(String chunk, SseEmitter emitter, Set<String> bookNames, StringBuilder fullContent, String questionType) throws IOException {
        // 如果chunk包含SSE数据前缀，则开始处理
        if (chunk.startsWith("data:")) {
            try {
                // 分离数据部分
                String jsonStr = chunk.substring(5).trim();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(jsonStr);
                
                // 检查是否有内容类型和实际内容
                if (node.has("type") && node.has("data")) {
                    String type = node.get("type").asText();
                    String data = node.get("data").asText();
                    
                    // 根据类型处理不同的内容
                    switch (type) {
                        case "content":
                            // 检测书籍名称
                            Matcher matcher = BOOK_PATTERN.matcher(data);
                            while (matcher.find()) {
                                String bookName = matcher.group(1);
                                if (!bookName.isEmpty()) {
                                    bookNames.add(bookName);
                                    logger.info("从内容中检测到书籍名称: 《{}》", bookName);
                                }
                            }
                            
                            // 收集完整内容
                            fullContent.append(data);
                            break;
                            
                        case "think":
                            // 标记think块已处理，但不发送给客户端
                            hasSeenThink = true;
                            logger.debug("处理思考内容: {}", data);
                            return; // 不发送think块
                            
                        case "end":
                            // 处理结束标记
                            logger.info("收到结束标记");
                            break;
                    }
                } else {
                    // 对于没有type和data的JSON，尝试直接处理内容
                    logger.debug("处理不包含type和data的JSON数据: {}", jsonStr);
                    Matcher matcher = BOOK_PATTERN.matcher(jsonStr);
                    while (matcher.find()) {
                        String bookName = matcher.group(1);
                        if (!bookName.isEmpty()) {
                            bookNames.add(bookName);
                            logger.info("从非标准JSON中检测到书籍名称: 《{}》", bookName);
                        }
                    }
                }
                
                // 将原始块数据转发给客户端
                emitter.send(chunk, MediaType.TEXT_EVENT_STREAM);
                
        } catch (Exception e) {
                logger.error("解析SSE数据块失败: {}", e.getMessage());
                // 即使解析失败，也尝试原样发送数据
                emitter.send(chunk, MediaType.TEXT_EVENT_STREAM);
                
                // 即使JSON解析失败，也尝试从原始chunk中提取书籍名称
                try {
                    String rawContent = chunk.substring(5).trim();
                    Matcher matcher = BOOK_PATTERN.matcher(rawContent);
                    while (matcher.find()) {
                        String bookName = matcher.group(1);
                        if (!bookName.isEmpty()) {
                            bookNames.add(bookName);
                            logger.info("在JSON解析失败后从原始数据中检测到书籍名称: 《{}》", bookName);
                        }
                    }
                    
                    // 添加到完整内容
                    fullContent.append(rawContent);
                } catch (Exception ex) {
                    logger.error("从原始数据提取书籍名称失败: {}", ex.getMessage());
                }
            }
        } else {
            // 非SSE格式数据，原样发送
            emitter.send(chunk, MediaType.TEXT_EVENT_STREAM);
            
            // 尝试从非SSE格式数据中提取书籍名称
            Matcher matcher = BOOK_PATTERN.matcher(chunk);
            while (matcher.find()) {
                String bookName = matcher.group(1);
                if (!bookName.isEmpty()) {
                    bookNames.add(bookName);
                    logger.info("从非SSE格式数据中检测到书籍名称: 《{}》", bookName);
                }
            }
            
            // 添加到完整内容
            fullContent.append(chunk);
        }
    }
}