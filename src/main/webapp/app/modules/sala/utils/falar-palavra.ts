/**
 * Síntese de voz do jogo — ponto ÚNICO de fala (professor, aluno, qualquer tela).
 *
 * Duas melhorias em relação ao SpeechSynthesis puro:
 *
 * 1. VOZ MENOS ROBOTIZADA: não existe uma voz "Maria" universal — cada
 *    sistema/navegador expõe vozes diferentes. Escolhemos a melhor pt-BR
 *    disponível por ordem de preferência:
 *      - "Francisca" (voz neural do Edge/Windows — a mais natural)
 *      - "Maria" (Microsoft Maria, padrão do Windows em pt-BR)
 *      - "Google português do Brasil" (Chrome)
 *      - "Luciana" / "Camila" (Safari/iOS/macOS)
 *      - qualquer voz pt-BR e, por fim, qualquer voz pt-*
 *    Se nada existir, o navegador usa a voz padrão dele (nunca quebra).
 *
 * 2. PAUSA DE 1 SEGUNDO antes de toda fala começar — dá tempo do jogador se
 *    preparar para ouvir. Cliques repetidos cancelam a fala/pausa anterior.
 */

// Ordem por NATURALIDADE, não por popularidade:
// - "Francisca" (neural do Edge) e "Google português do Brasil" (rede, Chrome)
//   soam muito mais humanas que as vozes locais do sistema;
// - "Maria" (local do Windows) fica como fallback — é a mais robotizada.
const PREFERENCIA_DE_VOZES = ['francisca', 'google português do brasil', 'luciana', 'camila', 'maria'];

const PAUSA_ANTES_DE_FALAR_MS = 1000;

let vozesCache: SpeechSynthesisVoice[] = [];
let timerPausa: ReturnType<typeof setTimeout> | null = null;

// As vozes carregam de forma assíncrona em alguns navegadores (Chrome dispara
// "voiceschanged" quando a lista fica pronta) — mantemos um cache atualizado
const atualizarVozes = () => {
  if (window.speechSynthesis) {
    vozesCache = window.speechSynthesis.getVoices();
  }
};

if (typeof window !== 'undefined' && window.speechSynthesis) {
  atualizarVozes();
  window.speechSynthesis.addEventListener?.('voiceschanged', atualizarVozes);
}

// Melhor voz pt-BR disponível neste navegador (null = deixar a padrão)
const escolherVoz = (): SpeechSynthesisVoice | null => {
  if (vozesCache.length === 0) atualizarVozes();
  const vozesPt = vozesCache.filter(v => v.lang?.toLowerCase().startsWith('pt'));
  if (vozesPt.length === 0) return null;

  for (const nomePreferido of PREFERENCIA_DE_VOZES) {
    const encontrada = vozesPt.find(v => v.name.toLowerCase().includes(nomePreferido));
    if (encontrada) return encontrada;
  }
  return vozesPt.find(v => v.lang?.toLowerCase() === 'pt-br') ?? vozesPt[0];
};

export interface OpcoesFala {
  /** Velocidade da fala (padrão 0.85 — um pouco mais lenta para ditado) */
  rate?: number;
  /** Chamado quando a fala termina (ou falha) — útil para estados visuais */
  onEnd?: () => void;
}

/** Fala o texto em pt-BR com a melhor voz disponível, após 1s de pausa. */
export const falarPalavra = (texto: string, opcoes: OpcoesFala = {}) => {
  if (!window.speechSynthesis || !texto) {
    opcoes.onEnd?.();
    return;
  }

  // Cancela qualquer fala em andamento E qualquer pausa pendente —
  // clicar duas vezes não pode enfileirar dois áudios
  window.speechSynthesis.cancel();
  if (timerPausa) clearTimeout(timerPausa);

  timerPausa = setTimeout(() => {
    const utter = new SpeechSynthesisUtterance(texto);
    utter.lang = 'pt-BR';
    utter.rate = opcoes.rate ?? 0.85;
    const voz = escolherVoz();
    if (voz) utter.voice = voz;
    if (opcoes.onEnd) {
      utter.onend = opcoes.onEnd;
      utter.onerror = opcoes.onEnd;
    }
    window.speechSynthesis.speak(utter);
  }, PAUSA_ANTES_DE_FALAR_MS);
};

export default falarPalavra;
