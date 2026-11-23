package com.estudaenade.api.infra.codigo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Em vez de enviar e-mail, escreve o código no log (padrão, {@code otp.provider=log}). */
@Slf4j
@Service
@ConditionalOnProperty(name = "otp.provider", havingValue = "log", matchIfMissing = true)
public class LogEnviadorCodigo implements EnviadorCodigo {

    @Override
    public void enviar(String email, String codigo, int validadeMinutos) {
        log.info("============================================");
        log.info("  CÓDIGO PARA REDEFINIR A SENHA DE {}: {}", email, codigo);
        log.info("============================================");
    }
}
