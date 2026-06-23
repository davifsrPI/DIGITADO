import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './resposta.reducer';

export const RespostaDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const respostaEntity = useAppSelector(state => state.resposta.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="respostaDetailsHeading">
          <Translate contentKey="digitadoApp.resposta.detail.title">Resposta</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{respostaEntity.id}</dd>
          <dt>
            <span id="respostaDigitada">
              <Translate contentKey="digitadoApp.resposta.respostaDigitada">Resposta Digitada</Translate>
            </span>
          </dt>
          <dd>{respostaEntity.respostaDigitada}</dd>
          <dt>
            <span id="correta">
              <Translate contentKey="digitadoApp.resposta.correta">Correta</Translate>
            </span>
          </dt>
          <dd>{respostaEntity.correta ? 'true' : 'false'}</dd>
          <dt>
            <span id="tempoResposta">
              <Translate contentKey="digitadoApp.resposta.tempoResposta">Tempo Resposta</Translate>
            </span>
          </dt>
          <dd>{respostaEntity.tempoResposta}</dd>
          <dt>
            <span id="pontuacao">
              <Translate contentKey="digitadoApp.resposta.pontuacao">Pontuacao</Translate>
            </span>
          </dt>
          <dd>{respostaEntity.pontuacao}</dd>
          <dt>
            <span id="dataResposta">
              <Translate contentKey="digitadoApp.resposta.dataResposta">Data Resposta</Translate>
            </span>
          </dt>
          <dd>
            {respostaEntity.dataResposta ? <TextFormat value={respostaEntity.dataResposta} type="date" format={APP_DATE_FORMAT} /> : null}
          </dd>
          <dt>
            <Translate contentKey="digitadoApp.resposta.atividade">Atividade</Translate>
          </dt>
          <dd>{respostaEntity.atividade ? respostaEntity.atividade.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.resposta.aluno">Aluno</Translate>
          </dt>
          <dd>{respostaEntity.aluno ? respostaEntity.aluno.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.resposta.palavra">Palavra</Translate>
          </dt>
          <dd>{respostaEntity.palavra ? respostaEntity.palavra.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/resposta" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/resposta/${respostaEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default RespostaDetail;
