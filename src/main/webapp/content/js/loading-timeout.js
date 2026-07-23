/* global window, document */
// Mostra o guia de solução de problemas se o app não montar em 4 segundos.
// Vive em arquivo próprio (não inline no index.html) para permitir uma
// Content-Security-Policy de produção com script-src 'self', sem 'unsafe-inline'.
window.onload = function () {
  setTimeout(showError, 4000);
};
function showError() {
  const errorElm = document.getElementById('jhipster-error');
  if (errorElm?.style) {
    errorElm.style.display = 'block';
  }
}
