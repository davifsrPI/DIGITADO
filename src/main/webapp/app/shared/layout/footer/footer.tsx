import React from 'react';
import { Link } from 'react-router-dom';

import { abrirGerenciadorCookies } from 'app/shared/cookies/cookie-consent';

// Rodapé global: acesso PERMANENTE ao gerenciador de cookies e à Política de
// Privacidade (LGPD, arts. 8º §5º e 9º).
// As páginas com tema próprio já estilizam o <footer> via body.<pagina> footer.
const Footer = () => (
  <footer className="footer d-flex justify-content-center align-items-center gap-3 py-3 flex-wrap">
    <span>DIGITADO © {new Date().getFullYear()}</span>
    <Link to="/privacidade">Política de Privacidade</Link>
    <button type="button" className="cc-footer-btn" onClick={abrirGerenciadorCookies}>
      Gerenciar Cookies
    </button>
  </footer>
);

export default Footer;
