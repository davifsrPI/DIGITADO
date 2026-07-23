/**
 * Núcleo do consentimento de cookies (LGPD) - sem dependência de React.
 *
 * Responsabilidades:
 * 1. Guardar/ler a decisão do usuário (localStorage, com versão e data);
 * 2. Funcionar como PORTÃO de scripts: nenhum rastreador (Analytics, Pixel...)
 *    é carregado antes do consentimento explícito da categoria correspondente;
 * 3. Emitir eventos para a UI reagir (banner some, modal abre de qualquer tela).
 *
 * Por que localStorage e não backend? O consentimento precisa existir ANTES de
 * qualquer chamada identificável e vale para visitantes anônimos - é uma
 * preferência do dispositivo, exatamente o caso de uso previsto pela LGPD para
 * armazenamento local. Guardar só isso no navegador não conflita com a regra
 * do projeto de validar dados de negócio no backend.
 */

export type CategoriaOpcional = 'preferencias' | 'analiticos' | 'marketing';

export interface CategoriasConsentimento {
  // Essenciais (sessão, segurança, o próprio registro do consentimento):
  // sempre ativos - o site não funciona sem eles e a LGPD os dispensa de opt-in
  essenciais: true;
  preferencias: boolean;
  analiticos: boolean;
  marketing: boolean;
}

export interface ConsentimentoCookies {
  // Versão da política: se os textos/categorias mudarem, incremente para
  // reapresentar o banner a todos os usuários
  versao: number;
  data: string;
  categorias: CategoriasConsentimento;
}

export const VERSAO_POLITICA = 1;
const CHAVE_STORAGE = 'digitado.cookieConsent';

// Eventos globais: a UI (banner/modal) escuta; qualquer botão "Gerenciar Cookies"
// no app só precisa disparar EVENTO_ABRIR_GERENCIADOR
export const EVENTO_CONSENTIMENTO_ALTERADO = 'digitado:cookie-consent-alterado';
export const EVENTO_ABRIR_GERENCIADOR = 'digitado:abrir-gerenciador-cookies';

export const CONSENTIMENTO_RECUSA_TOTAL: CategoriasConsentimento = {
  essenciais: true,
  preferencias: false,
  analiticos: false,
  marketing: false,
};

export const CONSENTIMENTO_ACEITE_TOTAL: CategoriasConsentimento = {
  essenciais: true,
  preferencias: true,
  analiticos: true,
  marketing: true,
};

/** Lê a decisão salva; null se nunca decidiu ou se a política mudou de versão. */
export const obterConsentimento = (): ConsentimentoCookies | null => {
  try {
    const bruto = localStorage.getItem(CHAVE_STORAGE);
    if (!bruto) return null;
    const salvo = JSON.parse(bruto) as ConsentimentoCookies;
    if (salvo?.versao !== VERSAO_POLITICA || !salvo.categorias) return null;
    return salvo;
  } catch {
    return null;
  }
};

/**
 * Salva a decisão, avisa a aplicação e aplica o portão de scripts.
 * Se o usuário REDUZIU permissões que já estavam em uso, recarrega a página -
 * é a única forma garantida de interromper rastreadores já carregados.
 */
export const salvarConsentimento = (categorias: Omit<CategoriasConsentimento, 'essenciais'>): ConsentimentoCookies => {
  const anterior = obterConsentimento();
  const consentimento: ConsentimentoCookies = {
    versao: VERSAO_POLITICA,
    data: new Date().toISOString(),
    categorias: { essenciais: true, ...categorias },
  };
  localStorage.setItem(CHAVE_STORAGE, JSON.stringify(consentimento));
  window.dispatchEvent(new CustomEvent(EVENTO_CONSENTIMENTO_ALTERADO, { detail: consentimento }));

  const reduziuPermissao =
    anterior &&
    (['preferencias', 'analiticos', 'marketing'] as CategoriaOpcional[]).some(c => anterior.categorias[c] && !consentimento.categorias[c]);
  if (reduziuPermissao) {
    window.location.reload();
    return consentimento;
  }

  aplicarConsentimento(consentimento);
  return consentimento;
};

export const aceitarTodos = () => salvarConsentimento(CONSENTIMENTO_ACEITE_TOTAL);

export const recusarOpcionais = () => salvarConsentimento(CONSENTIMENTO_RECUSA_TOTAL);

/** Abre o modal de preferências de qualquer lugar do app (footer, página de privacidade...). */
export const abrirGerenciadorCookies = () => {
  window.dispatchEvent(new CustomEvent(EVENTO_ABRIR_GERENCIADOR));
};

// ─── Portão de scripts de rastreamento ──────────────────────────────────────
//
// REGRA DE OURO: todo script de terceiros entra AQUI, nunca no index.html.
// Assim é impossível carregar um rastreador antes do consentimento.

const scriptsJaCarregados = new Set<string>();

/** Injeta um script externo uma única vez. */
const carregarScript = (id: string, src: string, onLoad?: () => void) => {
  if (scriptsJaCarregados.has(id) || document.getElementById(id)) return;
  scriptsJaCarregados.add(id);
  const s = document.createElement('script');
  s.id = id;
  s.src = src;
  s.async = true;
  if (onLoad) s.onload = onLoad;
  document.head.appendChild(s);
};

/**
 * Carrega apenas os scripts das categorias autorizadas.
 * Chamada na inicialização do app (se já há consentimento salvo) e a cada
 * alteração de consentimento. Idempotente: nada é carregado duas vezes.
 */
export const aplicarConsentimento = (consentimento: ConsentimentoCookies | null) => {
  if (!consentimento) return; // sem decisão = só essenciais, nenhum rastreador

  if (consentimento.categorias.analiticos) {
    carregarScriptsAnaliticos();
  }
  if (consentimento.categorias.marketing) {
    carregarScriptsMarketing();
  }
  // "preferencias" não injeta script externo: autoriza cookies/localStorage de
  // conveniência (ex.: lembrar volume do áudio, tema) - consulte antes de gravar:
  //   obterConsentimento()?.categorias.preferencias
};

/** Google Analytics 4 (exemplo) - descomente e informe seu ID quando contratar. */
const carregarScriptsAnaliticos = () => {
  // carregarScript('ga4', 'https://www.googletagmanager.com/gtag/js?id=G-XXXXXXXXXX', () => {
  //   (window as any).dataLayer = (window as any).dataLayer || [];
  //   const gtag = (...args: unknown[]) => (window as any).dataLayer.push(args);
  //   gtag('js', new Date());
  //   gtag('config', 'G-XXXXXXXXXX', { anonymize_ip: true });
  // });
};

/** Meta Pixel (exemplo) - descomente e informe seu ID quando contratar. */
const carregarScriptsMarketing = () => {
  // carregarScript('meta-pixel', 'https://connect.facebook.net/en_US/fbevents.js', () => {
  //   (window as any).fbq?.('init', 'SEU_PIXEL_ID');
  //   (window as any).fbq?.('track', 'PageView');
  // });
};
