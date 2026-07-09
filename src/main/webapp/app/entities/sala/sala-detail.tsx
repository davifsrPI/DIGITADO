import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './sala.reducer';

export const SalaDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const salaEntity = useAppSelector(state => state.sala.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="salaDetailsHeading">
          <Translate contentKey="digitadoApp.sala.detail.title">Sala</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="codigo">
              <Translate contentKey="digitadoApp.sala.codigo">Codigo</Translate>
            </span>
          </dt>
          <dd>{salaEntity.codigo}</dd>
          <dt>
            <span id="nome">
              <Translate contentKey="digitadoApp.sala.nome">Nome</Translate>
            </span>
          </dt>
          <dd>{salaEntity.nome}</dd>
          <dt>
            <span id="descricao">
              <Translate contentKey="digitadoApp.sala.descricao">Descricao</Translate>
            </span>
          </dt>
          <dd>{salaEntity.descricao}</dd>
          <dt>
            <span id="ativo">
              <Translate contentKey="digitadoApp.sala.ativo">Ativo</Translate>
            </span>
          </dt>
          <dd>{salaEntity.ativo ? 'true' : 'false'}</dd>
          <dt>
            <Translate contentKey="digitadoApp.sala.professor">Professor</Translate>
          </dt>
          <dd>{salaEntity.professor ? salaEntity.professor.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.sala.alunos">Alunos</Translate>
          </dt>
          <dd>
            {salaEntity.alunos
              ? salaEntity.alunos.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {salaEntity.alunos && i === salaEntity.alunos.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/sala" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/sala/${salaEntity.codigo}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default SalaDetail;
