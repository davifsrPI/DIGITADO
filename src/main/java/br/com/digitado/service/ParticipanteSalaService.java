package br.com.digitado.service;

import br.com.digitado.domain.ParticipanteSala;
import br.com.digitado.repository.ParticipanteSalaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identificação do aluno em cada sala: o nome de verdade, a turma e a escolha
 * de aparecer para os colegas pelo apelido.
 *
 * Existe porque o placar mostrava o nome da CONTA, e numa sala de aula isso não
 * serve: a conta pode ter sido criada com qualquer coisa, e o professor precisa
 * saber quem é o aluno e de que turma ele é. O aluno preenche uma vez por sala,
 * na tela de entrada, e a partir daí:
 *
 * - os COLEGAS veem nomePublico (apelido, se ele escolheu esconder o nome);
 * - o PROFESSOR vê nomeParaProfessor (nome verdadeiro + apelido entre parênteses).
 *
 * O nome verdadeiro nunca entra no placar transmitido pelo WebSocket - ele só
 * sai pelos endpoints restritos ao dono da sala.
 */
@Service
public class ParticipanteSalaService {

    // Limites das colunas: corta no tamanho em vez de estourar o INSERT
    private static final int MAX_NOME = 60;
    private static final int MAX_TURMA = 30;
    private static final int MAX_APELIDO = 30;

    private final ParticipanteSalaRepository participanteSalaRepository;

    public ParticipanteSalaService(ParticipanteSalaRepository participanteSalaRepository) {
        this.participanteSalaRepository = participanteSalaRepository;
    }

    /**
     * O que a tela de entrada envia e devolve: o nome, a turma e a escolha de
     * mostrar o apelido no lugar do nome.
     */
    public record Identificacao(String nome, String turma, boolean usarApelido, String apelido) {}

    /**
     * Uma linha da lista que só o professor recebe: o login (chave do placar),
     * o nome verdadeiro, a turma e os dois nomes já montados - o que a turma vê
     * e o que ele vê.
     */
    public record ParticipanteVM(String login, String nome, String turma, boolean usarApelido, String apelido, String nomeProfessor) {}

    /** Erro de preenchimento da tela de entrada (nome/turma/apelido em branco). */
    public static class IdentificacaoInvalidaException extends RuntimeException {

        public IdentificacaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    /**
     * Grava (ou atualiza) a identificação do aluno naquela sala.
     *
     * Os três campos são obrigatórios de verdade - a validação do formulário no
     * navegador não vale como garantia, um cliente adulterado entraria sem nome.
     * O apelido só é exigido quando o aluno escolheu jogar com ele.
     */
    @Transactional
    public ParticipanteSala salvar(String salaCodigo, String login, Identificacao dados) {
        String nome = limpar(dados.nome(), MAX_NOME);
        String turma = limpar(dados.turma(), MAX_TURMA);
        String apelido = limpar(dados.apelido(), MAX_APELIDO);
        if (nome == null) {
            throw new IdentificacaoInvalidaException("Informe o seu nome");
        }
        if (turma == null) {
            throw new IdentificacaoInvalidaException("Informe a sua turma");
        }
        if (dados.usarApelido() && apelido == null) {
            throw new IdentificacaoInvalidaException("Informe o apelido que os colegas vão ver");
        }
        ParticipanteSala participante = participanteSalaRepository
            .findBySalaCodigoAndLogin(salaCodigo, login)
            .orElseGet(() -> {
                ParticipanteSala novo = new ParticipanteSala();
                novo.setSalaCodigo(salaCodigo);
                novo.setLogin(login);
                return novo;
            });
        participante.setNome(nome);
        participante.setTurma(turma);
        participante.setUsarApelido(dados.usarApelido());
        participante.setApelido(dados.usarApelido() ? apelido : null);
        participante.setDataEntrada(Instant.now());
        return participanteSalaRepository.save(participante);
    }

    @Transactional(readOnly = true)
    public Optional<ParticipanteSala> buscar(String salaCodigo, String login) {
        return participanteSalaRepository.findBySalaCodigoAndLogin(salaCodigo, login);
    }

    /**
     * Nome que os colegas veem, quando o aluno já se identificou nesta sala.
     * Vazio para quem entrou antes desta tela existir (ou para o professor): aí
     * vale o nome de exibição antigo, resolvido pelo JogoSalaController.
     */
    @Transactional(readOnly = true)
    public Optional<String> nomePublico(String salaCodigo, String login) {
        return buscar(salaCodigo, login).map(ParticipanteSala::nomePublico);
    }

    /** Todos os alunos identificados na sala - lista restrita ao dono da sala. */
    @Transactional(readOnly = true)
    public List<ParticipanteVM> listar(String salaCodigo) {
        return participanteSalaRepository.findBySalaCodigoOrderByNomeAsc(salaCodigo).stream().map(this::toVM).toList();
    }

    /**
     * login → nome que o PROFESSOR vê. É o mapa que as telas dele usam para
     * traduzir o placar (que vem com o nome público) de volta para o nome real.
     */
    @Transactional(readOnly = true)
    public Map<String, String> nomesParaProfessor(String salaCodigo) {
        return participanteSalaRepository
            .findBySalaCodigoOrderByNomeAsc(salaCodigo)
            .stream()
            .collect(Collectors.toMap(ParticipanteSala::getLogin, ParticipanteSala::nomeParaProfessor, (a, b) -> a));
    }

    private ParticipanteVM toVM(ParticipanteSala p) {
        return new ParticipanteVM(p.getLogin(), p.getNome(), p.getTurma(), p.isUsarApelido(), p.getApelido(), p.nomeParaProfessor());
    }

    // Apara as pontas e corta no tamanho da coluna; devolve null para texto vazio
    private String limpar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }
}
