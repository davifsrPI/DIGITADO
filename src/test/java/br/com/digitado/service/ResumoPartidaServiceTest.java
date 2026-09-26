package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.service.JogoSalaService.RelatorioPalavra;
import br.com.digitado.service.JogoSalaService.RespostaDetalhe;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contas do resumo pessoal da partida: o que o aluno acertou, o que errou, a
 * media da turma e o percentual de erro de cada palavra. Logica pura, sem banco.
 */
class ResumoPartidaServiceTest {

    private final ResumoPartidaService service = new ResumoPartidaService();

    private static RespostaDetalhe resposta(String login, String texto, boolean correta, int ordem) {
        return new RespostaDetalhe(login, "Aluno " + login, texto, correta, ordem);
    }

    private static RelatorioPalavra palavra(int indice, String texto, List<RespostaDetalhe> respostas) {
        int acertos = (int) respostas.stream().filter(RespostaDetalhe::correta).count();
        return new RelatorioPalavra(indice, texto, "MEDIO", respostas.size(), acertos, respostas);
    }

    /**
     * Partida de 3 palavras com 4 alunos:
     * - "casa": ana e bia acertam, caio e duda erram  -> 50% de erro
     * - "exercicio": so ana acerta                     -> 75% de erro
     * - "paralelepipedo": ninguem acerta               -> 100% de erro
     */
    private static List<RelatorioPalavra> partidaDeExemplo() {
        return List.of(
            palavra(
                0,
                "casa",
                List.of(
                    resposta("ana", "casa", true, 1),
                    resposta("bia", "casa", true, 2),
                    resposta("caio", "caza", false, 3),
                    resposta("duda", "kasa", false, 4)
                )
            ),
            palavra(
                1,
                "exercicio",
                List.of(
                    resposta("ana", "exercicio", true, 1),
                    resposta("bia", "exercisio", false, 2),
                    resposta("caio", "ezercicio", false, 3),
                    resposta("duda", "exersicio", false, 4)
                )
            ),
            palavra(
                2,
                "paralelepipedo",
                List.of(
                    resposta("ana", "paralelepipeto", false, 1),
                    resposta("bia", "paralelipipedo", false, 2),
                    resposta("caio", "paralepipedo", false, 3),
                    resposta("duda", "paralelepido", false, 4)
                )
            )
        );
    }

    @Test
    @DisplayName("separa os acertos dos erros do proprio aluno")
    void separaAcertosEErros() {
        ResumoPartidaService.ResumoAluno resumo = service.montar(partidaDeExemplo(), "ana", "ana").orElseThrow();

        assertThat(resumo.totalPalavras()).isEqualTo(3);
        assertThat(resumo.acertos()).isEqualTo(2);
        assertThat(resumo.erros()).isEqualTo(1);
        assertThat(resumo.semResposta()).isZero();
        assertThat(resumo.palavras()).extracting(ResumoPartidaService.PalavraResumo::acertou).containsExactly(true, true, false);
        // O texto digitado volta para o aluno comparar com a palavra certa
        assertThat(resumo.palavras().get(2).respostaDigitada()).isEqualTo("paralelepipeto");
    }

    @Test
    @DisplayName("percentual de erro de cada palavra sai sobre quem respondeu")
    void percentualDeErroPorPalavra() {
        ResumoPartidaService.ResumoAluno resumo = service.montar(partidaDeExemplo(), "caio", "caio").orElseThrow();

        assertThat(resumo.palavras()).extracting(ResumoPartidaService.PalavraResumo::pctErro).containsExactly(50, 75, 100);
        assertThat(resumo.palavras()).extracting(ResumoPartidaService.PalavraResumo::totalErros).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("media da turma conta todo mundo que respondeu, nao so o aluno")
    void mediaDaTurma() {
        // ana 2 acertos, bia 1, caio 0, duda 0 -> media 0,75
        ResumoPartidaService.ResumoAluno resumo = service.montar(partidaDeExemplo(), "bia", "bia").orElseThrow();

        assertThat(resumo.totalParticipantes()).isEqualTo(4);
        assertThat(resumo.mediaAcertosTurma()).isEqualTo(0.8); // 0,75 arredondado para uma casa
        assertThat(resumo.acertos()).isEqualTo(1);
    }

    @Test
    @DisplayName("palavra que o aluno deixou passar conta como sem resposta, nao como erro")
    void palavraSemResposta() {
        List<RelatorioPalavra> relatorio = List.of(
            palavra(0, "casa", List.of(resposta("ana", "casa", true, 1), resposta("bia", "caza", false, 2))),
            // bia nao respondeu esta: o tempo acabou antes
            palavra(1, "exercicio", List.of(resposta("ana", "exercicio", true, 1)))
        );

        ResumoPartidaService.ResumoAluno resumo = service.montar(relatorio, "bia", "bia").orElseThrow();

        assertThat(resumo.acertos()).isZero();
        assertThat(resumo.erros()).isEqualTo(1);
        assertThat(resumo.semResposta()).isEqualTo(1);
        ResumoPartidaService.PalavraResumo naoRespondida = resumo.palavras().get(1);
        assertThat(naoRespondida.respondeu()).isFalse();
        assertThat(naoRespondida.respostaDigitada()).isNull();
    }

    @Test
    @DisplayName("nome do cabecalho e o publico que ficou gravado na resposta")
    void usaNomePublicoDaResposta() {
        List<RelatorioPalavra> relatorio = List.of(palavra(0, "casa", List.of(new RespostaDetalhe("ana", "Foguete", "casa", true, 1))));

        ResumoPartidaService.ResumoAluno resumo = service.montar(relatorio, "ana", "ana").orElseThrow();

        assertThat(resumo.nome()).isEqualTo("Foguete");
    }

    @Test
    @DisplayName("quem nao respondeu nada nao tem resumo")
    void semParticipacaoNaoTemResumo() {
        Optional<ResumoPartidaService.ResumoAluno> resumo = service.montar(partidaDeExemplo(), "visitante", "visitante");

        assertThat(resumo).isEmpty();
    }

    @Test
    @DisplayName("partida sem relatorio nao gera resumo")
    void relatorioVazio() {
        assertThat(service.montar(List.of(), "ana", "ana")).isEmpty();
        assertThat(service.montar(null, "ana", "ana")).isEmpty();
    }
}
