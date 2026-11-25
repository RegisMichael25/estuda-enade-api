package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeAlunoResponse;
import com.estudaenade.api.domain.enums.Encerramento;
import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.enums.TipoAtividade;
import com.estudaenade.api.domain.model.Atividade;
import com.estudaenade.api.domain.model.Tentativa;
import com.estudaenade.api.domain.model.Turma;
import com.estudaenade.api.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TentativaServiceTest {

    @Mock private AtividadeRepository atividadeRepository;
    @Mock private TentativaRepository tentativaRepository;
    @Mock private RespostaTentativaRepository respostaRepository;
    @Mock private ParticipacaoRepository participacaoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private DesempenhoService desempenhoService;

    @InjectMocks
    private TentativaService service;

    private UUID alunoId;
    private UUID turmaId;
    private UUID atividadeId;
    private Atividade atividade;

    @BeforeEach
    void preparar() {
        alunoId = UUID.randomUUID();
        turmaId = UUID.randomUUID();
        atividadeId = UUID.randomUUID();
        Turma turma = Turma.builder().id(turmaId).nome("Turma A").build();
        atividade = Atividade.builder()
                .id(atividadeId)
                .turma(turma)
                .titulo("Lista: Banco de Dados")
                .tipo(TipoAtividade.LISTA)
                .status(StatusAtividade.PUBLICADA)
                .build();

        when(atividadeRepository.findById(atividadeId)).thenReturn(Optional.of(atividade));
        when(participacaoRepository.existsByTurmaIdAndAlunoId(turmaId, alunoId)).thenReturn(true);
    }

    private Tentativa tentativaFinalizada(int numero, int acertos, Instant finalizadaEm) {
        return Tentativa.builder()
                .id(UUID.randomUUID())
                .atividade(atividade)
                .numero(numero)
                .iniciadaEm(finalizadaEm.minusSeconds(120))
                .finalizadaEm(finalizadaEm)
                .encerramento(Encerramento.ENVIO)
                .acertos(acertos)
                .total(5)
                .build();
    }

    @Test
    void melhorTentativaDesempataPelaMaisRecenteEntreAsMelhoresNotas() {
        Instant base = Instant.parse("2026-11-25T10:00:00Z");
        Tentativa t1 = tentativaFinalizada(1, 4, base.plusSeconds(60));
        Tentativa t2 = tentativaFinalizada(2, 4, base.plusSeconds(120));
        Tentativa t3 = tentativaFinalizada(3, 2, base.plusSeconds(180));

        when(tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId)).thenReturn(List.of(t3, t2, t1));

        AtividadeAlunoResponse resp = service.detalharAtividade(alunoId, atividadeId);

        assertEquals(4, resp.melhorAcertos());
        assertEquals(t2.getId(), resp.melhorTentativaId());
        assertTrue(resp.podeIniciar());
    }

    @Test
    void atividadeEncerradaComTentativasRetornaMelhorTentativaEPodeIniciarFalso() {
        atividade.setStatus(StatusAtividade.ENCERRADA);
        Instant base = Instant.parse("2026-11-25T10:00:00Z");
        Tentativa t1 = tentativaFinalizada(1, 3, base.plusSeconds(60));
        Tentativa t2 = tentativaFinalizada(2, 5, base.plusSeconds(120));

        when(tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId)).thenReturn(List.of(t2, t1));

        AtividadeAlunoResponse resp = service.detalharAtividade(alunoId, atividadeId);

        assertFalse(resp.podeIniciar());
        assertEquals(5, resp.melhorAcertos());
        assertEquals(t2.getId(), resp.melhorTentativaId());
    }

    @Test
    void atividadeEncerradaSemTentativasMantemMelhorTentativaNula() {
        atividade.setStatus(StatusAtividade.ENCERRADA);
        when(tentativaRepository.findByAlunoIdOrderByIniciadaEmDesc(alunoId)).thenReturn(List.of());

        AtividadeAlunoResponse resp = service.detalharAtividade(alunoId, atividadeId);

        assertFalse(resp.podeIniciar());
        assertNull(resp.melhorAcertos());
        assertNull(resp.melhorTentativaId());
        assertEquals(0, resp.tentativasFeitas());
    }
}
