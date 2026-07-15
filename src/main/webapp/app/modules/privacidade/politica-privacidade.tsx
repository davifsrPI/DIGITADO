import './politica-privacidade.scss';

import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { abrirGerenciadorCookies } from 'app/shared/cookies/cookie-consent';
import { useBodyClass } from 'app/shared/util/use-body-class';

/**
 * Política de Privacidade (LGPD art. 9º — transparência).
 * Página PÚBLICA: acessível sem login, linkada no rodapé, no banner de cookies
 * e no formulário de cadastro.
 *
 * Os trechos entre [COLCHETES] devem ser preenchidos pelo responsável legal
 * antes de publicar — não invente esses dados.
 */
export const PoliticaPrivacidade = () => {
  useBodyClass('pp-page');

  return (
    <div className="pp-wrapper">
      <div className="pp-card">
        <h1>Política de Privacidade</h1>
        <p className="pp-atualizacao">Última atualização: [DATA DA PUBLICAÇÃO]</p>

        <h2>1. Quem somos (controlador)</h2>
        <p>
          O DIGITADO é operado por [RAZÃO SOCIAL / NOME COMPLETO], [CNPJ/CPF], com sede em [ENDEREÇO]. Para qualquer assunto de privacidade,
          contate o encarregado de dados (DPO): [NOME E E-MAIL DO ENCARREGADO].
        </p>

        <h2>2. Quais dados coletamos e para quê</h2>
        <ul>
          <li>
            <strong>Cadastro</strong> — nome, sobrenome, e-mail, login e senha (guardada apenas como hash criptográfico, nunca em texto):
            usados exclusivamente para criar e autenticar a sua conta.
          </li>
          <li>
            <strong>Desempenho no jogo</strong> — respostas digitadas, acertos, erros ortográficos, pontuação, ranking e conquistas: usados
            para as funcionalidades pedagógicas da plataforma (placar, evolução, conquistas).
          </li>
          <li>
            <strong>Palavra do dia</strong> — registro diário de tentativa (acertou/errou) vinculado ao seu login por até 30 dias; depois
            disso o vínculo é anonimizado automaticamente e resta apenas a estatística agregada.
          </li>
          <li>
            <strong>Endereço IP</strong> — usado somente para segurança (limite de requisições contra ataques), com base no legítimo
            interesse (art. 7º, IX).
          </li>
        </ul>
        <p>Não coletamos dados sensíveis (art. 5º, II) e não usamos os seus dados para publicidade.</p>

        <h2>3. Cookies</h2>
        <p>
          Usamos cookies essenciais (login e segurança) e, somente com o seu consentimento, cookies opcionais de preferências, análise e
          marketing. Você decide no primeiro acesso e pode mudar quando quiser em{' '}
          <button type="button" className="cc-footer-btn" onClick={abrirGerenciadorCookies}>
            Gerenciar Cookies
          </button>
          .
        </p>

        <h2>4. Compartilhamento</h2>
        <p>
          Não vendemos nem compartilhamos seus dados pessoais com terceiros. Não há serviços de análise ou publicidade ativos; se algum for
          adicionado no futuro, só carregará após o seu consentimento e esta política será atualizada.
        </p>

        <h2>5. Armazenamento e segurança</h2>
        <p>
          Os dados ficam em banco de dados próprio, com senha protegida por hash bcrypt, acesso autenticado por token, limite de requisições
          contra ataques e registro auditável de erros. Retenção: os dados da conta são mantidos enquanto ela existir.
        </p>

        <h2>6. Seus direitos (art. 18)</h2>
        <ul>
          <li>
            <strong>Acesso e correção</strong> — na tela <Link to="/account/settings">Configurações</Link>;
          </li>
          <li>
            <strong>Portabilidade</strong> — baixe todos os seus dados em &quot;Configurações → Meus dados (LGPD) → Baixar meus dados&quot;;
          </li>
          <li>
            <strong>Exclusão</strong> — apague sua conta e todos os dados pessoais em &quot;Configurações → Meus dados (LGPD) → Excluir
            minha conta&quot; (irreversível; exige sua senha);
          </li>
          <li>
            <strong>Revogação do consentimento de cookies</strong> — em &quot;Gerenciar Cookies&quot;, no rodapé;
          </li>
          <li>
            <strong>Demais direitos</strong> (informação, oposição, reclamação à ANPD) — contate o encarregado: [E-MAIL DO ENCARREGADO].
          </li>
        </ul>

        <h2>7. Crianças e adolescentes</h2>
        <p>
          [DEFINIR CONFORME O MODELO DE USO: se a plataforma for usada por menores de idade, descrever aqui como é coletado o consentimento
          específico de um dos pais ou responsável (art. 14) ou o papel da instituição de ensino como controladora.]
        </p>

        <h2>8. Alterações desta política</h2>
        <p>Mudanças relevantes serão comunicadas nesta página e, quando afetarem cookies, o banner de consentimento será reapresentado.</p>
      </div>
    </div>
  );
};

export default PoliticaPrivacidade;
