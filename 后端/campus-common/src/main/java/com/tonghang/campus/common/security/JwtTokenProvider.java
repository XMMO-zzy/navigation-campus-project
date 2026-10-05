package com.tonghang.campus.common.security;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析。
 *
 * <p>来源：《13-API接口文档》§1「鉴权：Authorization: Bearer &lt;accessToken&gt;」；
 * accessToken 2h / refreshToken 7d，见《11》§3.1 与 application.yml。
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_USER_TYPE = "ut";
    public static final String CLAIM_VERIFY = "vs";
    public static final String CLAIM_TYPE = "typ";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtTokenProvider(
            @Value("${campus.jwt.secret}") String secret,
            @Value("${campus.jwt.access-token-ttl:7200}") long accessTtlSeconds,
            @Value("${campus.jwt.refresh-token-ttl:604800}") long refreshTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    /** 签发 accessToken */
    public String createAccessToken(Long userId, Integer userType, Integer verifyStatus) {
        return create(userId, userType, verifyStatus, TYPE_ACCESS, accessTtlSeconds);
    }

    /** 签发 refreshToken */
    public String createRefreshToken(Long userId, Integer userType, Integer verifyStatus) {
        return create(userId, userType, verifyStatus, TYPE_REFRESH, refreshTtlSeconds);
    }

    private String create(Long userId, Integer userType, Integer verifyStatus, String type, long ttlSeconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000L);
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_UID, userId);
        claims.put(CLAIM_USER_TYPE, userType);
        claims.put(CLAIM_VERIFY, verifyStatus);
        claims.put(CLAIM_TYPE, type);
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token。
     *
     * @throws BizException 40002 Token 过期；40001 Token 无效
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    claims.get(CLAIM_UID, Number.class).longValue(),
                    claims.get(CLAIM_USER_TYPE, Number.class).intValue(),
                    claims.get(CLAIM_VERIFY, Number.class).intValue(),
                    claims.get(CLAIM_TYPE, String.class));
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
