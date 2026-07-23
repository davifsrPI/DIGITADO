import './cookie-consent.scss';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  CategoriaOpcional,
  EVENTO_ABRIR_GERENCIADOR,
  aceitarTodos,
  aplicarConsentimento,
  obterConsentimento,
  recusarOpcionais,
  salvarConsentimento,
} from './cookie-consent';

// Descrições honestas de cada categoria - sem juridiquês e sem minimizar o rastreamento
const CATEGORIAS = [
  {
    id: 'essenciais' as const,
    nome: 'Essenciais',
    fixa: true,
    descricao: 'Necessários para o site funcionar: sessão de login, segurança e o registro desta sua escolha. Não podem ser desativados.',
  },
  {
    id: 'preferencias' as const,
    nome: 'Preferências',
    fixa: false,
    descricao: 'Lembram suas escolhas de conveniência, como volume do áudio das palavras e configurações de exibição.',
  },
  {
    id: 'analiticos' as const,
    nome: 'Analíticos',
    fixa: false,
    descricao: 'Nos ajudam a entender como o site é usado (páginas visitadas, erros) para melhorá-lo. Os dados são agregados.',
  },
  {
    id: 'marketing' as const,
    nome: 'Marketing',
    fixa: false,
    descricao: 'Usados por parceiros para medir campanhas e exibir conteúdo relevante em outras plataformas.',
  },
];

/**
 * Banner de consentimento de cookies (LGPD) + modal "Gerenciar Cookies".
 *
 * - Aparece só na primeira visita (ou quando a versão da política muda);
 * - "Aceitar todos" e "Recusar opcionais" têm o MESMO destaque visual (sem dark pattern);
 * - O modal pode ser reaberto a qualquer momento via abrirGerenciadorCookies()
 *   (usado pelo botão permanente no rodapé);
 * - Acessível: role=dialog, aria-modal, fechamento por Esc, foco gerenciado,
 *   animações respeitam prefers-reduced-motion (no SCSS).
 */
