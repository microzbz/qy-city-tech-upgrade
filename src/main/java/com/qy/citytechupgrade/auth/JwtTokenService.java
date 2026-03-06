package com.qy.citytechupgrade.auth;

import com.qy.citytechupgrade.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtTokenService {
    private final AppProperties appProperties;

    public JwtTokenService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String createToken(Long userId, String username, String displayName, Long enterpriseId, List<String> roles) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(appProperties.getJwt().getExpireSeconds());

        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expireAt))
            .addClaims(Map.of(
                "username", username,
                "displayName", displayName,
                "enterpriseId", enterpriseId == null ? 0L : enterpriseId,
                "roles", roles
            ))
            .signWith(secretKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(secretKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private SecretKey secretKey() {
        byte[] bytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[Math.max(bytes.length, 32)];
        System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, padded.length));
        return Keys.hmacShaKeyFor(padded);
    }
}
