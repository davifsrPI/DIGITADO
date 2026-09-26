package br.com.digitado.service;

import br.com.digitado.service.JogoSalaService.RelatorioPalavra;
import br.com.digitado.service.JogoSalaService.RespostaDetalhe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Monta o resumo PESSOAL de um aluno numa partida: o que ele acertou, o que
 * errou, como a turma foi na média e quanta gente tropeçou em cada palavra.
 *
 * Deriva tudo do relatório da partida (uma entrada por palavra, com as respostas
 * digitadas), então serve aos dois casos sem duplicar regra:
 *
 * - partida em andamento / recém-encerrada: relatório vivo do JogoSalaService;
 * - partida antiga: relatório gravado no snapshot (EstatisticaPartidaService),
 *   inclusive o das salas jogadas antes desta tela existir.
 *
 * Quem pode ver o quê é decidido no SalaResource: o aluno só pede o próprio
 * resumo, o resumo de outro aluno é restrito ao professor dono da sala.
 */
@Service
public class ResumoPartidaService {

    /**
     * Uma palavra da partida sob a ótica do aluno: o que ele escreveu, se
     * acertou e quanto da turma errou aquela palavra.
     *
     * respondeu = false quando o tempo acabou sem ele mandar nada - é diferente
     * de errar, e a tela mostra as duas coisas separadas.
     *
     * pctErro é calculado sobre quem RESPONDEU a palavra (mesma base do "% de
     * acerto" que o professor já vê no relatório).
     */
    public record PalavraResumo(
        int indice,
        String texto,
        String dificuldade,
        String respostaDigitada,
        boolean respondeu,
        boolean acertou,
        int totalRespostas,
        int totalErros,
        int pctErro
    ) {}

    /**
     * O resumo que a tela recebe: os números do aluno, a média da turma para ele
     * se comparar e a lista palavra a palavra.
     */
    public record ResumoAluno(
        String login,
        String nome,
        int totalPalavras,
        int acertos,
        int erros,
        int semResposta,
        double mediaAcertosTurma,
        int totalParticipantes,
        List<PalavraResumo> palavras
    ) {}

    /**
     * Resumo do aluno a partir do relatório da partida.
     *
     * Devolve vazio quando o login não participou (não respondeu nenhuma
     * palavra): sem isto a tela mostraria um resumo zerado para quem só assistiu.
     */
    public Optional<ResumoAluno> montar(List<RelatorioPalavra> relatorio, String login, String nomeFallback) {
        if (relatorio == null || relatorio.isEmpty() || login == null) {
            return Optional.empty();
        }
        // Participantes da partida = quem respondeu ao menos uma palavra. É a base
        // da média da turma: quem não jogou não pode puxar a média para baixo.
        Set<String> participantes = new LinkedHashSet<>();
        Map<String, Integer> acertosPorLogin = new HashMap<>();
        for (RelatorioPalavra palavra : relatorio) {
            for (RespostaDetalhe resposta : palavra.respostas()) {
                participantes.add(resposta.login());
                if (resposta.correta()) {
                    acertosPorLogin.merge(resposta.login(), 1, Integer::sum);
                }
            }
        }
        if (!participantes.contains(login)) {
            return Optional.empty();
        }

        String nome = nomeFallback;
        List<PalavraResumo> palavras = new ArrayList<>(relatorio.size());
        int acertos = 0;
        int erros = 0;
        int semResposta = 0;
        for (RelatorioPalavra palavra : relatorio) {
            RespostaDetalhe minha = palavra.respostas().stream().filter(r -> login.equals(r.login())).findFirst().orElse(null);
            if (minha != null && minha.nome() != null && !minha.nome().isBlank()) {
                nome = minha.nome();
            }
            int errosDaPalavra = palavra.totalRespostas() - palavra.totalAcertos();
            int pctErro = palavra.totalRespostas() > 0 ? (int) Math.round((errosDaPalavra * 100.0) / palavra.totalRespostas()) : 0;
            boolean respondeu = minha != null;
            boolean acertou = respondeu && minha.correta();
            if (!respondeu) {
                semResposta++;
            } else if (acertou) {
                acertos++;
            } else {
                erros++;
            }
            palavras.add(
                new PalavraResumo(
                    palavra.indice(),
                    palavra.texto(),
                    palavra.dificuldade(),
                    respondeu ? minha.texto() : null,
                    respondeu,
                    acertou,
                    palavra.totalRespostas(),
                    errosDaPalavra,
                    pctErro
                )
            );
        }

        // Média de acertos da turma: total de acertos dividido pelos participantes
        double mediaTurma = participantes.isEmpty()
            ? 0
            : participantes.stream().mapToInt(p -> acertosPorLogin.getOrDefault(p, 0)).sum() / (double) participantes.size();
        // Arredonda em uma casa - a tela mostra "7,3 de 15"
        mediaTurma = Math.round(mediaTurma * 10) / 10.0;

        return Optional.of(
            new ResumoAluno(login, nome, relatorio.size(), acertos, erros, semResposta, mediaTurma, participantes.size(), palavras)
        );
    }
}
