/**
 * Síntese de voz do jogo, ponto ÚNICO de fala (professor, aluno, qualquer tela).
 *
 * Três cuidados em relação ao SpeechSynthesis puro:
 *
 * 1. VOZ MENOS ROBOTIZADA: não existe uma voz "Maria" universal, cada
 *    sistema/navegador expõe vozes diferentes. Escolhemos a melhor pt-BR
 *    disponível por ordem de preferência:
 *     - "Francisca" (voz neural do Edge/Windows, a mais natural)
 *     - "Maria" (Microsoft Maria, padrão do Windows em pt-BR)
 *     - "Google português do Brasil" (Chrome)
 *     - "Luciana" / "Camila" (Safari/iOS/macOS)
 *     - qualquer voz pt-BR e, por fim, qualquer voz pt-*
 *    Se nada existir, o navegador usa a voz padrão dele (nunca quebra).
 *
 * 2. PAUSA DE 1 SEGUNDO antes de toda fala começar, dá tempo do jogador se
 *    preparar para ouvir. Cliques repetidos cancelam a fala/pausa anterior.
 *
 * 3. DESTRAVA NO IPHONE: ver destravarNoPrimeiroToque abaixo.
 */

// Ordem por NATURALIDADE, não por popularidade:
// - "Francisca" (neural do Edge) e "Google português do Brasil" (rede, Chrome)
//   soam muito mais humanas que as vozes locais do sistema;
// - "Maria" (local do Windows) fica como fallback, é a mais robotizada.
const PREFERENCIA_DE_VOZES = ['francisca', 'google português do brasil', 'luciana', 'camila', 'maria'];

const PAUSA_ANTES_DE_FALAR_MS = 1000;

// Espera entre cancelar a fala anterior e começar a próxima. O Safari engole a
// fala pedida no mesmo instante em que um cancel() foi emitido - o áudio
// simplesmente não sai, sem erro nenhum.
const ESPERA_APOS_CANCELAR_MS = 120;

let vozesCache: SpeechSynthesisVoice[] = [];
let timerPausa: ReturnType<typeof setTimeout> | null = null;

// As vozes carregam de forma assíncrona em alguns navegadores (Chrome dispara
// "voiceschanged" quando a lista fica pronta), mantemos um cache atualizado
const atualizarVozes = () => {
  if (window.speechSynthesis) {
    vozesCache = window.speechSynthesis.getVoices();
  }
};

/**
 * Destrava o áudio no primeiro toque da pessoa na página.
 *
 * O Safari do iPhone só deixa a síntese de voz começar dentro de um gesto do
 * usuário. A palavra da rodada é falada sozinha, por um timer, sem toque nenhum
 * no meio - e no iPhone ela simplesmente não saía, muda, sem erro no console.
 *
 * A saída é falar UMA vez, sem som, no primeiro toque em qualquer lugar (entrar
 * na sala, tocar no campo, apertar um botão). A partir daí o motor de voz fica
 * liberado para o resto da visita, inclusive para as falas automáticas.
 */
let audioLiberado = false;
const destravarNoPrimeiroToque = () => {
  if (audioLiberado || typeof window === 'undefined' || !window.speechSynthesis) return;
  audioLiberado = true;
  try {
    const mudo = new SpeechSynthesisUtterance(' ');
    mudo.volume = 0;
    window.speechSynthesis.speak(mudo);
  } catch {
    // Motor de voz indisponível: o jogo segue, só sem áudio
  }
  // As vozes do iOS costumam só aparecer depois da primeira interação
  atualizarVozes();
};

if (typeof window !== 'undefined' && window.speechSynthesis) {
  atualizarVozes();
  window.speechSynthesis.addEventListener?.('voiceschanged', atualizarVozes);
  // once + capture: dispara no primeiro gesto, aconteça onde acontecer, e some
  for (const evento of ['pointerdown', 'touchend', 'keydown'] as const) {
    document.addEventListener(evento, destravarNoPrimeiroToque, { once: true, capture: true });
  }
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
  /** Velocidade da fala (padrão 0.85, um pouco mais lenta para ditado) */
  rate?: number;
  /** Chamado quando a fala termina (ou falha), útil para estados visuais */
  onEnd?: () => void;
  /**
   * Pausa (ms) antes de começar a falar. Padrão 1000ms na palavra da rodada,
   * para o jogador se preparar. Ao REOUVIR (botão de áudio) passamos 0: o
   * jogador já está pronto e qualquer espera aqui parece travamento.
   */
  pausaMs?: number;
}

/** Fala o texto em pt-BR com a melhor voz disponível, após a pausa configurada (1s por padrão). */
export const falarPalavra = (texto: string, opcoes: OpcoesFala = {}) => {
  const sintese = typeof window !== 'undefined' ? window.speechSynthesis : undefined;
  if (!sintese || !texto) {
    opcoes.onEnd?.();
    return;
  }

  // Esta chamada pode estar vindo de um clique (o botão de ouvir): é a melhor
  // hora para liberar o áudio do iPhone, se ainda não foi liberado
  destravarNoPrimeiroToque();

  // Cancela qualquer fala em andamento E qualquer pausa pendente,
  // clicar duas vezes não pode enfileirar dois áudios
  if (timerPausa) clearTimeout(timerPausa);
  const precisavaCancelar = sintese.speaking || sintese.pending;
  if (precisavaCancelar) sintese.cancel();

  const falar = () => {
    timerPausa = null;
    const utter = new SpeechSynthesisUtterance(texto);
    utter.lang = 'pt-BR';
    utter.rate = opcoes.rate ?? 0.85;
    const voz = escolherVoz();
    if (voz) utter.voice = voz;
    if (opcoes.onEnd) {
      utter.onend = opcoes.onEnd;
      utter.onerror = opcoes.onEnd;
    }
    // O Safari às vezes deixa a fila em pausa depois de a aba voltar do
    // segundo plano, e aí tudo que for pedido fica em silêncio esperando
    if (sintese.paused) sintese.resume();
    sintese.speak(utter);
  };

  // Só espera o cancelamento assentar quando havia mesmo algo falando
  const pausa = Math.max(opcoes.pausaMs ?? PAUSA_ANTES_DE_FALAR_MS, precisavaCancelar ? ESPERA_APOS_CANCELAR_MS : 0);
  if (pausa <= 0) {
    falar();
    return;
  }
  timerPausa = setTimeout(falar, pausa);
};

export default falarPalavra;
