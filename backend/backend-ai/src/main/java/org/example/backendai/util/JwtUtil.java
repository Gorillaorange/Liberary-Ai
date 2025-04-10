package org.example.backendai.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // 从配置文件中读取JWT密钥
    @Value("${jwt.secret}")
    private String secretString;

    // 延迟初始化密钥 - 将在第一次使用时创建
    private SecretKey secretKey;

    @Value("${jwt.expiration:86400000}")
    private Long expiration; // 默认24小时过期时间（毫秒）

    /**
     * 初始化密钥 - 确保至少256位
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            try {
                // 方法1: 尝试从配置的密钥派生一个安全密钥
                // 如果密钥已经够长且有足够的熵，可以使用
                if (secretString.length() >= 32) { // 至少32字节 = 256位
                    byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
                    secretKey = Keys.hmacShaKeyFor(keyBytes);
                } else {
                    // 方法2: 如果配置的密钥不够长，使用安全随机数生成器生成密钥
                    // 为HS512算法生成强安全密钥 (512位)
                    secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
                    
                    // 输出生成的密钥供配置文件使用（仅首次启动时有用）
                    byte[] encodedKey = secretKey.getEncoded();
                    String base64Key = Base64.getEncoder().encodeToString(encodedKey);
                    System.out.println("==================================");
                    System.out.println("生成了新的安全JWT密钥，请更新application.properties");
                    System.out.println("jwt.secret=" + base64Key);
                    System.out.println("==================================");
                }
            } catch (Exception e) {
                // 如果以上方法都失败，使用安全的默认值
                secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
                System.out.println("使用默认安全密钥: " + e.getMessage());
            }
        }
        return secretKey;
    }

    /**
     * 从令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从令牌中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                logger.error("令牌为空");
                return null;
            }
            
            // 如果token带有Bearer前缀，则去除
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
            
            // 检查token是否含有空格，如果有则返回null
            if (token.contains(" ")) {
                logger.error("令牌包含不合法的空格字符");
                return null;
            }
            
            Claims claims = getAllClaimsFromToken(token);
            // 从claims中获取userId，可以是字符串或者数字
            Object userIdObj = claims.get("userId");
            
            if (userIdObj != null) {
                if (userIdObj instanceof Integer) {
                    return ((Integer) userIdObj).longValue();
                } else if (userIdObj instanceof Long) {
                    return (Long) userIdObj;
                } else if (userIdObj instanceof String) {
                    try {
                        return Long.parseLong((String) userIdObj);
                    } catch (NumberFormatException e) {
                        logger.error("无法解析userId字符串: {}", userIdObj);
                        return null;
                    }
                }
            }
            logger.warn("Token中不包含userId或格式不正确");
            return null;
        } catch (Exception e) {
            logger.error("解析JWT token失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中获取过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从令牌中获取指定的声明
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析令牌获取所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (io.jsonwebtoken.security.SecurityException e) {
            logger.error("JWT签名验证失败: {}", e.getMessage());
            throw e;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            logger.error("JWT格式不正确: {}", e.getMessage());
            throw e;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.error("JWT已过期: {}", e.getMessage());
            throw e;
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            logger.error("不支持的JWT: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.error("JWT参数无效: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("解析JWT时发生未知错误: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 检查令牌是否过期
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 从令牌中获取用户角色
     */
    public String getRoleFromToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                logger.error("令牌为空");
                return null;
            }
            
            // 如果token带有Bearer前缀，则去除
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
            
            Claims claims = getAllClaimsFromToken(token);
            // 从claims中获取role
            Object roleObj = claims.get("role");
            
            if (roleObj != null) {
                return roleObj.toString();
            }
            
            logger.warn("Token中不包含role或格式不正确");
            return null;
        } catch (Exception e) {
            logger.error("从JWT token中获取角色失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 为指定用户和ID生成令牌
     */
    public String generateToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        logger.info("生成Token时设置userId: {}", userId);
        return doGenerateToken(claims, username);
    }
    
    /**
     * 为指定用户、ID和角色生成令牌
     */
    public String generateToken(String username, Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        logger.info("生成Token时设置userId: {}, role: {}", userId, role);
        return doGenerateToken(claims, username);
    }

    /**
     * 为指定用户生成令牌
     */
    public String generateToken(String username) {
        // 不要使用这个方法，因为它没有设置userId
        logger.warn("警告：使用没有userId的generateToken方法");
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, username);
    }

    /**
     * 生成令牌的核心方法
     */
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        final Date createdDate = new Date();
        final Date expirationDate = new Date(createdDate.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(createdDate)
                .setExpiration(expirationDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 验证令牌
     */
    public Boolean validateToken(String token, String username) {
        final String tokenUsername = getUsernameFromToken(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * 调试方法：解析token并输出完整信息
     */
    public void debugToken(String token) {
        try {
            logger.debug("===== DEBUG TOKEN =====");
            
            Claims claims = getAllClaimsFromToken(token);
            logger.debug("Subject: {}", claims.getSubject());
            logger.debug("IssuedAt: {}", claims.getIssuedAt());
            logger.debug("Expiration: {}", claims.getExpiration());
            
            Object userIdObj = claims.get("userId");
            if (userIdObj == null) {
                logger.warn("未找到userId声明");
            } else {
                logger.debug("UserId类型: {}, 值: {}", userIdObj.getClass().getName(), userIdObj);
            }
        } catch (Exception e) {
            logger.error("解析Token异常: {}", e.getMessage());
        }
    }
} 