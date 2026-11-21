package com.estudaenade.api.domain.service;

import com.estudaenade.api.domain.model.CodigoAcesso;
import com.estudaenade.api.domain.repository.CodigoAcessoRepository;
import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.codigo.EnviadorCodigo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Códigos de uso único enviados por e-mail (login sem senha). */
@Service
@RequiredArgsConstructor
public class CodigoAcessoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodigoAcessoRepository repository;
    private final EnviadorCodigo enviador;

    @Value("${otp.expiration-minutes:10}")
    private int expiracaoMinutos;

    @Value("${otp.rate-limit-max-attempts:3}")
    private int maxSolicitacoes;

    @Value("${otp.rate-limit-window-minutes:10}")
    private int janelaMinutos;

    @Value("${otp.max-tentativas-verificacao:5}")
    private int maxTentativasVerificacao;

    // Quando preenchido, todo código gerado é este. Vazio = código aleatório.
    @Value("${otp.codigo-fixo:}")
    private String codigoFixo;

    @Transactional
    public void solicitar(String email) {
        Instant agora = Instant.now();
        if (repository.countByEmailAndCriadoEmAfter(email, agora.minus(Duration.ofMinutes(janelaMinutos))) >= maxSolicitacoes) {
            throw ApiException.muitasSolicitacoes("Muitos códigos solicitados. Aguarde alguns minutos e tente de novo.");
        }
        repository.invalidarPendentes(email);

        String codigo = gerarCodigo();
        repository.save(CodigoAcesso.builder()
                .email(email)
                .codigoHash(hash(email, codigo))
                .expiraEm(agora.plus(Duration.ofMinutes(expiracaoMinutos)))
                .criadoEm(agora)
                .build());
        enviador.enviar(email, codigo, expiracaoMinutos);
    }

    /**
     * Confere o código sem consumi-lo. Roda em transação própria e não faz rollback no erro:
     * o incremento de tentativas precisa ser gravado mesmo quando o código está errado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void conferir(String email, String codigo) {
        CodigoAcesso atual = repository
                .findFirstByEmailAndUsadoFalseAndExpiraEmAfterOrderByCriadoEmDesc(email, Instant.now())
                .orElseThrow(CodigoAcessoService::invalido);

        boolean confere = MessageDigest.isEqual(
                atual.getCodigoHash().getBytes(StandardCharsets.UTF_8),
                hash(email, codigo == null ? "" : codigo.trim()).getBytes(StandardCharsets.UTF_8));
        if (!confere) {
            atual.setTentativas(atual.getTentativas() + 1);
            if (atual.getTentativas() >= maxTentativasVerificacao) {
                atual.setUsado(true); // código queimado: é preciso solicitar outro
            }
            throw invalido();
        }
    }

    /** Depois de um login concluído, o código não pode ser reutilizado. */
    @Transactional
    public void consumir(String email) {
        repository.invalidarPendentes(email);
    }

    private static ApiException invalido() {
        return ApiException.naoAutorizado("CODIGO_INVALIDO", "Código inválido ou expirado.");
    }

    private String gerarCodigo() {
        if (codigoFixo != null && !codigoFixo.isBlank()) {
            return codigoFixo.trim();
        }
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    static String hash(String... partes) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(String.join(":", partes).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
