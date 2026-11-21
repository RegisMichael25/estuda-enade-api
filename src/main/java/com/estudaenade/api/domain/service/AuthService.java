package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.contrato.dto.EntrarRequest;
import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.api.dto.AuthDtos.UsuarioResponse;
import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.model.RefreshToken;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.RefreshTokenRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CodigoAcessoService codigoAcessoService;
    private final CadastroProfessorService cadastroProfessorService;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshDias;

    public void solicitarCodigo(String email) {
        codigoAcessoService.solicitar(normalizar(email));
    }

    /**
     * Login pelo código recebido por e-mail. No primeiro acesso cria a conta — e, se faltarem nome e papel,
     * responde CADASTRO_NECESSARIO sem gastar o código, para o cliente reenviar com os dados.
     * O professor pode entrar pendente de aprovação (veja {@link CadastroProfessorService}).
     */
    @Transactional
    public SessaoResponse entrar(EntrarRequest req) {
        String email = normalizar(req.getEmail());
        codigoAcessoService.conferir(email, req.getCodigo());

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) {
            if (req.getNome() == null || req.getNome().isBlank() || req.getPapel() == null) {
                throw ApiException.regra("CADASTRO_NECESSARIO",
                        "Primeiro acesso: informe nome e papel (ALUNO ou PROFESSOR) para criar a conta.");
            }
            usuario = req.getPapel() == Papel.PROFESSOR
                    ? cadastroProfessorService.cadastrar(req.getNome().trim(), email, req.getCodigoConvite())
                    : usuarioRepository.save(Usuario.builder()
                            .nome(req.getNome().trim()).email(email).papel(Papel.ALUNO).aprovado(true).build());
        }
        codigoAcessoService.consumir(email);
        return abrirSessao(usuario);
    }

    /** Troca o refresh token por uma nova sessão. O token usado é revogado (rotação). */
    @Transactional
    public SessaoResponse renovar(String refreshToken) {
        RefreshToken atual = refreshTokenRepository.findByTokenHash(CodigoAcessoService.hash(refreshToken))
                .filter(t -> !t.isRevogado() && t.getExpiraEm().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.naoAutorizado("SESSAO_EXPIRADA", "Sessão expirada. Entre novamente."));
        atual.setRevogado(true);
        return abrirSessao(atual.getUsuario());
    }

    @Transactional
    public void sair(String refreshToken) {
        refreshTokenRepository.findByTokenHash(CodigoAcessoService.hash(refreshToken))
                .ifPresent(t -> t.setRevogado(true));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse eu(UUID usuarioId) {
        return UsuarioResponse.de(buscar(usuarioId));
    }

    @Transactional
    public UsuarioResponse atualizarNome(UUID usuarioId, String nome) {
        Usuario usuario = buscar(usuarioId);
        usuario.setNome(nome.trim());
        return UsuarioResponse.de(usuario);
    }

    private Usuario buscar(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> ApiException.naoAutorizado("SESSAO_EXPIRADA", "Conta não encontrada."));
    }

    private SessaoResponse abrirSessao(Usuario usuario) {
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

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
