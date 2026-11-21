package com.estudaenade.api.domain.service;

import com.estudaenade.api.api.dto.CadastroProfessorDtos.ConviteCriadoResponse;
import com.estudaenade.api.api.dto.CadastroProfessorDtos.ConviteResponse;
import com.estudaenade.api.api.dto.CadastroProfessorDtos.ProfessorPendenteResponse;
import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.domain.model.ConviteProfessor;
import com.estudaenade.api.domain.model.Usuario;
import com.estudaenade.api.domain.repository.ConviteProfessorRepository;
import com.estudaenade.api.domain.repository.UsuarioRepository;
import com.estudaenade.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Quem pode atuar como professor. O papel dá acesso ao gabarito de todo o banco de questões, então
 * o cadastro passa por três camadas:
 * <ol>
 *   <li>convite de uso único, criado por um professor aprovado → entra aprovado;</li>
 *   <li>e-mail de um domínio institucional ({@code cadastro.professor.dominios-email}) → entra aprovado;</li>
 *   <li>nos demais casos a conta é criada pendente e só acessa as rotas do professor depois que um
 *       professor aprovado liberar.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CadastroProfessorService {

    // Sem I, O, 0 e 1: evita confusão ao digitar o código
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final ConviteProfessorRepository conviteRepository;

    @Value("${cadastro.professor.dominios-email:}")
    private List<String> dominiosInstitucionais;

    @Value("${cadastro.professor.convite-validade-dias:7}")
    private long validadeConviteDias;

    // ======================== cadastro ========================

    /**
     * Cria a conta do professor. Convite informado e inválido recusa o cadastro (em vez de criar a conta
     * pendente em silêncio), para a pessoa poder corrigir o código.
     */
    public Usuario cadastrar(String nome, String email, String codigoConvite) {
        Instant agora = Instant.now();
        ConviteProfessor convite = null;
        if (codigoConvite != null && !codigoConvite.isBlank()) {
            convite = conviteRepository.findByCodigoHash(hash(codigoConvite))
                    .filter(c -> c.disponivel(agora))
                    .orElseThrow(() -> ApiException.regra("CONVITE_INVALIDO",
                            "Convite inválido, expirado ou já utilizado. Peça outro a um professor."));
        }
        boolean aprovado = convite != null || dominioInstitucional(email);
        Usuario professor = usuarioRepository.save(Usuario.builder()
                .nome(nome).email(email).papel(Papel.PROFESSOR)
                .aprovado(aprovado)
                .aprovadoEm(aprovado ? agora : null)
                .aprovadoPor(convite == null ? null : convite.getCriadoPor())
                .build());
        if (convite != null) {
            convite.setUsadoPor(professor);
            convite.setUsadoEm(agora);
        }
        log.info("Professor {} cadastrado: {}", professor.getId(),
                convite != null ? "aprovado por convite" : aprovado ? "aprovado pelo domínio do e-mail" : "pendente de aprovação");
        return professor;
    }

    // ======================== aprovação ========================

    @Transactional(readOnly = true)
    public List<ProfessorPendenteResponse> pendentes() {
        return usuarioRepository.findByPapelAndAprovadoFalseOrderByCriadoEmAsc(Papel.PROFESSOR).stream()
                .map(ProfessorPendenteResponse::de).toList();
    }

    /** Libera um professor pendente. Ele precisa renovar a sessão para o acesso valer. Repetir não gera erro. */
    public ProfessorPendenteResponse aprovar(UUID aprovadorId, UUID professorId) {
        Usuario aprovador = aprovado(aprovadorId);
        Usuario professor = usuarioRepository.findById(professorId)
                .filter(u -> u.getPapel() == Papel.PROFESSOR)
                .orElseThrow(() -> ApiException.naoEncontrado("Professor não encontrado."));
        if (!professor.isAprovado()) {
            professor.setAprovado(true);
            professor.setAprovadoEm(Instant.now());
            professor.setAprovadoPor(aprovador);
            log.info("Professor {} aprovado por {}", professor.getId(), aprovador.getId());
        }
        return ProfessorPendenteResponse.de(professor);
    }

    // ======================== convites ========================

    public ConviteCriadoResponse criarConvite(UUID professorId) {
        Usuario criador = aprovado(professorId);
        String codigo = novoCodigo();
        ConviteProfessor convite = conviteRepository.save(ConviteProfessor.builder()
                .codigoHash(hash(codigo))
                .criadoPor(criador)
                .expiraEm(Instant.now().plus(Duration.ofDays(validadeConviteDias)))
                .build());
        return new ConviteCriadoResponse(convite.getId(), codigo, convite.getExpiraEm());
    }

    @Transactional(readOnly = true)
    public List<ConviteResponse> convites(UUID professorId) {
        return conviteRepository.findByCriadoPorIdOrderByCriadoEmDesc(professorId).stream()
                .map(ConviteResponse::de).toList();
    }

    // ======================== apoio ========================

    /** Confere no banco (e não só no token) que quem aprova ou convida é um professor aprovado. */
    private Usuario aprovado(UUID professorId) {
        return usuarioRepository.findById(professorId)
                .filter(u -> u.getPapel() == Papel.PROFESSOR && u.isAprovado())
                .orElseThrow(() -> ApiException.acessoNegado("Somente professores aprovados podem fazer isso."));
    }

    /** O domínio precisa ser exatamente um dos configurados (subdomínio ou sufixo parecido não vale). */
    private boolean dominioInstitucional(String email) {
        String dominio = email.substring(email.lastIndexOf('@') + 1);
        return dominiosInstitucionais.stream().map(String::trim).filter(d -> !d.isEmpty())
                .anyMatch(d -> d.equalsIgnoreCase(dominio));
    }

    /** Ex.: K7M2Q-9XWPA */
    private static String novoCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO + 1);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            if (i == TAMANHO_CODIGO / 2) sb.append('-');
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Aceita o código com ou sem hífen, em maiúsculas ou minúsculas. */
    private static String hash(String codigo) {
        return CodigoAcessoService.hash("convite-professor",
                codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""));
    }
}
