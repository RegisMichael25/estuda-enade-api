package com.estudaenade.api.infra.google;

import com.estudaenade.api.exception.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validação real do ID token, com uma chave RSA local no lugar das do Google. */
class NimbusVerificadorTokenGoogleTest {

    private static final String CLIENT_ID = "app-web.apps.googleusercontent.com";
    private static final KeyPair CHAVES = gerarChaves();
    private static final KeyPair OUTRAS_CHAVES = gerarChaves();

    private final NimbusVerificadorTokenGoogle verificador = new NimbusVerificadorTokenGoogle(
            List.of(" outro-app ", CLIENT_ID),
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVES.getPublic()).build());

    @Test
    void tokenValidoDevolveOsDadosDaConta() {
        VerificadorTokenGoogle.DadosGoogle dados = verificador.verificar(token(c -> { }));
        assertThat(dados.sub()).isEqualTo("sub-123");
        assertThat(dados.email()).isEqualTo("ana@gmail.test");
        assertThat(dados.nome()).isEqualTo("Ana Souza");
    }

    @Test
    void aceitaOsDoisFormatosDeEmissorDoGoogle() {
        assertThat(verificador.verificar(token(c -> c.issuer("accounts.google.com"))).sub()).isEqualTo("sub-123");
    }

    @Test
    void recusaTokenDeOutroAplicativo() {
        assertInvalido(token(c -> c.audience("app-de-outra-pessoa")));
    }

    @Test
    void recusaOutroEmissor() {
        assertInvalido(token(c -> c.issuer("https://emissor-falso.test")));
    }

    @Test
    void recusaTokenExpirado() {
        assertInvalido(token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600)))));
    }

    @Test
    void recusaEmailNaoVerificadoOuAusente() {
        assertInvalido(token(c -> c.claim("email_verified", false)));
        assertInvalido(token(c -> c.claim("email", null)));
    }

    @Test
    void recusaAssinaturaDeOutraChaveETextoQualquer() {
        assertInvalido(assinar(claims(c -> { }), OUTRAS_CHAVES));
        assertInvalido("nao-e-um-jwt");
    }

    @Test
    void semClientIdConfiguradoOLoginComGoogleFicaDesligado() {
        NimbusVerificadorTokenGoogle desligado = new NimbusVerificadorTokenGoogle(List.of(" ", ""),
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVES.getPublic()).build());
        assertThatThrownBy(() -> desligado.verificar(token(c -> { })))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(503);
                    assertThat(e.getCodigo()).isEqualTo("GOOGLE_DESABILITADO");
                });
    }

    // ======================== apoio ========================

    private void assertInvalido(String idToken) {
        assertThatThrownBy(() -> verificador.verificar(idToken))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(401);
                    assertThat(e.getCodigo()).isEqualTo("GOOGLE_TOKEN_INVALIDO");
                });
    }

    private static String token(Consumer<JWTClaimsSet.Builder> ajuste) {
        return assinar(claims(ajuste), CHAVES);
    }

    private static JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> ajuste) {
        Instant agora = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("sub-123")
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(agora.plusSeconds(3600)))
                .claim("email", "ana@gmail.test")
                .claim("email_verified", true)
                .claim("name", "Ana Souza");
        ajuste.accept(b);
        return b.build();
    }

    private static String assinar(JWTClaimsSet claims, KeyPair chaves) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(chaves.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair gerarChaves() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            return gerador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
