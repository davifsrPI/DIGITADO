package br.com.digitado.web.rest.vm;

// Resultado de uma tentativa da Palavra do Dia: a palavra correta só é revelada
// aqui (após a chance única ser consumida), junto com as estatísticas agregadas
public record ResultadoPalavraDoDiaVM(boolean acertou, String palavraCorreta, long totalTentativas, long totalAcertos) {}
