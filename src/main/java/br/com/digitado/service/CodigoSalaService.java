package br.com.digitado.service;

import br.com.digitado.repository.SalaRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o código de acesso da sala - o ÚNICO lugar do sistema que decide como
 * esse código é formado.
 *
 * Antes quem sorteava era a tela de criação, no navegador: o cliente escolhia a
 * chave primária da sala e, quando dava colisão, tentava de novo até cinco
 * vezes. O alfabeto e o tamanho ficavam no front, repetidos em outros arquivos
 * para validar o campo de entrada. Agora a regra vive aqui, e o servidor só
 * devolve código que ele já conferiu estar livre.
 */
@Service
public class CodigoSalaService {

    /**
     * Alfabeto do código: sem O, I, 1 e 0. São os caracteres que o aluno mais
     * confunde ao copiar o código do quadro, e trocar um por outro manda ele
     * para uma sala que não existe.
     */
    public static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Tamanho fixo do código - a tela de entrada desenha uma caixa por caractere. */
    public static final int TAMANHO = 6;

    /**
     * Tentativas antes de desistir. Com 32^6 (mais de um bilhão) de combinações,
     * bater em 20 códigos ocupados seguidos significa que alguma coisa está
     * errada - melhor falhar alto do que girar para sempre.
     */
    private static final int MAX_TENTATIVAS = 20;

    // SecureRandom e não Math.random/Random: o código é a credencial de entrada
    // na sala, e uma sequência previsível deixaria adivinhar salas alheias
    private final SecureRandom aleatorio = new SecureRandom();

    private final SalaRepository salaRepository;

    public CodigoSalaService(SalaRepository salaRepository) {
        this.salaRepository = salaRepository;
    }

    /** Sala sem código livre depois de MAX_TENTATIVAS sorteios. */
    public static class SemCodigoDisponivelException extends RuntimeException {

        public SemCodigoDisponivelException(String mensagem) {
            super(mensagem);
        }
    }

    /**
     * Um código que AINDA NÃO EXISTE no banco.
     *
     * A conferência aqui não dispensa a checagem na criação da sala: entre pedir
     * o código e criar a sala existe uma janela em que outra pessoa pode ficar
     * com ele. O POST continua rejeitando duplicado - esta consulta só faz a
     * colisão ser praticamente impossível em vez de improvável.
     */
    @Transactional(readOnly = true)
    public String gerarDisponivel() {
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS; tentativa++) {
            String codigo = sortear();
            if (!salaRepository.existsById(codigo)) {
                return codigo;
            }
        }
        throw new SemCodigoDisponivelException("Não foi possível gerar um código de sala livre");
    }

    /** Sorteia um código com o formato definido aqui, sem consultar o banco. */
    public String sortear() {
        StringBuilder codigo = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) {
            codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }
}
