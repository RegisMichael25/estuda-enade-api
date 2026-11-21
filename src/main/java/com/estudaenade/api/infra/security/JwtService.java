package com.estudaenade.api.infra.security;

import com.estudaenade.api.domain.enums.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey chave;
    private final Duration validadeAccess;

    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.access-expiration-minutes:30}") long minutos) {
        this.chave = Keys.hmacShaKeyFor(bytesDoSegredo(secret));
        this.validadeAccess = Duration.ofMinutes(minutos);
    }

    /**
     * O segredo vem de JWT_SECRET (ambiente ou .env) e nunca do repositório. Se não estiver definido,
     * usa um segredo aleatório que só existe na memória: a API funciona, mas os tokens emitidos antes
     * de reiniciar deixam de valer e os clientes renovam a sessão pelo refresh token.
     */
    private static byte[] bytesDoSegredo(String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] aleatorio = new byte[64];
            new SecureRandom().nextBytes(aleatorio);
            log.warn("JWT_SECRET não definido: usando um segredo aleatório temporário. "
                    + "As sessões abertas serão renovadas a cada reinício; defina JWT_SECRET no .env para mantê-las.");
            return aleatorio;
        }
        return secret.trim().getBytes(StandardCharsets.UTF_8);
    }

    public String gerarAccessToken(UsuarioLogado usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.id().toString())
                .claim("papel", usuario.papel().name())
                .claim("aprovado", usuario.aprovado())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validadeAccess)))
                .signWith(chave)
                .compact();
    }

    /** Usuário do token, ou vazio se o token for inválido ou estiver expirado. */
    public Optional<UsuarioLogado> ler(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            return Optional.of(new UsuarioLogado(
                    UUID.fromString(claims.getSubject()),
                    Papel.valueOf(claims.get("papel", String.class)),
                    Boolean.TRUE.equals(claims.get("aprovado", Boolean.class))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public long validadeAccessSegundos() {
        return validadeAccess.toSeconds();
    }
}
