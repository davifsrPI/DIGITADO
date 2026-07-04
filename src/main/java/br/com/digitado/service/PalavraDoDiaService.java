package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.PalavraDoDiaTentativa;
import br.com.digitado.repository.PalavraDoDiaTentativaRepository;
import br.com.digitado.repository.PalavraRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica da Palavra do Dia — TODA a informação sensível fica aqui no backend:
 *
 * - A palavra é sorteada de forma determinística pelo dia (mesma palavra para todos
 *   durante o dia inteiro) e o TEXTO NUNCA é enviado ao frontend antes da tentativa;
 *   o cliente recebe apenas as letras embaralhadas (anagrama).
 * - A validação da resposta acontece aqui, com a mesma regra das partidas.
 * - O controle de "uma chance" é do servidor: conta logada é validada contra o banco
 *   (constraint única por dia+login) e visitante anônimo recebe um cookie httpOnly.
 */
@Service
public class PalavraDoDiaService {

    // Fuso oficial do jogo — o "dia" vira à meia-noite de Brasília
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final PalavraRepository palavraRepository;
    private final PalavraDoDiaTentativaRepository tentativaRepository;
    private final PalavraEstatisticaService estatisticaService;

    public PalavraDoDiaService(
        PalavraRepository palavraRepository,
        PalavraDoDiaTentativaRepository tentativaRepository,
        PalavraEstatisticaService estatisticaService
    ) {
        this.palavraRepository = palavraRepository;
        this.tentativaRepository = tentativaRepository;
        this.estatisticaService = estatisticaService;
    }

    public LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    // Sorteio determinístico: índice = dia-da-época % total de palavras ativas.
    // Ordenação estável por id garante a mesma palavra em todas as chamadas do dia.
    public Optional<Palavra> palavraDeHoje() {
        long total = palavraRepository.countByAtivaTrue();
        if (total == 0) {
            return Optional.empty();
        }
        int indice = (int) (hoje().toEpochDay() % total);
        return palavraRepository.findByAtivaTrue(PageRequest.of(indice, 1, Sort.by("id"))).stream().findFirst();
    }

    // Embaralha as letras com semente fixa do dia — todo mundo vê o mesmo anagrama
    // e chamadas repetidas não vazam informação extra sobre a ordem original
    public String embaralhar(String texto) {
        List<Character> letras = new ArrayList<>();
        for (char c : texto.toUpperCase().toCharArray()) {
            letras.add(c);
        }
        Random random = new Random(hoje().toEpochDay());
        String original = texto.toUpperCase();
        // Tenta algumas vezes até o anagrama ficar diferente da palavra original
        for (int i = 0; i < 10; i++) {
            java.util.Collections.shuffle(letras, random);
            StringBuilder sb = new StringBuilder();
            letras.forEach(sb::append);
            if (!sb.toString().equals(original) || texto.length() <= 1) {
                return sb.toString();
            }
        }
        StringBuilder sb = new StringBuilder();
        letras.forEach(sb::append);
        return sb.toString();
    }

    // O usuário logado já usou a chance de hoje? (fonte da verdade: banco)
    public Optional<PalavraDoDiaTentativa> tentativaDoUsuario(String login) {
        return tentativaRepository.findByDataAndLogin(hoje(), login);
    }

    // Valida a resposta, grava a tentativa e atualiza as estatísticas — tudo no backend.
    // Mesma regra de comparação das partidas: ignora maiúsculas, acentos contam.
    @Transactional
    public boolean tentar(Palavra palavra, String login, String resposta) {
        boolean acertou = resposta != null && resposta.trim().toLowerCase().equals(palavra.getTexto().trim().toLowerCase());

        tentativaRepository.save(new PalavraDoDiaTentativa().data(hoje()).login(login).acertou(acertou).palavraId(palavra.getId()));
        estatisticaService.registrarTentativa(palavra.getId(), acertou);
        return acertou;
    }

    // Contadores agregados da palavra (para exibir "% de acerto" após a tentativa).
    // Leitura direta no banco, nas colunas total_tentativas/total_acertos da tabela palavra
    public long[] estatisticasDaPalavra(Long palavraId) {
        List<Object[]> linhas = palavraRepository.buscarEstatistica(palavraId);
        if (linhas.isEmpty()) {
            return new long[] { 0, 0 };
        }
        Object[] linha = linhas.get(0);
        return new long[] { ((Number) linha[0]).longValue(), ((Number) linha[1]).longValue() };
    }
}
