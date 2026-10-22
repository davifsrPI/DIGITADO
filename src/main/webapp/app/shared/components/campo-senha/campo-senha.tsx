import './campo-senha.scss';

import React, { useLayoutEffect, useRef, useState } from 'react';
import { FormGroup, Label } from 'reactstrap';
import { ValidatedInput } from 'react-jhipster';

/**
 * O "olhinho" da senha: um botão dentro do campo que alterna entre esconder e
 * mostrar o que foi digitado.
 *
 * Num laboratório de escola, com teclado de layout trocado e aluno digitando
 * devagar, errar a senha sem conseguir ver o que digitou é a causa mais comum de
 * "não consigo entrar" - e cada tentativa errada conta para o bloqueio do login.
 */

// Olho aberto (mostrar) e olho riscado (esconder), em SVG: emoji muda de sistema para sistema
const IconeOlho: React.FC<{ aberto: boolean }> = ({ aberto }) => (
  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
    <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" strokeLinecap="round" strokeLinejoin="round" />
    <circle cx="12" cy="12" r="3" />
    {!aberto && <line x1="3" y1="3" x2="21" y2="21" strokeLinecap="round" />}
  </svg>
);

const BotaoOlho: React.FC<{ visivel: boolean; alternar: () => void; altura?: number }> = ({ visivel, alternar, altura }) => (
  <button
    type="button"
    className="campo-senha-olho"
    onClick={alternar}
    // mousedown sem foco: o cursor continua no campo, a pessoa segue digitando
    onMouseDown={e => e.preventDefault()}
    aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
    aria-pressed={visivel}
    title={visivel ? 'Ocultar senha' : 'Mostrar senha'}
    style={altura ? { height: altura } : undefined}
  >
    <IconeOlho aberto={!visivel} />
  </button>
);

// Altura do campo, medida: o botão fica centrado nele e não na mensagem de erro
// que aparece logo abaixo (os formulários têm estilos de campo diferentes entre si)
const useAlturaDoCampo = () => {
  const ref = useRef<HTMLDivElement>(null);
  const [altura, setAltura] = useState<number | undefined>(undefined);
  useLayoutEffect(() => {
    const campo = ref.current?.querySelector('input');
    if (campo && campo.offsetHeight > 0) setAltura(campo.offsetHeight);
  });
  return { ref, altura };
};

/** Campo de senha simples (fora do ValidatedForm), com o olhinho. */
export const CampoSenha: React.FC<React.InputHTMLAttributes<HTMLInputElement>> = props => {
  const [visivel, setVisivel] = useState(false);
  const { ref, altura } = useAlturaDoCampo();
  return (
    <div className="campo-senha" ref={ref}>
      <input {...props} type={visivel ? 'text' : 'password'} />
      <BotaoOlho visivel={visivel} alternar={() => setVisivel(v => !v)} altura={altura} />
    </div>
  );
};

/**
 * Campo de senha DENTRO de um ValidatedForm, com o olhinho.
 *
 * O ValidatedForm só registra (validação, erro, touched) os filhos DIRETOS cujo
 * displayName é ValidatedField - por isso este componente se apresenta com esse
 * nome e repassa ao ValidatedInput tudo o que o formulário injetou.
 */
export const CampoSenhaValidado = (props: any) => {
  const [visivel, setVisivel] = useState(false);
  const { ref, altura } = useAlturaDoCampo();
  const { name, id = name, label, className, labelClass, type, ...resto } = props;
  return (
    <FormGroup className={className}>
      {label && (
        <Label id={`${name}Label`} for={id} className={labelClass}>
          {label}
        </Label>
      )}
      <div className="campo-senha" ref={ref}>
        <ValidatedInput name={name} id={id} {...resto} type={visivel ? 'text' : 'password'} />
        <BotaoOlho visivel={visivel} alternar={() => setVisivel(v => !v)} altura={altura} />
      </div>
    </FormGroup>
  );
};
CampoSenhaValidado.displayName = 'ValidatedField';

export default CampoSenha;
