package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AuthDtos.SessaoResponse;
import com.estudaenade.api.api.dto.AuthDtos.UsuarioResponse;
import com.estudaenade.api.domain.model.RefreshToken;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.RefreshTokenRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.estudaenade.api.domain.service.SessaoService.normalizarEmail;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SenhaService senhaService;
    private final SessaoService sessaoService;
    private final CodigoAcessoService codigoAcessoService;

    @Transactional
    public SessaoResponse entrar(String email, String senha) {
        UUID id = senhaService.autenticar(normalizarEmail(email), senha);
        return sessaoService.abrir(buscar(id));
    }

    /** Troca o refresh token por uma nova sessão. O token usado é revogado (rotação). */
    @Transactional
    public SessaoResponse renovar(String refreshToken) {
        RefreshToken atual = refreshTokenRepository.findByTokenHash(CodigoAcessoService.hash(refreshToken))
                .filter(t -> !t.isRevogado() && t.getExpiraEm().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.naoAutorizado("SESSAO_EXPIRADA", "Sessão expirada. Entre novamente."));
        atual.setRevogado(true);
        return sessaoService.abrir(atual.getUsuario());
    }

    @Transactional
    public void sair(String refreshToken) {
        refreshTokenRepository.findByTokenHash(CodigoAcessoService.hash(refreshToken))
                .ifPresent(t -> t.setRevogado(true));
    }

    public void solicitarCodigoTrocaSenha(UUID usuarioId) {
        codigoAcessoService.solicitar(buscar(usuarioId).getEmail(), true);
    }

    /** Código errado vira 422, não 401: o cliente trataria 401 como sessão expirada. */
    @Transactional
    public SessaoResponse trocarSenha(UUID usuarioId, String codigo, String novaSenha) {
        Usuario usuario = buscar(usuarioId);
        senhaService.validarForca(novaSenha); // antes, para não gastar tentativa do código
        try {
            codigoAcessoService.conferir(usuario.getEmail(), codigo);
        } catch (ApiException e) {
            throw ApiException.regra("CODIGO_INVALIDO", e.getMessage());
        }
        senhaService.definir(usuario, novaSenha);
        usuario.setEmailVerificado(true);
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        codigoAcessoService.consumir(usuario.getEmail());
        return sessaoService.abrir(buscar(usuarioId)); // consumir() limpa o contexto: recarrega o usuário
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
}
