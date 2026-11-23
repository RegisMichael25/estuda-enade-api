package com.estudaenade.api.infra.google;

import com.estudaenade.api.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Confere o ID token com as chaves públicas do Google (JWKS, guardadas em cache pelo Nimbus): assinatura,
 * validade, emissor ({@code iss}) e destinatário ({@code aud}, que precisa ser um dos client IDs configurados).
 * Sem client ID configurado ({@code GOOGLE_CLIENT_IDS} vazio), o login com Google fica desligado.
 */
@Slf4j
@Component
public class NimbusVerificadorTokenGoogle implements VerificadorTokenGoogle {

    static final String JWKS_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> EMISSORES = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    @Autowired
    public NimbusVerificadorTokenGoogle(@Value("${google.client-ids:}") List<String> clientIds) {
        this(clientIds, NimbusJwtDecoder.withJwkSetUri(JWKS_GOOGLE).build());
    }

    /** Usado nos testes, com um decoder de chave local no lugar do JWKS do Google. */
    NimbusVerificadorTokenGoogle(List<String> clientIds, NimbusJwtDecoder decoder) {
        List<String> ids = clientIds == null ? List.of()
                : clientIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (ids.isEmpty()) {
            log.info("GOOGLE_CLIENT_IDS não definido: login com Google desligado.");
            this.decoder = null;
            return;
        }
        decoder.setJwtValidator(validador(Set.copyOf(ids)));
        this.decoder = decoder;
    }

    private static OAuth2TokenValidator<Jwt> validador(Set<String> clientIds) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), // exp / nbf
                new JwtClaimValidator<Object>("iss", iss -> iss != null && EMISSORES.contains(iss.toString())),
                new JwtClaimValidator<Collection<?>>("aud",
                        aud -> aud != null && aud.stream().anyMatch(a -> clientIds.contains(a.toString()))));
    }

    @Override
    public DadosGoogle verificar(String idToken) {
        if (decoder == null) {
            throw ApiException.indisponivel("GOOGLE_DESABILITADO", "O login com Google não está disponível.");
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException e) {
            log.debug("ID token do Google recusado: {}", e.getMessage());
            throw invalido();
        } catch (JwtException e) {
            // Falha ao buscar as chaves do Google, por exemplo: nunca aceitar o token nesse caso.
            log.warn("Não foi possível validar o ID token do Google: {}", e.getMessage());
            throw ApiException.indisponivel("GOOGLE_DESABILITADO",
                    "Não foi possível confirmar a conta Google agora. Tente novamente em instantes.");
        }

        String email = jwt.getClaimAsString("email");
        boolean emailVerificado = Boolean.parseBoolean(String.valueOf(jwt.getClaims().get("email_verified")));
        if (jwt.getSubject() == null || email == null || email.isBlank() || !emailVerificado) {
            throw invalido();
        }
        return new DadosGoogle(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    private static ApiException invalido() {
        return ApiException.naoAutorizado("GOOGLE_TOKEN_INVALIDO",
                "Não foi possível confirmar sua conta Google. Tente entrar com Google de novo.");
    }
}
