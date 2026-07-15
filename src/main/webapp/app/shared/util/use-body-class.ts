import { useEffect } from 'react';

/**
 * Aplica uma classe CSS ao <body> enquanto a página está montada e a remove ao
 * desmontar. É o mecanismo que liga o tema escuro de cada tela (body.home-page,
 * body.lobby-page...) — os estilos por página e o rodapé (footer.scss) dependem
 * dessas classes.
 *
 * Antes deste hook, o mesmo useEffect de add/remove estava copiado em 13
 * componentes de página; agora a regra vive num lugar só.
 */
export const useBodyClass = (className: string) => {
  useEffect(() => {
    document.body.classList.add(className);
    return () => document.body.classList.remove(className);
  }, [className]);
};

export default useBodyClass;
