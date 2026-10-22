import axios from 'axios';
import { falarPalavra, OpcoesFala } from './falar-palavra';

/**
 * Ponto único de "ouvir a palavra da rodada", nos dois modos possíveis.
 *
 * MODO SEGURO (texto ausente): o servidor não transmite o texto da palavra enquanto
 * a rodada está aberta - ele ia para o tópico que todo aluno assina, e a resposta
 * chegava ao aluno antes de ele responder. Aqui buscamos o ÁUDIO gerado no servidor
 * e tocamos o som, sem nunca ter o texto no aparelho.
 *
 * MODO ANTIGO (texto presente): quando o servidor não consegue gerar áudio (sem
 * sintetizador instalado), ele volta a transmitir o texto e quem fala é a voz do
 * navegador. A escolha é do servidor, esta função só obedece ao que chegou.
 *
 * O áudio vem por axios, e não num <audio src="...">: a API é autenticada por
 * token JWT no cabeçalho, que uma tag de áudio não manda. Buscamos os bytes com o
 * interceptador do axios e tocamos a partir de um blob.
 *
 * POR QUE UM ELEMENTO SÓ, DESTRAVADO NO PRIMEIRO TOQUE: o som chega depois de uma
 * ida ao servidor, e o play() acontece fora do gesto do usuário. O Safari do iPhone
 * recusa isso SEMPRE - inclusive quando o aluno toca no botão de ouvir - e o Chrome
 * recusa enquanto a página não recebeu nenhum toque. Era o que deixava o duelo 1v1
 * mudo: na sala de turma a caixa de som do professor cobria o aparelho calado do
 * aluno, no duelo não há professor. Um <audio> que já tocou dentro de um gesto fica
 * liberado para a visita inteira; por isso ele é criado uma vez, tocado em silêncio
 * no primeiro toque da página e reaproveitado em toda rodada.
 *
 * E o som da palavra fica guardado: ouvir de novo (botão) toca na hora, dentro do
 * próprio clique, sem nova ida ao servidor.
 */

export interface OpcoesOuvir extends OpcoesFala {
  /** Código da sala - o áudio pedido é sempre o da rodada corrente dela. */
  codigoSala?: string;
  /** Endereço do áudio, quando não é o de uma sala (ex.: modo individual). */
  urlAudio?: string;
  /**
   * Identifica a palavra da rodada (o id dela). Com ela o som buscado fica guardado
   * e "ouvir de novo" não volta ao servidor. Sem ela, busca sempre.
   */
  chave?: string | number | null;
  /** Texto da palavra, quando o servidor o transmitiu (modo antigo). */
  texto?: string | null;
  /**
   * Chamado quando o navegador recusa tocar o áudio sem um toque do usuário
   * (política de autoplay do iOS e do Chrome). A tela usa para pedir que o aluno
   * toque no botão de ouvir.
   */
  onBloqueadoPeloNavegador?: () => void;
}

// WAV de 44 bytes sem nenhuma amostra: tocá-lo no primeiro gesto libera o elemento
const SILENCIO = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';
const PAUSA_PADRAO_MS = 1000;

let elemento: HTMLAudioElement | null = null;
let destravado = false;
// Último som buscado: a chave da palavra e o blob dela
let guardado: { chave: string; url: string } | null = null;
let timerPausa: ReturnType<typeof setTimeout> | null = null;
// Cada pedido novo invalida os anteriores ainda a caminho do servidor
let pedidoAtual = 0;

const obterElemento = (): HTMLAudioElement | null => {
  if (!elemento && typeof Audio !== 'undefined') {
    elemento = new Audio();
    elemento.preload = 'auto';
  }
  return elemento;
};

/** Libera o elemento de áudio dentro de um gesto do usuário (primeiro toque na página). */
export const destravarAudio = () => {
  const el = obterElemento();
  if (!el || destravado) return;
  destravado = true;
  try {
    el.src = SILENCIO;
    const tocando = el.play();
    tocando
      ?.then(() => el.pause())
      .catch(() => {
        // Não liberou neste gesto: tenta de novo no próximo
        destravado = false;
      });
  } catch {
    destravado = false;
  }
};

