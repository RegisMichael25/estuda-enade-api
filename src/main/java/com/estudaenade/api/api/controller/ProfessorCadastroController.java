package com.estudaenade.api.api.controller;

import com.estudaenade.api.api.contrato.ProfessorCadastroApi;
import com.estudaenade.api.api.dto.CadastroProfessorDtos.ConviteCriadoResponse;
import com.estudaenade.api.api.dto.CadastroProfessorDtos.ConviteResponse;
import com.estudaenade.api.api.dto.CadastroProfessorDtos.ProfessorPendenteResponse;
import com.estudaenade.api.domain.service.CadastroProfessorService;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Aprovação de novos professores e convites. Como toda rota de professor, exige professor já aprovado.
 * Rotas em {@link ProfessorCadastroApi}.
 */
@RestController
@RequiredArgsConstructor
public class ProfessorCadastroController implements ProfessorCadastroApi {

    private final CadastroProfessorService cadastroProfessorService;

    @Override
    public List<ProfessorPendenteResponse> professoresPendentes() {
        return cadastroProfessorService.pendentes();
    }

    @Override
    public ProfessorPendenteResponse aprovarProfessor(UUID professorId) {
        return cadastroProfessorService.aprovar(UsuarioLogado.atual().id(), professorId);
    }

    @Override
    public List<ConviteResponse> convites() {
        return cadastroProfessorService.convites(UsuarioLogado.atual().id());
    }

    /** Gera um convite de uso único. O código só aparece nesta resposta. */
    @Override
    public ConviteCriadoResponse criarConvite() {
        return cadastroProfessorService.criarConvite(UsuarioLogado.atual().id());
    }
}
