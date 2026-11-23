package com.estudaenade.api.domain.service;

import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.RefreshTokenRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.estudaenade.api.domain.service.SessaoService.normalizarEmail;

/** "Esqueci a senha": código por e-mail e definição da nova senha. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CodigoAcessoService codigoAcessoService;
    private final SenhaService senhaService;

    /** Responde igual com e sem conta para o e-mail; só envia o código quando há conta. */
    public void solicitarCodigo(String email) {
        String normalizado = normalizarEmail(email);
        codigoAcessoService.solicitar(normalizado, usuarioRepository.existsByEmail(normalizado));
    }

    /**
     * Confere o código e grava a nova senha. O código comprova o e-mail ({@code emailVerificado}).
     * Todas as sessões são encerradas; o usuário entra de novo com a nova senha.
     */
    @Transactional
    public void redefinir(String email, String codigo, String novaSenha) {
        String normalizado = normalizarEmail(email);
        senhaService.validarForca(novaSenha); // antes do código, para não gastar uma tentativa à toa
        codigoAcessoService.conferir(normalizado, codigo);

        // Há códigos registrados para e-mails sem conta (veja CodigoAcessoService.solicitar).
        Usuario usuario = usuarioRepository.findByEmail(normalizado)
                .orElseThrow(() -> ApiException.naoAutorizado("CODIGO_INVALIDO", "Código inválido ou expirado."));

        senhaService.definir(usuario, novaSenha);
        usuario.setEmailVerificado(true);
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        codigoAcessoService.consumir(normalizado);
        log.info("Senha da conta {} redefinida pelo código por e-mail", usuario.getId());
    }
}
