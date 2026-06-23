import usuario from 'app/entities/usuario/usuario.reducer';
import sala from 'app/entities/sala/sala.reducer';
import listaPalavras from 'app/entities/lista-palavras/lista-palavras.reducer';
import palavra from 'app/entities/palavra/palavra.reducer';
import atividade from 'app/entities/atividade/atividade.reducer';
import resposta from 'app/entities/resposta/resposta.reducer';
import erroOrtografico from 'app/entities/erro-ortografico/erro-ortografico.reducer';
import ranking from 'app/entities/ranking/ranking.reducer';
import conquista from 'app/entities/conquista/conquista.reducer';
import usuarioConquista from 'app/entities/usuario-conquista/usuario-conquista.reducer';
/* jhipster-needle-add-reducer-import - JHipster will add reducer here */

const entitiesReducers = {
  usuario,
  sala,
  listaPalavras,
  palavra,
  atividade,
  resposta,
  erroOrtografico,
  ranking,
  conquista,
  usuarioConquista,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default entitiesReducers;
