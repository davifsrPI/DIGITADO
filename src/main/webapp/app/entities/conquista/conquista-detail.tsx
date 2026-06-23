import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './conquista.reducer';

export const ConquistaDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const conquistaEntity = useAppSelector(state => state.conquista.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="conquistaDetailsHeading">
          <Translate contentKey="digitadoApp.conquista.detail.title">Conquista</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{conquistaEntity.id}</dd>
          <dt>
            <span id="nome">
              <Translate contentKey="digitadoApp.conquista.nome">Nome</Translate>
            </span>
          </dt>
          <dd>{conquistaEntity.nome}</dd>
          <dt>
            <span id="descricao">
              <Translate contentKey="digitadoApp.conquista.descricao">Descricao</Translate>
            </span>
          </dt>
          <dd>{conquistaEntity.descricao}</dd>
          <dt>
            <span id="xpRecompensa">
              <Translate contentKey="digitadoApp.conquista.xpRecompensa">Xp Recompensa</Translate>
            </span>
          </dt>
          <dd>{conquistaEntity.xpRecompensa}</dd>
        </dl>
        <Button tag={Link} to="/conquista" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/conquista/${conquistaEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ConquistaDetail;
