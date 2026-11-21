package com.estudaenade.api.infra;

import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.infra.security.JwtService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O segredo do JWT não tem valor no repositório: sem JWT_SECRET, cada subida usa um segredo aleatório. */
class JwtServiceTest {

    private static final UsuarioLogado PROFESSOR = new UsuarioLogado(UUID.randomUUID(), Papel.PROFESSOR, true);

    @Test
    void semSegredoConfiguradoGeraUmAleatorioQueSoValeNaquelaInstancia() {
        JwtService umaSubida = new JwtService("", 30);
        JwtService outraSubida = new JwtService("  ", 30);

        String token = umaSubida.gerarAccessToken(PROFESSOR);

        assertEquals(PROFESSOR, umaSubida.ler(token).orElseThrow());
        assertTrue(outraSubida.ler(token).isEmpty(), "token de outra subida não pode ser aceito");
    }

    @Test
    void comSegredoConfiguradoOTokenValeEntreSubidasEOutroSegredoNaoAbreNada() {
        String segredo = "um-segredo-definido-no-env-com-mais-de-32-caracteres";
        String token = new JwtService(segredo, 30).gerarAccessToken(PROFESSOR);

        assertEquals(PROFESSOR, new JwtService(segredo, 30).ler(token).orElseThrow());

        // token assinado com um segredo diferente do configurado
        String forjado = new JwtService("outro-segredo-qualquer-com-mais-de-32-caracteres", 30).gerarAccessToken(PROFESSOR);
        assertTrue(new JwtService(segredo, 30).ler(forjado).isEmpty());
        assertTrue(new JwtService("", 30).ler(forjado).isEmpty());
    }

    @Test
    void tokenSemAMarcaDeAprovadoETratadoComoPendente() {
        JwtService jwt = new JwtService("", 30);
        UsuarioLogado pendente = jwt.ler(jwt.gerarAccessToken(new UsuarioLogado(UUID.randomUUID(), Papel.PROFESSOR, false))).orElseThrow();
        assertEquals("ROLE_PROFESSOR_PENDENTE", pendente.autoridade());
        assertEquals("ROLE_PROFESSOR", PROFESSOR.autoridade());
        assertEquals("ROLE_ALUNO", new UsuarioLogado(UUID.randomUUID(), Papel.ALUNO, true).autoridade());
    }
}
