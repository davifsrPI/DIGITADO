import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import './entrada-palavra.scss';

// Tipos de tentativa de burla que a guarda detecta e bloqueia
export type TipoBurla = 'colagem' | 'copia' | 'arrasto' | 'correcao-automatica' | 'insercao-multipla';

// Só letras do português (com acentos) e hífen de palavras compostas passam
const CARACTERES_INVALIDOS = /[^a-záàâãéêíóôõúüç-]/g;
// Enquanto o acento está sendo composto (tecla morta ´ seguida da vogal), o campo
// precisa exibir o acento sozinho por um instante. Apagá-lo nessa janela cancela a
// composição do navegador e a vogal acentuada nunca chega, por isso as teclas
// mortas passam durante a composição e só são sanitizadas no fim dela
const CARACTERES_INVALIDOS_COMPONDO = /[^a-záàâãéêíóôõúüç´`^~¨-]/g;
// Acentos que o teclado físico digita sozinhos antes de virarem vogal acentuada
const MARCAS_MORTAS = '´`^~¨';
// Depois de uma tecla morta, a vogal acentuada chega em seguida. Passada essa janela,
// uma troca de letra por letra não é mais acentuação - é o corretor.
const VALIDADE_TECLA_MORTA_MS = 5000;

// O que UMA tecla produz. Todo o resto (colar, arrastar, corretor, desfazer, autofill)
// é texto que não foi digitado e não entra no campo.
const INSERCOES_DE_TECLA = new Set(['insertText', 'insertCompositionText', 'insertLineBreak', 'insertParagraph']);
const MOTIVO_DA_INSERCAO: Record<string, TipoBurla> = {
  insertFromPaste: 'colagem',
  insertFromPasteAsQuotation: 'colagem',
  insertFromDrop: 'arrasto',
  insertReplacementText: 'correcao-automatica',
};

/**
 * Diferença entre dois estados do campo, medida descontando o pedaço igual do começo
 * e do fim: o trecho que saiu e o trecho que entrou.
 *
 * É o que separa DIGITAR de SER CORRIGIDO. Uma tecla insere 1 e remove 0; o backspace
 * remove 1 e insere 0; uma tecla morta virando vogal acentuada troca 1 por 1. Já o
 * corretor reescreve um trecho inteiro de uma vez ("csaa" virando "casa" troca 2 por
 * 2) e a sugestão da barra do teclado insere a palavra toda.
 */
export function diferenca(antes: string, depois: string): { removido: string; inserido: string } {
  let inicio = 0;
  while (inicio < antes.length && inicio < depois.length && antes[inicio] === depois[inicio]) {
    inicio++;
  }
  let fim = 0;
  while (fim < antes.length - inicio && fim < depois.length - inicio && antes[antes.length - 1 - fim] === depois[depois.length - 1 - fim]) {
    fim++;
  }
  return { removido: antes.slice(inicio, antes.length - fim), inserido: depois.slice(inicio, depois.length - fim) };
}

export function medirEdicao(antes: string, depois: string): { inseridos: number; removidos: number } {
  const { removido, inserido } = diferenca(antes, depois);
  return { removidos: removido.length, inseridos: inserido.length };
}

/**
 * A edição cabe em UMA tecla?
 *
 * Apagar nunca traz texto de fora, então apagar qualquer quantidade passa (selecionar
 * tudo e limpar o campo é edição normal). Inserir, no máximo um caractere.
 *
 * A troca de um caractere por outro é o caso delicado: é assim que a tecla morta do
 * teclado físico vira vogal acentuada (´ + a = á), e é assim também que o corretor
 * troca uma letra da palavra ("caza" virando "casa", "voce" virando "você"). Por isso
 * ela só passa quando há uma tecla morta por trás: o acento ainda no campo, ou a tecla
 * morta pressionada há instantes.
 */
function edicaoDeUmaTecla(antes: string, depois: string, compondo: boolean, teclaMorta: boolean): boolean {
  const { removido, inserido } = diferenca(antes, depois);
  if (inserido.length === 0) return true;
  if (inserido.length > 1 || removido.length > 1) return false;
  if (removido.length === 0) return true;
  return compondo && (MARCAS_MORTAS.includes(removido) || teclaMorta);
}

/**
 * O aparelho digita num teclado de vidro (celular, tablet)?
 *
 * Nesses teclados o corretor e a barra de sugestões são do sistema, não da página:
 * pedir "não corrija" pelos atributos não basta, o Gboard ignora. O jeito de o teclado
 * não oferecer nada é o campo ser do tipo senha - nenhum teclado sugere, corrige,
 * aprende ou aceita escrita deslizando num campo de senha. Ver EntradaPalavra.
 */
export function tecladoDeVidro(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  try {
    return window.matchMedia('(pointer: coarse)').matches;
  } catch {
    // Navegador sem suporte à consulta: segue como teclado físico
    return false;
  }
}

interface Props {
  id?: string;
  value: string;
  onChange: (valor: string) => void;
  // Notifica cada tentativa bloqueada de inserir texto sem digitar (colar, corretor, arrastar)
  onBurla?: (tipo: TipoBurla) => void;
  disabled?: boolean;
  maxLength?: number;
  placeholder?: string;
  // Classes visuais do campo, cada tela mantém o próprio estilo
  className?: string;
  ariaLabel?: string;
  inputRef?: React.MutableRefObject<HTMLInputElement | null>;
}

/**
 * Campo de resposta blindado contra o corretor ortográfico e a colagem, digitado no
 * teclado NORMAL do aparelho (o do sistema no celular, o físico no computador). Os
 * acentos saem do próprio teclado: segurando a letra no celular, com a tecla morta
 * (ABNT2: ´ + a = á) no computador.
 *
 * A blindagem tem três camadas, porque nenhuma sozinha basta:
 *
 * 1. NO CELULAR, O TECLADO NÃO PODE NEM OFERECER. Os atributos autocorrect/spellcheck
 *    PEDEM ao teclado que não corrija, e o Gboard ignora o pedido. O que nenhum teclado
 *    ignora é o tipo do campo: em campo de senha não há barra de sugestões, corretor,
 *    escrita deslizando o dedo nem aprendizado do que foi digitado. Então, em aparelho
 *    de tela sensível ao toque, o campo é de senha - e, como senha aparece em bolinhas,
 *    quem mostra a palavra é o espelho: o texto que o aluno lê é desenhado pelo
 *    componente, com cursor próprio, e o campo de verdade fica transparente por cima,
 *    recebendo o toque e abrindo o teclado.
 *
 * 2. O QUE O CAMPO ACEITA. Cada evento pode mudar o texto no máximo o equivalente a uma
 *    tecla (ver edicaoDeUmaTecla) e só por inserção de tecla (ver INSERCOES_DE_TECLA).
 *    Tocar numa sugestão, escrever deslizando, colar, arrastar texto, desfazer ou deixar
 *    o corretor trocar a palavra na barra de espaço muda mais que isso - tudo recusado e
 *    contado como tentativa de burla, doa a qual teclado for.
 *
 * 3. COPIAR E COLAR NÃO EXISTEM AQUI. Colar, recortar, copiar, arrastar e o menu de
 *    toque longo (de onde saem "Colar" e, no iPhone, "Substituir...") são bloqueados no
 *    campo, tanto pelo atalho do teclado quanto pelo evento.
 */
export const EntradaPalavra: React.FC<Props> = ({
  id,
  value,
  onChange,
  onBurla,
  disabled,
  maxLength = 40,
  placeholder,
  className,
  ariaLabel,
  inputRef,
}) => {
  const innerRef = useRef<HTMLInputElement | null>(null);
  const espelhoRef = useRef<HTMLDivElement | null>(null);
  const cursorRef = useRef<HTMLSpanElement | null>(null);
  const onBurlaRef = useRef(onBurla);
  useEffect(() => {
    onBurlaRef.current = onBurla;
  }, [onBurla]);

  // Teclado de vidro: campo de senha + espelho. Medido uma vez, na montagem - o aparelho
  // não troca de teclado no meio da partida.
  const [espelhado] = useState(tecladoDeVidro);
  // Posição do cursor e foco, usados só pelo espelho para desenhar o campo
  const [cursor, setCursor] = useState(0);
  const [focado, setFocado] = useState(false);

  // Composição em andamento: tecla morta no teclado físico, palavra sendo montada no
  // teclado do celular (o Gboard compõe a palavra inteira enquanto o aluno digita)
  const compondoRef = useRef(false);
  // Quando a última tecla morta (´ ` ^ ~ ¨) foi pressionada
  const teclaMortaRef = useRef(0);
  const teclaMortaRecente = () => Date.now() - teclaMortaRef.current < VALIDADE_TECLA_MORTA_MS;

  const sincronizarCursor = useCallback(() => {
    const el = innerRef.current;
    if (el) setCursor(el.selectionStart ?? el.value.length);
  }, []);

  // Guarda nativa: o SyntheticEvent do React não expõe o inputType de forma
  // confiável, então o listener de beforeinput é registrado direto no elemento
  useEffect(() => {
    const el = innerRef.current;
    if (!el) return undefined;
    const guarda = (ev: InputEvent) => {
      const tipo = ev.inputType;
      // Navegador que não informa o inputType: a guarda de edição resolve sozinha,
      // recusar aqui no escuro travaria a digitação
      if (!tipo) return;
      // Apagar não traz texto de fora - só recortar, que leva a palavra para fora
      if (tipo.startsWith('delete')) {
        if (tipo === 'deleteByCut') {
          ev.preventDefault();
          onBurlaRef.current?.('copia');
        }
        return;
      }
      if (INSERCOES_DE_TECLA.has(tipo)) return;
      ev.preventDefault();
      onBurlaRef.current?.(MOTIVO_DA_INSERCAO[tipo] ?? 'insercao-multipla');
    };
    el.addEventListener('beforeinput', guarda);
    return () => el.removeEventListener('beforeinput', guarda);
  }, []);

  const setRefs = useCallback(
    (el: HTMLInputElement | null) => {
      innerRef.current = el;
      if (inputRef) inputRef.current = el;
    },
    [inputRef],
  );

  const sanitizar = useCallback((s: string) => s.toLowerCase().replace(CARACTERES_INVALIDOS, '').slice(0, maxLength), [maxLength]);

  /**
   * Onde deixar o cursor depois que o React reescrever o campo.
   *
   * Só é usado quando a limpeza muda o texto que o navegador acabou de montar (o aluno
   * digitou um número no meio da palavra, por exemplo): aí o React reescreve o input e
   * o cursor saltaria para o fim, fazendo o resto da digitação sair fora de ordem.
   * null = o valor no DOM já é o certo, e o cursor fica onde o navegador deixou.
   */
  const cursorDesejadoRef = useRef<number | null>(null);
  useLayoutEffect(() => {
    const el = innerRef.current;
    if (el == null) return;
    const pos = cursorDesejadoRef.current;
    if (pos != null) {
      cursorDesejadoRef.current = null;
      const alvo = Math.max(0, Math.min(pos, el.value.length));
      try {
        el.setSelectionRange(alvo, alvo);
      } catch {
        // Campo de um tipo que não suporta seleção: sem reposicionar, nada quebra
      }
    }
    setCursor(el.selectionStart ?? el.value.length);
  }, [value]);

  // Espelho: mantém o cursor à vista quando a palavra é maior que a largura do campo
  useLayoutEffect(() => {
    const caixa = espelhoRef.current;
    const marca = cursorRef.current;
    if (!caixa || !marca) return;
    const margem = 12;
    const x = marca.offsetLeft;
    if (x - caixa.scrollLeft > caixa.clientWidth - margem) {
      caixa.scrollLeft = x - caixa.clientWidth + margem;
    } else if (x - caixa.scrollLeft < margem) {
      caixa.scrollLeft = Math.max(0, x - margem);
    }
  }, [value, cursor]);

  // Desfaz no DOM uma edição recusada. Sem isto o texto recusado fica visível no campo:
  // o estado não mudou, então o React não tem o que re-renderizar no input controlado.
  const recusar = (el: HTMLInputElement, tipo: TipoBurla) => {
    const cursorAtual = el.selectionStart;
    el.value = value;
    if (cursorAtual != null) {
      try {
        el.setSelectionRange(Math.min(cursorAtual, value.length), Math.min(cursorAtual, value.length));
      } catch {
        // idem: reposicionar o cursor é conforto, não requisito
      }
    }
    sincronizarCursor();
    onBurlaRef.current?.(tipo);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const el = e.target;
    const bruto = el.value;
    const compondo = compondoRef.current;
    // Com acento a meio caminho, as teclas mortas passam: limpá-las agora cancelaria a
    // composição do navegador e a vogal acentuada nunca chegaria
    const limpo = compondo ? bruto.toLowerCase().replace(CARACTERES_INVALIDOS_COMPONDO, '').slice(0, maxLength) : sanitizar(bruto);

    // Mudou mais que uma tecla, ou trocou uma letra por outra sem tecla morta por trás:
    // corretor, sugestão da barra, escrita deslizando ou preenchimento automático
    if (!edicaoDeUmaTecla(value, limpo, compondo, teclaMortaRecente())) {
      recusar(el, 'insercao-multipla');
      return;
    }
    teclaMortaRef.current = 0;

    // Durante a composição o valor vai para o estado como veio do navegador: devolver
    // outro texto aqui faria o React reescrever o input no meio da composição, e o
    // navegador a cancelaria
    if (compondo) {
      onChange(limpo);
      return;
    }
    // A limpeza mexeu no texto (caractere não aceito): o React vai reescrever o input,
    // então o cursor precisa voltar para depois do que sobrou do que foi digitado
    if (limpo !== bruto) {
      const pos = el.selectionStart ?? bruto.length;
      cursorDesejadoRef.current = Math.max(0, pos - (bruto.length - limpo.length));
    }
    onChange(limpo);
  };

  const handleCompositionStart = () => {
    compondoRef.current = true;
  };

  // Fim da composição: o acento já virou vogal acentuada e a palavra do teclado do
  // celular já foi confirmada. Agora as teclas mortas que tenham sobrado saem.
  const handleCompositionEnd = (e: React.CompositionEvent<HTMLInputElement>) => {
    compondoRef.current = false;
    const el = e.currentTarget;
    const composto = sanitizar(el.value);
    // A confirmação não pode trazer texto novo: o corretor do Android troca a palavra
    // composta pela "correta" exatamente neste momento
    if (!edicaoDeUmaTecla(value, composto, true, teclaMortaRecente())) {
      // Volta ao que estava ANTES da confirmação, já sem uma tecla morta que tenha
      // ficado pendente (durante a composição ela é deixada passar de propósito)
      const anterior = sanitizar(value);
      el.value = anterior;
      onBurlaRef.current?.('insercao-multipla');
      onChange(anterior);
      return;
    }
    onChange(composto);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    const tecla = e.key;
    // Copiar, recortar e colar pelo atalho do teclado físico
    const atalho = tecla.toLowerCase();
    if ((e.ctrlKey || e.metaKey) && (atalho === 'v' || atalho === 'c' || atalho === 'x' || atalho === 'insert')) {
      e.preventDefault();
      onBurlaRef.current?.(atalho === 'v' ? 'colagem' : 'copia');
      return;
    }
    if (e.shiftKey && tecla === 'Insert') {
      e.preventDefault();
      onBurlaRef.current?.('colagem');
      return;
    }
    // Tecla morta: o próximo caractere pode trocar o acento pela vogal acentuada
    if (tecla === 'Dead' || (tecla.length === 1 && MARCAS_MORTAS.includes(tecla))) {
      teclaMortaRef.current = Date.now();
    }
    sincronizarCursor();
  };

  const bloquear = (tipo: TipoBurla) => (e: React.SyntheticEvent) => {
    e.preventDefault();
    onBurlaRef.current?.(tipo);
  };

  return (
    <div className={`ep-wrap${espelhado ? ' ep-wrap--espelho' : ''}`}>
      {espelhado && (
        // O que o aluno lê. O campo de verdade é de senha (é o que cala o corretor do
        // teclado) e fica transparente por cima deste espelho.
        <div ref={espelhoRef} aria-hidden="true" className={`ep-espelho${disabled ? ' ep-espelho--off' : ''} ${className ?? ''}`}>
          {value.length === 0 && !focado ? (
            <span className="ep-espelho-placeholder">{placeholder}</span>
          ) : (
            <span className="ep-espelho-texto">
              {value.slice(0, cursor)}
              {focado && !disabled && <span ref={cursorRef} className="ep-espelho-cursor" />}
              {value.slice(cursor)}
            </span>
          )}
        </div>
      )}
      <input
        id={id}
        ref={setRefs}
        // Campo de senha no celular: nenhum teclado de vidro sugere, corrige ou aprende
        // o que é digitado num campo de senha. Quem mostra a palavra é o espelho.
        type={espelhado ? 'password' : 'text'}
        className={espelhado ? 'ep-captura' : className}
        value={value}
        onChange={handleChange}
        onCompositionStart={handleCompositionStart}
        onCompositionEnd={handleCompositionEnd}
        onKeyDown={handleKeyDown}
        onKeyUp={sincronizarCursor}
        onClick={sincronizarCursor}
        onSelect={sincronizarCursor}
        onFocus={() => {
          setFocado(true);
          sincronizarCursor();
        }}
        onBlur={() => setFocado(false)}
        onPaste={bloquear('colagem')}
        onCopy={bloquear('copia')}
        onCut={bloquear('copia')}
        onDrop={bloquear('arrasto')}
        onDragStart={bloquear('arrasto')}
        // Menu de toque longo: é de onde saem "Colar" e, no iPhone, "Substituir..."
        onContextMenu={e => e.preventDefault()}
        placeholder={espelhado ? undefined : placeholder}
        // Teclado normal de texto do aparelho. Os atributos abaixo pedem para ele não
        // corrigir, não sugerir e não deixar a primeira letra maiúscula - quem garante
        // mesmo são o tipo do campo e a guarda de edição.
        // No modo espelho o inputmode fica de fora de propósito: no Android ele tem
        // precedência sobre o tipo do campo, e pedir "texto comum" devolveria ao teclado
        // justamente as sugestões que o campo de senha cala.
        inputMode={espelhado ? undefined : 'text'}
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="none"
        spellCheck={false}
        data-gramm="false"
        data-enable-grammarly="false"
        maxLength={maxLength}
        disabled={disabled}
        aria-label={ariaLabel}
      />
    </div>
  );
};

export default EntradaPalavra;
