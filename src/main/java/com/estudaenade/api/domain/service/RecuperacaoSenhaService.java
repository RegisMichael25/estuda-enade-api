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

@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CodigoAcessoService codigoAcessoService;
    private final SenhaService senhaService;

    /** Só envia com conta, mas registra o pedido sempre (o limite responde igual nos dois casos). */
    public void solicitarCodigo(String email) {
        String normalizado = normalizarEmail(email);
        codigoAcessoService.solicitar(normalizado, usuarioRepository.existsByEmail(normalizado));
    }

    @Transactional
    public void redefinir(String email, String codigo, String novaSenha) {
        String normalizado = normalizarEmail(email);
        senhaService.validarForca(novaSenha); // antes, para não gastar tentativa do código
        codigoAcessoService.conferir(normalizado, codigo);

        Usuario usuario = usuarioRepository.findByEmail(normalizado)
                .orElseThrow(() -> ApiException.naoAutorizado("CODIGO_INVALIDO", "Código inválido ou expirado."));

        senhaService.definir(usuario, novaSenha);
        usuario.setEmailVerificado(true);
        refreshTokenRepository.revogarTodosDoUsuario(usuario.getId());
        codigoAcessoService.consumir(normalizado);
        log.info("Senha da conta {} redefinida pelo código por e-mail", usuario.getId());
    }
}