export const CookieConsentBanner = () => {
  const [mostrarBanner, setMostrarBanner] = useState(false);
  const [mostrarModal, setMostrarModal] = useState(false);
  const [opcionais, setOpcionais] = useState<Record<CategoriaOpcional, boolean>>({
    preferencias: false,
    analiticos: false,
    marketing: false,
  });
  const tituloModalRef = useRef<HTMLHeadingElement>(null);

  // Primeira visita: mostra o banner. Visita recorrente: aplica o consentimento
  // salvo (carrega os scripts já autorizados) sem incomodar o usuário.
  useEffect(() => {
    const salvo = obterConsentimento();
    if (salvo) {
      aplicarConsentimento(salvo);
    } else {
      setMostrarBanner(true);
    }
  }, []);

  // Botão "Gerenciar Cookies" (rodapé ou qualquer tela) reabre o modal
  useEffect(() => {
    const abrir = () => {
      const salvo = obterConsentimento();
      if (salvo) {
        setOpcionais({
          preferencias: salvo.categorias.preferencias,
          analiticos: salvo.categorias.analiticos,
          marketing: salvo.categorias.marketing,
        });
      }
      setMostrarModal(true);
    };
    window.addEventListener(EVENTO_ABRIR_GERENCIADOR, abrir);
    return () => window.removeEventListener(EVENTO_ABRIR_GERENCIADOR, abrir);
  }, []);

  // Acessibilidade do modal: foco no título ao abrir e fechamento com Esc
  useEffect(() => {
    if (!mostrarModal) return undefined;
    tituloModalRef.current?.focus();
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMostrarModal(false);
    };
    window.addEventListener('keydown', aoTeclar);
    return () => window.removeEventListener('keydown', aoTeclar);
  }, [mostrarModal]);

  const concluir = useCallback(() => {
    setMostrarBanner(false);
    setMostrarModal(false);
  }, []);

  const handleAceitarTodos = () => {
    aceitarTodos();
    concluir();
  };

  const handleRecusar = () => {
    recusarOpcionais();
    concluir();
  };

  const handleSalvarPersonalizado = () => {
    salvarConsentimento(opcionais);
    concluir();
  };

  const alternar = (id: CategoriaOpcional) => setOpcionais(prev => ({ ...prev, [id]: !prev[id] }));

  if (!mostrarBanner && !mostrarModal) return null;

  return (
    <>
      {/* ── Banner da primeira visita ─────────────────────────────── */}
      {mostrarBanner && !mostrarModal && (
        <section className="cc-banner" role="region" aria-label="Consentimento de cookies">
          <div className="cc-banner-texto">
            <strong>🍪 Este site usa cookies</strong>
            <p>
              Usamos cookies essenciais para o funcionamento do site e, com a sua permissão, cookies opcionais para lembrar preferências e
              entender o uso da plataforma. Saiba mais na <Link to="/privacidade">Política de Privacidade</Link>. Você pode mudar de ideia a
              qualquer momento em &quot;Gerenciar Cookies&quot;, no rodapé.
            </p>
          </div>
          <div className="cc-banner-acoes">
            {/* Mesma classe visual nos dois botões de decisão: recusar é tão fácil quanto aceitar */}
            <button type="button" className="cc-btn cc-btn-decisao" onClick={handleRecusar}>
              Recusar opcionais
            </button>
            <button type="button" className="cc-btn cc-btn-decisao" onClick={handleAceitarTodos}>
              Aceitar todos
            </button>
            <button type="button" className="cc-btn cc-btn-link" onClick={() => setMostrarModal(true)}>
              Personalizar
            </button>
          </div>
        </section>
      )}

      {/* ── Modal de personalização / gerenciamento ───────────────── */}
      {mostrarModal && (
        <div className="cc-modal-overlay" onClick={() => setMostrarModal(false)}>
          <div className="cc-modal" role="dialog" aria-modal="true" aria-labelledby="cc-modal-titulo" onClick={e => e.stopPropagation()}>
            <div className="cc-modal-header">
              <h2 id="cc-modal-titulo" ref={tituloModalRef} tabIndex={-1}>
                Preferências de cookies
              </h2>
              <button type="button" className="cc-fechar" onClick={() => setMostrarModal(false)} aria-label="Fechar sem salvar">
                ✕
              </button>
            </div>

            <p className="cc-modal-sub">
              Escolha quais categorias autorizar. Os cookies essenciais ficam sempre ativos porque o site não funciona sem eles.
            </p>

            <div className="cc-categorias">
              {CATEGORIAS.map(cat => {
                const ligado = cat.fixa || opcionais[cat.id as CategoriaOpcional];
                return (
                  <div key={cat.id} className="cc-categoria">
                    <div className="cc-categoria-info">
                      <span className="cc-categoria-nome">
                        {cat.nome}
                        {cat.fixa && <span className="cc-sempre-ativo">sempre ativos</span>}
                      </span>
                      <p className="cc-categoria-desc">{cat.descricao}</p>
                    </div>
                    <button
                      type="button"
                      role="switch"
                      aria-checked={ligado}
                      aria-label={`Cookies de ${cat.nome}`}
                      className={`cc-switch${ligado ? ' cc-switch--on' : ''}${cat.fixa ? ' cc-switch--fixo' : ''}`}
                      disabled={cat.fixa}
                      onClick={() => !cat.fixa && alternar(cat.id as CategoriaOpcional)}
                    >
                      <span className="cc-switch-thumb" />
                    </button>
                  </div>
                );
              })}
            </div>

            <div className="cc-modal-acoes">
              <button type="button" className="cc-btn cc-btn-decisao" onClick={handleRecusar}>
                Recusar opcionais
              </button>
              <button type="button" className="cc-btn cc-btn-decisao" onClick={handleAceitarTodos}>
                Aceitar todos
              </button>
              <button type="button" className="cc-btn cc-btn-salvar" onClick={handleSalvarPersonalizado}>
                Salvar preferências
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default CookieConsentBanner;
