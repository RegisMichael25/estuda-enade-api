package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.api.dto.AuthDtos.UsuarioResponse;
import com.estudaenade.api.domain.model.RefreshToken;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.RefreshTokenRepository;
import com.estudaenade.api.infra.security.JwtService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SessaoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshDias;

    @Transactional
    public SessaoResponse abrir(Usuario usuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(CodigoAcessoService.hash(refresh))
                .expiraEm(Instant.now().plus(Duration.ofDays(refreshDias)))
                .build());

        String access = jwtService.gerarAccessToken(
                new UsuarioLogado(usuario.getId(), usuario.getPapel(), usuario.isAprovado()));
        return new SessaoResponse(access, refresh, jwtService.validadeAccessSegundos(), UsuarioResponse.de(usuario));
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
