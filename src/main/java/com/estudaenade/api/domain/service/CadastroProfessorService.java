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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CadastroProfessorService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final ConviteProfessorRepository conviteRepository;

    @Value("${cadastro.professor.convite-validade-dias:7}")
    private long validadeConviteDias;

    @Value("${cadastro.professor.convite-cooldown-minutos:5}")
    private long cooldownConviteMinutos;

    // ======================== cadastro ========================

    /**
     * Grava o professor já montado pelo cadastro (nome, e-mail, senha, Google). Entra aprovado só com convite válido;
     * sem convite fica pendente até um professor aprovado liberar. O domínio do e-mail não aprova ninguém: alunos
     * também têm e-mail institucional.
     */
    public Usuario cadastrar(Usuario professor, String codigoConvite) {
        Instant agora = Instant.now();
        ConviteProfessor convite = null;
        if (codigoConvite != null && !codigoConvite.isBlank()) {
            convite = conviteRepository.findByCodigoHash(hash(codigoConvite))
                    .filter(c -> c.disponivel(agora))
                    .orElseThrow(() -> ApiException.regra("CONVITE_INVALIDO",
                            "Convite inválido, expirado ou já utilizado. Peça outro a um professor."));
        }
        boolean aprovado = convite != null;
        professor.setPapel(Papel.PROFESSOR);
        professor.setAprovado(aprovado);
        professor.setAprovadoEm(aprovado ? agora : null);
        professor.setAprovadoPor(convite == null ? null : convite.getCriadoPor());
        professor = usuarioRepository.save(professor);
        if (convite != null) {
            convite.setUsadoPor(professor);
            convite.setUsadoEm(agora);
        }
        log.info("Professor {} cadastrado: {}", professor.getId(),
                aprovado ? "aprovado por convite" : "pendente de aprovação");
        return professor;
    }

    // ======================== aprovação ========================

    @Transactional(readOnly = true)
    public List<ProfessorPendenteResponse> pendentes() {
        return usuarioRepository.findByPapelAndAprovadoFalseOrderByCriadoEmAsc(Papel.PROFESSOR).stream()
                .map(ProfessorPendenteResponse::de).toList();
    }

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
        Instant agora = Instant.now();

        List<ConviteProfessor> existentes = conviteRepository.findByCriadoPorIdOrderByCriadoEmDesc(professorId);

        boolean temConviteAtivo = existentes.stream().anyMatch(c -> c.disponivel(agora));
        if (temConviteAtivo) {
            throw ApiException.regra("CONVITE_ATIVO_EXISTENTE",
                    "Você já possui um convite ativo. Invalide o convite atual antes de gerar um novo.");
        }

        if (!existentes.isEmpty()) {
            ConviteProfessor ultimo = existentes.get(0);
            Instant liberadoEm = ultimo.getCriadoEm().plus(Duration.ofMinutes(cooldownConviteMinutos));
            if (agora.isBefore(liberadoEm)) {
                long segundosRestantes = Math.max(1, Duration.between(agora, liberadoEm).getSeconds());
                long minutos = segundosRestantes / 60;
                long segundos = segundosRestantes % 60;
                throw ApiException.regra("CONVITE_COOLDOWN",
                        String.format("Aguarde %dm%02ds antes de gerar um novo convite.", minutos, segundos));
            }
        }

        String codigo = novoCodigo();
        ConviteProfessor convite = conviteRepository.save(ConviteProfessor.builder()
                .codigoHash(hash(codigo))
                .criadoPor(criador)
                .criadoEm(agora)
                .expiraEm(agora.plus(Duration.ofDays(validadeConviteDias)))
                .build());
        return new ConviteCriadoResponse(convite.getId(), codigo, convite.getExpiraEm());
    }

    public ConviteResponse invalidarConvite(UUID professorId, UUID conviteId) {
        aprovado(professorId);
        ConviteProfessor convite = conviteRepository.findById(conviteId)
                .filter(c -> c.getCriadoPor().getId().equals(professorId))
                .orElseThrow(() -> ApiException.naoEncontrado("Convite não encontrado."));

        Instant agora = Instant.now();
        if (!convite.disponivel(agora)) {
            throw ApiException.regra("CONVITE_NAO_DISPONIVEL",
                    "Este convite já foi utilizado, expirado ou invalidado.");
        }

        convite.setInvalidadoEm(agora);
        log.info("Convite {} invalidado pelo professor {}", convite.getId(), professorId);
        return ConviteResponse.de(convite);
    }

    @Transactional(readOnly = true)
    public List<ConviteResponse> convites(UUID professorId) {
        return conviteRepository.findByCriadoPorIdOrderByCriadoEmDesc(professorId).stream()
                .map(ConviteResponse::de).toList();
    }

    // ======================== apoio ========================

    private Usuario aprovado(UUID professorId) {
        return usuarioRepository.findById(professorId)
                .filter(u -> u.getPapel() == Papel.PROFESSOR && u.isAprovado())
                .orElseThrow(() -> ApiException.acessoNegado("Somente professores aprovados podem fazer isso."));
    }

    private static String novoCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO + 1);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            if (i == TAMANHO_CODIGO / 2) sb.append('-');
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String hash(String codigo) {
        return CodigoAcessoService.hash("convite-professor",
                codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""));
    }
}