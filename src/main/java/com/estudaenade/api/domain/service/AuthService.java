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

/**
 * Login com e-mail e senha, sessão e dados da conta. O cadastro fica em {@link CadastroService}, o login com
 * Google em {@link LoginGoogleService} e o "esqueci a senha" em {@link RecuperacaoSenhaService}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SenhaService senhaService;
    private final SessaoService sessaoService;
    private final CodigoAcessoService codigoAcessoService;

    /** A conferência da senha (e o contador de falhas) roda em transação própria no {@link SenhaService}. */
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

    /** Envia ao e-mail da conta logada o código para trocar a senha (mesmo código e limite do "esqueci a senha"). */
    public void solicitarCodigoTrocaSenha(UUID usuarioId) {
        codigoAcessoService.solicitar(buscar(usuarioId).getEmail(), true);
    }

    /**
     * Cria ou troca a senha da conta logada com o código enviado por e-mail (vale também para conta sem senha).
     * Código errado responde 422 e não 401: o cliente trataria 401 como sessão expirada. O código comprova o e-mail
     * ({@code emailVerificado}). Todas as sessões são encerradas e uma nova é devolvida para este dispositivo.
     */
    @Transactional
    public SessaoResponse trocarSenha(UUID usuarioId, String codigo, String novaSenha) {
        Usuario usuario = buscar(usuarioId);
        senhaService.validarForca(novaSenha); // antes do código, para não gastar uma tentativa à toa
        try {
            codigoAcessoService.conferir(usuario.getEmail(), codigo);
        } catch (ApiException e) {
            throw ApiException.regra("CODIGO_INVALIDO", e.getMessage());
        }
        senhaService.definir(usuario, novaSenha);
        usuario.setEmailVerificado(true);
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        codigoAcessoService.consumir(usuario.getEmail());
        return sessaoService.abrir(buscar(usuarioId));
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
