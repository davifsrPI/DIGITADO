import axios from 'axios';
import { falarPalavra, OpcoesFala } from './falar-palavra';

/**
 * Ponto único de "ouvir a palavra da rodada", nos dois modos possíveis.
 *
 * MODO SEGURO (texto ausente): o servidor não transmite mais o texto da palavra
 * enquanto a rodada está aberta - ele ia para o tópico que todo aluno assina, e a
 * resposta chegava ao aluno antes de ele responder. Aqui buscamos o ÁUDIO gerado
 * no servidor e tocamos o som, sem nunca ter o texto no aparelho.
 *
 * MODO ANTIGO (texto presente): quando o servidor não consegue gerar áudio (sem
 * sintetizador instalado), ele volta a transmitir o texto e quem fala é a voz do
 * navegador - que é melhor, mas exige ter a palavra aqui. A escolha é do servidor,
 * esta função só obedece ao que chegou no estado do jogo.
 *
 * O áudio vem por axios, e não num <audio src="...">: a API é autenticada por
 * token JWT no cabeçalho, que uma tag de áudio não manda. Buscamos os bytes com o
 * interceptador do axios e tocamos a partir de um blob.
 */

export interface OpcoesOuvir extends OpcoesFala {
  /** Código da sala - o áudio pedido é sempre o da rodada corrente dela. */
  codigoSala?: string;
  /** Texto da palavra, quando o servidor o transmitiu (modo antigo). */
  texto?: string | null;
  /**
   * Chamado quando o navegador recusa tocar o áudio sem um toque do usuário
   * (política de autoplay do iOS e do Chrome). A tela usa para pedir que o aluno
   * toque no botão de ouvir.
   */
  onBloqueadoPeloNavegador?: () => void;
}

// Áudio em reprodução: um novo pedido interrompe o anterior, como o cancel() da
// síntese de voz faz. Sem isto, dois cliques no botão tocariam em dobro.
let audioAtual: HTMLAudioElement | null = null;
let urlAtual: string | null = null;
let timerPausa: ReturnType<typeof setTimeout> | null = null;

const PAUSA_PADRAO_MS = 1000;

// Solta o blob anterior: sem revoke, cada rodada deixa um objeto preso na memória
const liberar = () => {
  if (audioAtual) {
    audioAtual.pause();
    audioAtual = null;
  }
  if (urlAtual) {
    URL.revokeObjectURL(urlAtual);
    urlAtual = null;
  }
};

/** Interrompe o que estiver tocando (troca de palavra, saída da tela). */
export const pararAudioDaPalavra = () => {
  if (timerPausa) {
    clearTimeout(timerPausa);
    timerPausa = null;
  }
  liberar();
};

const tocarDoServidor = async (codigoSala: string, opcoes: OpcoesOuvir) => {
  try {
    const { data } = await axios.get<Blob>(`/api/salas/${codigoSala}/audio`, { responseType: 'blob' });
    liberar();
    urlAtual = URL.createObjectURL(data);
    const audio = new Audio(urlAtual);
    audioAtual = audio;
    // onEnd também no erro: a tela usa para desligar a animação do ícone, e um
    // ícone tocando para sempre é pior que um áudio que não saiu
    audio.onended = () => {
      opcoes.onEnd?.();
      liberar();
    };
    audio.onerror = () => {
      opcoes.onEnd?.();
      liberar();
    };
    await audio.play();
  } catch (erro: any) {
    opcoes.onEnd?.();
    // NotAllowedError é a política de autoplay: falta um toque do usuário
    if (erro?.name === 'NotAllowedError') {
      opcoes.onBloqueadoPeloNavegador?.();
    }
  }
};

/**
 * Ouve a palavra da rodada: som do servidor quando o texto não veio, voz do
 * navegador quando veio.
 *
 * A pausa de 1s antes de começar vale para os dois modos - é o tempo de o aluno
 * se preparar para ouvir. Ao REOUVIR (botão), a tela passa pausaMs: 0.
 */
export const ouvirPalavraDaRodada = (opcoes: OpcoesOuvir = {}) => {
  const { texto, codigoSala } = opcoes;

  // Modo antigo: o servidor transmitiu o texto, então a voz do navegador fala
  if (texto) {
    pararAudioDaPalavra();
    falarPalavra(texto, opcoes);
    return;
  }

  if (!codigoSala) {
    opcoes.onEnd?.();
    return;
  }

  pararAudioDaPalavra();
  const pausa = opcoes.pausaMs ?? PAUSA_PADRAO_MS;
  if (pausa <= 0) {
    void tocarDoServidor(codigoSala, opcoes);
    return;
  }
  timerPausa = setTimeout(() => {
    timerPausa = null;
    void tocarDoServidor(codigoSala, opcoes);
  }, pausa);
};

export default ouvirPalavraDaRodada;