if (typeof document !== 'undefined') {
  for (const evento of ['pointerdown', 'touchend', 'keydown'] as const) {
    document.addEventListener(evento, destravarAudio, { capture: true, passive: true });
  }
}

const liberarGuardado = () => {
  if (guardado) {
    URL.revokeObjectURL(guardado.url);
    guardado = null;
  }
};

/** Interrompe o que estiver tocando (troca de palavra, saída da tela). */
export const pararAudioDaPalavra = () => {
  pedidoAtual++;
  if (timerPausa) {
    clearTimeout(timerPausa);
    timerPausa = null;
  }
  if (elemento && elemento.src && !elemento.src.startsWith('data:')) {
    elemento.pause();
  }
};

const tocar = (url: string, opcoes: OpcoesOuvir) => {
  const el = obterElemento();
  if (!el) {
    opcoes.onEnd?.();
    return;
  }
  // onEnd também no erro: a tela usa para desligar a animação do ícone, e um
  // ícone tocando para sempre é pior que um áudio que não saiu
  el.onended = () => opcoes.onEnd?.();
  el.onerror = () => opcoes.onEnd?.();
  el.src = url;
  el.currentTime = 0;
  const tocando = el.play();
  tocando
    // Tocou: o elemento já está liberado, o primeiro toque não precisa mais
    // destravá-lo (e trocar o som no meio da palavra)
    ?.then(() => {
      destravado = true;
    })
    .catch((erro: any) => {
      opcoes.onEnd?.();
      // NotAllowedError é a política de autoplay: falta um toque do usuário
      if (erro?.name === 'NotAllowedError') {
        destravado = false;
        opcoes.onBloqueadoPeloNavegador?.();
      }
    });
};

const urlDoAudio = (opcoes: OpcoesOuvir): string | null =>
  opcoes.urlAudio ?? (opcoes.codigoSala ? `/api/salas/${opcoes.codigoSala}/audio` : null);

const buscarETocar = async (url: string, chave: string | null, pedido: number, opcoes: OpcoesOuvir) => {
  try {
    const { data } = await axios.get<Blob>(url, { responseType: 'blob' });
    if (pedido !== pedidoAtual) return; // outra palavra (ou a saída da tela) chegou antes
    liberarGuardado();
    const blobUrl = URL.createObjectURL(data);
    if (chave) guardado = { chave, url: blobUrl };
    tocar(blobUrl, opcoes);
  } catch {
    opcoes.onEnd?.();
  }
};

/**
 * Ouve a palavra da rodada: som do servidor quando o texto não veio, voz do
 * navegador quando veio.
 *
 * A pausa de 1s antes de começar vale para os dois modos - é o tempo de o aluno
 * se preparar para ouvir. Ao REOUVIR (botão), a tela passa pausaMs: 0, e com o som
 * já guardado ele toca dentro do próprio clique.
 */
export const ouvirPalavraDaRodada = (opcoes: OpcoesOuvir = {}) => {
  const { texto } = opcoes;

  // Modo antigo: o servidor transmitiu o texto, então a voz do navegador fala
  if (texto) {
    pararAudioDaPalavra();
    falarPalavra(texto, opcoes);
    return;
  }

  const url = urlDoAudio(opcoes);
  if (!url) {
    opcoes.onEnd?.();
    return;
  }

  pararAudioDaPalavra();
  const pedido = pedidoAtual;
  const chave = opcoes.chave != null ? `${url}#${opcoes.chave}` : null;
  const executar = () => {
    timerPausa = null;
    if (chave && guardado?.chave === chave) {
      tocar(guardado.url, opcoes);
    } else {
      void buscarETocar(url, chave, pedido, opcoes);
    }
  };
  const pausa = opcoes.pausaMs ?? PAUSA_PADRAO_MS;
  if (pausa <= 0) {
    executar();
    return;
  }
  timerPausa = setTimeout(executar, pausa);
};

export default ouvirPalavraDaRodada;
