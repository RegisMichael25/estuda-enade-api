package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.AlunoApi;
import com.estudaenade.api.api.contrato.dto.ComentarioRequest;
import com.estudaenade.api.api.dto.ComentarioDtos.ComentarioResponse;
import com.estudaenade.api.api.dto.ComentarioDtos.MuralResponse;
import com.estudaenade.api.domain.service.ComentarioService;
import com.estudaenade.api.api.contrato.dto.CriarProvaPessoalRequest;
import com.estudaenade.api.api.contrato.dto.EntrarTurmaRequest;
import com.estudaenade.api.api.contrato.dto.ResponderRequest;
import com.estudaenade.api.api.contrato.dto.SinalizacaoRequest;
import com.estudaenade.api.api.contrato.dto.SortearPraticaRequest;
import com.estudaenade.api.api.dto.AtividadeDtos.AtividadeAlunoResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.ResultadoTentativaResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.TentativaEstadoResponse;
import com.estudaenade.api.api.dto.AtividadeDtos.TentativaResumoResponse;
import com.estudaenade.api.api.dto.DesempenhoDtos.DesempenhoAlunoResponse;
import com.estudaenade.api.api.dto.Pagina;
import com.estudaenade.api.api.dto.ProvaPessoalDtos.ProvaPessoalResponse;
import com.estudaenade.api.api.dto.ProvaPessoalDtos.ProvaPessoalResumoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.ContagemTemaResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.GabaritoResponse;
import com.estudaenade.api.api.dto.QuestaoDtos.QuestaoAlunoResponse;
import com.estudaenade.api.api.dto.TurmaDtos.TurmaAlunoResponse;
import com.estudaenade.api.domain.enums.HistoricoPratica;
import com.estudaenade.api.domain.service.DesempenhoService;
import com.estudaenade.api.domain.service.PraticaService;
import com.estudaenade.api.domain.service.ProvaPessoalService;
import com.estudaenade.api.domain.service.QuestaoService;
import com.estudaenade.api.domain.service.TentativaService;
import com.estudaenade.api.domain.service.TurmaService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tudo o que o aluno usa (app e versão web): turmas, atividades, tentativas, prática livre e desempenho.
 * Rotas em {@link AlunoApi}.
 */
@RestController
@RequiredArgsConstructor
public class AlunoController implements AlunoApi {

    private final TurmaService turmaService;
    private final ComentarioService comentarioService;
    private final TentativaService tentativaService;
    private final QuestaoService questaoService;
    private final PraticaService praticaService;
    private final ProvaPessoalService provaPessoalService;
    private final DesempenhoService desempenhoService;

    // ---------- turmas ----------

    @Override
    public TurmaAlunoResponse entrarNaTurma(EntrarTurmaRequest req) {
        return turmaService.entrar(aluno(), req.getCodigo());
    }

    @Override
    public List<TurmaAlunoResponse> minhasTurmas() {
        return turmaService.listarDoAluno(aluno());
    }

    @Override
    public void sairDaTurma(UUID turmaId) {
        turmaService.sair(aluno(), turmaId);
    }

    // ---------- atividades e tentativas ----------

    @Override
    public List<AtividadeAlunoResponse> minhasAtividades() {
        return tentativaService.listarAtividades(aluno());
    }

    @Override
    public AtividadeAlunoResponse minhaAtividade(UUID atividadeId) {
        return tentativaService.detalharAtividade(aluno(), atividadeId);
    }

    /** Inicia a tentativa (o cronômetro começa aqui) ou retoma a que estiver em andamento. */
    @Override
    public TentativaEstadoResponse iniciarTentativa(UUID atividadeId) {
        return tentativaService.iniciar(aluno(), atividadeId);
    }

    @Override
    public List<TentativaResumoResponse> historicoDeTentativas() {
        return tentativaService.historico(aluno());
    }

    @Override
    public TentativaEstadoResponse estadoDaTentativa(UUID tentativaId) {
        return tentativaService.estado(aluno(), tentativaId);
    }

    /** Marca a alternativa de uma questão. {@code alternativaId} nulo deixa a questão em branco. */
    @Override
    public void responderNaTentativa(UUID tentativaId, UUID questaoId, ResponderRequest req) {
        tentativaService.responder(aluno(), tentativaId, questaoId, req.getAlternativaId());
    }

    @Override
    public ResultadoTentativaResponse enviarTentativa(UUID tentativaId) {
        return tentativaService.enviar(aluno(), tentativaId);
    }

