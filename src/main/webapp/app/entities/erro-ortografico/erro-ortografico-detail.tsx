import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './erro-ortografico.reducer';

export const ErroOrtograficoDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const erroOrtograficoEntity = useAppSelector(state => state.erroOrtografico.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="erroOrtograficoDetailsHeading">
          <Translate contentKey="digitadoApp.erroOrtografico.detail.title">ErroOrtografico</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{erroOrtograficoEntity.id}</dd>
          <dt>
            <span id="tipoErro">
              <Translate contentKey="digitadoApp.erroOrtografico.tipoErro">Tipo Erro</Translate>
            </span>
          </dt>
          <dd>{erroOrtograficoEntity.tipoErro}</dd>
          <dt>
            <span id="descricao">
              <Translate contentKey="digitadoApp.erroOrtografico.descricao">Descricao</Translate>
            </span>
          </dt>
          <dd>{erroOrtograficoEntity.descricao}</dd>
          <dt>
            <Translate contentKey="digitadoApp.erroOrtografico.resposta">Resposta</Translate>
          </dt>
          <dd>{erroOrtograficoEntity.resposta ? erroOrtograficoEntity.resposta.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/erro-ortografico" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/erro-ortografico/${erroOrtograficoEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ErroOrtograficoDetail;
