import './tela-carregamento.scss';
import React from 'react';

// Molde de tela de carregamento do DIGITADO.
//
// Segue o estilo visual do projeto (fundo #0f172a, shapes borradas, vidro fosco,
// Poppins) com as teclas de "DIGITADO" acendendo em onda, como se alguém digitasse.
//
// Uso:
//   <TelaCarregamento />                                        → tela cheia, texto padrão
//   <TelaCarregamento mensagem="Preparando o duelo..." />       → tela cheia, texto próprio
//   <TelaCarregamento embutido mensagem="Buscando salas..." />  → dentro de um card/seção,
//                                                                 sem cobrir a página inteira
interface TelaCarregamentoProps {
  /** Texto exibido abaixo da animação (padrão: "Carregando...") */
  mensagem?: string;
  /** Renderiza dentro do fluxo da página (para cards/listas) em vez de cobrir a tela toda */
  embutido?: boolean;
}

const LETRAS = ['D', 'I', 'G', 'I', 'T', 'A', 'D', 'O'];

export const TelaCarregamento = ({ mensagem = 'Carregando...', embutido = false }: TelaCarregamentoProps) => (
  <div className={`tc-wrapper${embutido ? ' tc-wrapper--embutido' : ''}`} role="status" aria-live="polite">
    {/* Shapes de fundo só na versão tela cheia — embutido herda o fundo de quem o contém */}
    {!embutido && (
      <div className="tc-bg">
        <div className="tc-shape one" />
        <div className="tc-shape two" />
        <div className="tc-shape three" />
      </div>
    )}

    <div className="tc-content">
      {/* Teclado: cada tecla acende em sequência, como uma digitação */}
      <div className="tc-teclado">
        {LETRAS.map((letra, i) => (
          <span key={i} className="tc-tecla" style={{ animationDelay: `${i * 0.12}s` }}>
            {letra}
          </span>
        ))}
      </div>

      <p className="tc-texto">{mensagem}</p>

      <div className="tc-barra">
        <div className="tc-barra-fill" />
      </div>
    </div>
  </div>
);

export default TelaCarregamento;