    @Override
    public ResultadoTentativaResponse resultadoDaTentativa(UUID tentativaId) {
        return tentativaService.resultado(aluno(), tentativaId);
    }

    // ---------- prática livre ----------

    @Override
    public List<Integer> anosDaPratica(UUID cursoId) {
        return questaoService.anosDisponiveis(cursoId);
    }

    /** Questões publicadas, sem gabarito e sem as reservadas para atividades ainda não concluídas. */
    @Override
    public Pagina<QuestaoAlunoResponse> questoesDaPratica(UUID cursoId, List<UUID> temaId, List<Integer> ano,
                                                          HistoricoPratica historico, String busca,
                                                          Integer pagina, Integer tamanho) {
        return questaoService.listarParaPratica(aluno(), cursoId, temaId, ano, historico, busca, pagina, tamanho);
    }

    /** Quantas questões há em cada tema do curso, para o aluno montar a própria prova. */
    @Override
    public List<ContagemTemaResponse> contagemDaPratica(UUID cursoId, List<Integer> ano, HistoricoPratica historico) {
        return praticaService.contagem(aluno(), cursoId, ano, historico);
    }

    /** Sorteia questões para a prova montada pelo aluno. Nada disso vai para a turma. */
    @Override
    public List<QuestaoAlunoResponse> sortearPratica(SortearPraticaRequest req) {
        return questaoService.sortearParaPratica(aluno(), req.getCursoId(), req.getTemaIds(), req.getAnos(),
                req.getHistorico(), Boolean.TRUE.equals(req.getEquilibrar()), req.getQuantidade(), req.getExcluirIds());
    }

    /** Responde e recebe gabarito e explicação na hora. */
    @Override
    public GabaritoResponse responderNaPratica(UUID questaoId, ResponderRequest req) {
        return praticaService.responder(aluno(), questaoId, req.getAlternativaId());
    }

    // ---------- provas montadas pelo aluno (só dele, fora da turma) ----------

    @Override
    public List<ProvaPessoalResumoResponse> minhasProvas() {
        return provaPessoalService.listar(aluno());
    }

    @Override
    public ProvaPessoalResponse criarProva(CriarProvaPessoalRequest req) {
        return provaPessoalService.criar(aluno(), req.getQuestaoIds(), req.getNome());
    }

    @Override
    public ProvaPessoalResponse detalharProva(UUID provaId) {
        return provaPessoalService.detalhar(aluno(), provaId);
    }

    @Override
    public void excluirProva(UUID provaId) {
        provaPessoalService.excluir(aluno(), provaId);
    }

    @Override
    public GabaritoResponse responderNaProva(UUID provaId, UUID questaoId, ResponderRequest req) {
        return provaPessoalService.responder(aluno(), provaId, questaoId, req.getAlternativaId());
    }

    @Override
    public ProvaPessoalResponse concluirProva(UUID provaId) {
        return provaPessoalService.concluir(aluno(), provaId);
    }

    @Override
    public ProvaPessoalResponse refazerProva(UUID provaId) {
        return provaPessoalService.refazer(aluno(), provaId);
    }

    @Override
    public ProvaPessoalResponse provaComErradas(UUID provaId) {
        return provaPessoalService.comErradas(aluno(), provaId);
    }

    @Override
    public void sinalizarQuestaoComoAluno(UUID questaoId, SinalizacaoRequest req) {
        questaoService.sinalizar(UsuarioLogado.atual(), questaoId, req == null ? null : req.getMotivo());
    }

    // ---------- desempenho ----------

    @Override
    public DesempenhoAlunoResponse meuDesempenho() {
        return desempenhoService.doAluno(aluno());
    }

    // ---------- mural de comentários ----------

    @Override
    public MuralResponse muralDaAtividadeComoAluno(UUID atividadeId) {
        return comentarioService.mural(UsuarioLogado.atual(), atividadeId);
    }

    @Override
    public ComentarioResponse comentarNaAtividadeComoAluno(UUID atividadeId, ComentarioRequest req) {
        return comentarioService.comentar(UsuarioLogado.atual(), atividadeId, req.getTexto());
    }

    @Override
    public ComentarioResponse editarComentarioComoAluno(UUID comentarioId, ComentarioRequest req) {
        return comentarioService.editar(UsuarioLogado.atual(), comentarioId, req.getTexto());
    }

    @Override
    public void apagarComentarioComoAluno(UUID comentarioId) {
        comentarioService.apagar(UsuarioLogado.atual(), comentarioId);
    }

    private static UUID aluno() {
        return UsuarioLogado.atual().id();
    }
}
