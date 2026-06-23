import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './usuario-conquista.reducer';

export const UsuarioConquistaDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const usuarioConquistaEntity = useAppSelector(state => state.usuarioConquista.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="usuarioConquistaDetailsHeading">
          <Translate contentKey="digitadoApp.usuarioConquista.detail.title">UsuarioConquista</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{usuarioConquistaEntity.id}</dd>
          <dt>
            <span id="dataConquista">
              <Translate contentKey="digitadoApp.usuarioConquista.dataConquista">Data Conquista</Translate>
            </span>
          </dt>
          <dd>
            {usuarioConquistaEntity.dataConquista ? (
              <TextFormat value={usuarioConquistaEntity.dataConquista} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="progresso">
              <Translate contentKey="digitadoApp.usuarioConquista.progresso">Progresso</Translate>
            </span>
          </dt>
          <dd>{usuarioConquistaEntity.progresso}</dd>
          <dt>
            <span id="concluida">
              <Translate contentKey="digitadoApp.usuarioConquista.concluida">Concluida</Translate>
            </span>
          </dt>
          <dd>{usuarioConquistaEntity.concluida ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="digitadoApp.usuarioConquista.aluno">Aluno</Translate>
          </dt>
          <dd>{usuarioConquistaEntity.aluno ? usuarioConquistaEntity.aluno.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.usuarioConquista.conquista">Conquista</Translate>
          </dt>
          <dd>{usuarioConquistaEntity.conquista ? usuarioConquistaEntity.conquista.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/usuario-conquista" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/usuario-conquista/${usuarioConquistaEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default UsuarioConquistaDetail;
