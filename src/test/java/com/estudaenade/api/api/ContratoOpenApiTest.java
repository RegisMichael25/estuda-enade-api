package com.estudaenade.api.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As requisições e as interfaces dos controllers são geradas do contrato, então nunca divergem dele.
 * As respostas e os enums são escritos à mão e só mapeados no pom.xml ({@code schemaMappings}); este
 * teste garante que o que o contrato promete é o que o código devolve.
 */
class ContratoOpenApiTest {

    private static final Path CONTRATO = Path.of("src/main/resources/static/openapi/estuda-enade-api.json");
    private static final Pattern MAPEAMENTO = Pattern.compile("<schemaMapping>([^=<]+)=([^<]+)</schemaMapping>");

    @Test
    @SuppressWarnings("unchecked")
    void respostasEEnumsDoContratoBatemComOCodigo() throws Exception {
        Map<String, Object> contrato = JsonMapper.builder().build().readValue(Files.readString(CONTRATO), Map.class);
        Map<String, Map<String, Object>> schemas =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) contrato.get("components")).get("schemas");

        List<String> diferencas = new ArrayList<>();
        int conferidos = 0;
        Matcher m = MAPEAMENTO.matcher(Files.readString(Path.of("pom.xml")));
        while (m.find()) {
            String schema = m.group(1).trim();
            String tipo = m.group(2).trim().replace("&lt;", "<").replace("&gt;", ">");
            Map<String, Object> definicao = schemas.get(schema);
            assertTrue(definicao != null, "schemaMapping sem schema no contrato: " + schema);

            // Pagina<X> usa os campos do record genérico Pagina
            Class<?> classe = carregar(tipo.contains("<") ? tipo.substring(0, tipo.indexOf('<')) : tipo);
            Set<String> noCodigo = classe.isEnum()
                    ? Arrays.stream(classe.getEnumConstants()).map(c -> ((Enum<?>) c).name()).collect(Collectors.toCollection(TreeSet::new))
                    : Arrays.stream(classe.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toCollection(TreeSet::new));
            Set<String> noContrato = classe.isEnum()
                    ? new TreeSet<>((List<String>) definicao.get("enum"))
                    : new TreeSet<>(((Map<String, Object>) definicao.get("properties")).keySet());
            if (!noCodigo.equals(noContrato)) {
                diferencas.add("%s: contrato %s x código %s".formatted(schema, noContrato, noCodigo));
            }
            // Records sempre enviam todos os campos (os vazios vão como null): no contrato, todos são obrigatórios.
            // Assim o cliente gerado não trata como opcional um campo que sempre vem. ErroResponse é exceção:
            // o 403 da segurança é montado fora do record e não leva "campos".
            if (classe.isRecord() && !schema.equals("ErroResponse")) {
                Set<String> obrigatorios = new TreeSet<>((List<String>) definicao.getOrDefault("required", List.of()));
                if (!obrigatorios.equals(noContrato)) {
                    diferencas.add("%s: campos sem required no contrato %s".formatted(schema,
                            noContrato.stream().filter(c -> !obrigatorios.contains(c)).toList()));
                }
            }
            conferidos++;
        }
        assertTrue(conferidos > 30, "esperava encontrar os schemaMappings no pom.xml, achei " + conferidos);
        assertEquals(List.of(), diferencas, "o contrato OpenAPI e os records/enums divergem");
    }

    /** Aceita o nome com ponto de classes aninhadas (ex.: {@code ...QuestaoDtos.QuestaoResponse}). */
    private static Class<?> carregar(String nome) throws ClassNotFoundException {
        String tentativa = nome;
        while (true) {
            try {
                return Class.forName(tentativa);
            } catch (ClassNotFoundException e) {
                int ponto = tentativa.lastIndexOf('.');
                if (ponto < 0) throw new ClassNotFoundException(nome);
                tentativa = tentativa.substring(0, ponto) + "$" + tentativa.substring(ponto + 1);
            }
        }
    }
}
