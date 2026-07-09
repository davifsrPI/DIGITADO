import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './atividade.reducer';

export const AtividadeDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const atividadeEntity = useAppSelector(state => state.atividade.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="atividadeDetailsHeading">
          <Translate contentKey="digitadoApp.atividade.detail.title">Atividade</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.id}</dd>
          <dt>
            <span id="titulo">
              <Translate contentKey="digitadoApp.atividade.titulo">Titulo</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.titulo}</dd>
          <dt>
            <span id="modo">
              <Translate contentKey="digitadoApp.atividade.modo">Modo</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.modo}</dd>
          <dt>
            <span id="dataInicio">
              <Translate contentKey="digitadoApp.atividade.dataInicio">Data Inicio</Translate>
            </span>
          </dt>
          <dd>
            {atividadeEntity.dataInicio ? <TextFormat value={atividadeEntity.dataInicio} type="date" format={APP_DATE_FORMAT} /> : null}
          </dd>
          <dt>
            <span id="dataFim">
              <Translate contentKey="digitadoApp.atividade.dataFim">Data Fim</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.dataFim ? <TextFormat value={atividadeEntity.dataFim} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <span id="tempoLimite">
              <Translate contentKey="digitadoApp.atividade.tempoLimite">Tempo Limite</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.tempoLimite}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="digitadoApp.atividade.status">Status</Translate>
            </span>
          </dt>
          <dd>{atividadeEntity.status}</dd>
          <dt>
            <Translate contentKey="digitadoApp.atividade.sala">Sala</Translate>
          </dt>
          <dd>{atividadeEntity.sala ? atividadeEntity.sala.codigo : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.atividade.lista">Lista</Translate>
          </dt>
          <dd>{atividadeEntity.lista ? atividadeEntity.lista.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/atividade" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/atividade/${atividadeEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default AtividadeDetail;
