package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.ComentarioDtos.ComentarioResponse;
import com.estudaenade.api.api.dto.ComentarioDtos.MuralResponse;
import com.estudaenade.api.api.dto.ItemResponse;
import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.enums.StatusAtividade;
import com.estudaenade.api.domain.model.Atividade;
import com.estudaenade.api.domain.model.ComentarioAtividade;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.AtividadeRepository;
import com.estudaenade.api.domain.repository.ComentarioAtividadeRepository;
import com.estudaenade.api.domain.repository.ParticipacaoRepository;
import com.estudaenade.api.domain.repository.TentativaRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Mural de comentários de uma atividade, aberto à turma.
 * <ul>
 *   <li>Professor: só o da turma vê e comenta.</li>
 *   <li>Aluno: precisa estar na turma e ter enviado ao menos uma tentativa; antes disso o mural responde 404.</li>
 *   <li>O autor edita e apaga o que escreveu; o professor da turma apaga qualquer comentário.</li>
 *   <li>Turma arquivada: mural só para leitura (sem comentar nem editar); apagar continua permitido.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ComentarioService {

    private final ComentarioAtividadeRepository comentarioRepository;
    private final AtividadeRepository atividadeRepository;
    private final ParticipacaoRepository participacaoRepository;
    private final TentativaRepository tentativaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public MuralResponse mural(UsuarioLogado usuario, UUID atividadeId) {
        Atividade atividade = atividadeVisivel(usuario, atividadeId);
        return new MuralResponse(podeComentar(atividade),
                comentarioRepository.findByAtividadeIdOrderByCriadoEmAscIdAsc(atividade.getId()).stream()
                        .map(c -> resposta(c, usuario)).toList());
    }

    public ComentarioResponse comentar(UsuarioLogado usuario, UUID atividadeId, String texto) {
        Atividade atividade = atividadeVisivel(usuario, atividadeId);
        exigirComentavel(atividade);
        Usuario autor = usuarioRepository.findById(usuario.id())
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário não encontrado."));
        ComentarioAtividade comentario = comentarioRepository.save(ComentarioAtividade.builder()
                .atividade(atividade).autor(autor).texto(texto.trim()).build());
        return resposta(comentario, usuario);
    }

    public ComentarioResponse editar(UsuarioLogado usuario, UUID comentarioId, String texto) {
        ComentarioAtividade comentario = visivel(usuario, comentarioId);
        if (!ehAutor(comentario, usuario)) {
            throw ApiException.acessoNegado("Só o autor pode editar este comentário.");
        }
        TurmaService.exigirAtiva(comentario.getAtividade().getTurma());
        comentario.setTexto(texto.trim());
        comentario.setEditadoEm(Instant.now());
        return resposta(comentario, usuario);
    }

    public void apagar(UsuarioLogado usuario, UUID comentarioId) {
        ComentarioAtividade comentario = visivel(usuario, comentarioId);
        if (!podeApagar(comentario, usuario)) {
            throw ApiException.acessoNegado("Só o autor ou o professor da turma pode apagar este comentário.");
        }
        comentarioRepository.delete(comentario);
    }

    private ComentarioAtividade visivel(UsuarioLogado usuario, UUID comentarioId) {
        return comentarioRepository.findById(comentarioId)
                .filter(c -> podeVer(usuario, c.getAtividade()))
                .orElseThrow(() -> ApiException.naoEncontrado("Comentário não encontrado."));
    }

    private Atividade atividadeVisivel(UsuarioLogado usuario, UUID atividadeId) {
        return atividadeRepository.findById(atividadeId)
                .filter(a -> podeVer(usuario, a))
                .orElseThrow(() -> ApiException.naoEncontrado("Atividade não encontrada."));
    }

    private boolean podeVer(UsuarioLogado usuario, Atividade atividade) {
        if (usuario.papel() == Papel.PROFESSOR) {
            return ehProfessorDaTurma(usuario, atividade);
        }
        return atividade.getStatus() != StatusAtividade.RASCUNHO
                && participacaoRepository.existsByTurmaIdAndAlunoId(atividade.getTurma().getId(), usuario.id())
                && tentativaRepository.existsByAtividadeIdAndAlunoIdAndFinalizadaEmIsNotNull(atividade.getId(), usuario.id());
    }

    private void exigirComentavel(Atividade atividade) {
        TurmaService.exigirAtiva(atividade.getTurma());
        if (atividade.getStatus() == StatusAtividade.RASCUNHO) {
            throw ApiException.regra("ATIVIDADE_NAO_PUBLICADA", "Publique a atividade antes de abrir o mural.");
        }
    }

    private static boolean podeComentar(Atividade atividade) {
        return !atividade.getTurma().isArquivada() && atividade.getStatus() != StatusAtividade.RASCUNHO;
    }

    private static boolean ehProfessorDaTurma(UsuarioLogado usuario, Atividade atividade) {
        return usuario.papel() == Papel.PROFESSOR
                && atividade.getTurma().getProfessor().getId().equals(usuario.id());
    }

    private static boolean ehAutor(ComentarioAtividade comentario, UsuarioLogado usuario) {
        return comentario.getAutor().getId().equals(usuario.id());
    }

    private static boolean podeApagar(ComentarioAtividade comentario, UsuarioLogado usuario) {
        return ehAutor(comentario, usuario) || ehProfessorDaTurma(usuario, comentario.getAtividade());
    }

    private static ComentarioResponse resposta(ComentarioAtividade c, UsuarioLogado usuario) {
        Usuario autor = c.getAutor();
        boolean meu = ehAutor(c, usuario);
        return new ComentarioResponse(c.getId(), c.getTexto(), new ItemResponse(autor.getId(), autor.getNome()),
                autor.getPapel(), c.getCriadoEm(), c.getEditadoEm(),
                meu, meu && !c.getAtividade().getTurma().isArquivada(), podeApagar(c, usuario));
    }
}
