package com.estudaenade.api.domain.service;

import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class SenhaService {

    static final int MINIMO_CARACTERES = 8;
    /** Limite do BCrypt: o que passar disso é ignorado. */
    static final int MAXIMO_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder encoder;
    /** Para o tempo de resposta não revelar se a conta existe. */
    private final String hashFicticio;

    @Value("${auth.login.max-falhas:5}")
    private int maxFalhas;

    @Value("${auth.login.bloqueio-minutos:15}")
    private int bloqueioMinutos;

    public SenhaService(UsuarioRepository usuarioRepository, PasswordEncoder encoder) {
        this.usuarioRepository = usuarioRepository;
        this.encoder = encoder;
        this.hashFicticio = encoder.encode("senha-ficticia-" + UUID.randomUUID());
    }

    public void validarForca(String senha) {
        boolean valida = senha != null
                && senha.codePointCount(0, senha.length()) >= MINIMO_CARACTERES
                && senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES
                && senha.codePoints().anyMatch(Character::isLetter)
                && senha.codePoints().anyMatch(Character::isDigit);
        if (!valida) {
            throw ApiException.regra("SENHA_FRACA",
                    "A senha precisa ter de 8 a 72 caracteres, com pelo menos uma letra e um número.");
        }
    }

    public void definir(Usuario usuario, String senha) {
        validarForca(senha);
        usuario.setSenhaHash(encoder.encode(senha));
        usuario.setSenhaAlteradaEm(Instant.now());
        usuario.setFalhasLogin(0);
        usuario.setBloqueadoAte(null);
    }

    /** Transação própria e sem rollback no erro: o contador de falhas precisa ser gravado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public UUID autenticar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null || usuario.getSenhaHash() == null) {
            encoder.matches(senha, hashFicticio);
            throw credenciaisInvalidas();
        }

        Instant agora = Instant.now();
        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(agora)) {
            throw muitasTentativas();
        }

        if (!encoder.matches(senha, usuario.getSenhaHash())) {
            usuario.setFalhasLogin(usuario.getFalhasLogin() + 1);
            if (usuario.getFalhasLogin() >= maxFalhas) {
                usuario.setFalhasLogin(0);
                usuario.setBloqueadoAte(agora.plus(Duration.ofMinutes(bloqueioMinutos)));
                log.warn("Login da conta {} bloqueado por {} min após {} senhas erradas",
                        usuario.getId(), bloqueioMinutos, maxFalhas);
                throw muitasTentativas();
            }
            throw credenciaisInvalidas();
        }

        usuario.setFalhasLogin(0);
        usuario.setBloqueadoAte(null);
        return usuario.getId();
    }

    private static ApiException credenciaisInvalidas() {
        return ApiException.naoAutorizado("CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos.");
    }

    private ApiException muitasTentativas() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MUITAS_TENTATIVAS",
                "Muitas tentativas com senha errada. Aguarde %d minutos ou redefina sua senha.".formatted(bloqueioMinutos));
    }
}
