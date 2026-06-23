import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './ranking.reducer';

export const RankingDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const rankingEntity = useAppSelector(state => state.ranking.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="rankingDetailsHeading">
          <Translate contentKey="digitadoApp.ranking.detail.title">Ranking</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{rankingEntity.id}</dd>
          <dt>
            <span id="posicao">
              <Translate contentKey="digitadoApp.ranking.posicao">Posicao</Translate>
            </span>
          </dt>
          <dd>{rankingEntity.posicao}</dd>
          <dt>
            <span id="pontuacaoTotal">
              <Translate contentKey="digitadoApp.ranking.pontuacaoTotal">Pontuacao Total</Translate>
            </span>
          </dt>
          <dd>{rankingEntity.pontuacaoTotal}</dd>
          <dt>
            <span id="ultimaAtualizacao">
              <Translate contentKey="digitadoApp.ranking.ultimaAtualizacao">Ultima Atualizacao</Translate>
            </span>
          </dt>
          <dd>
            {rankingEntity.ultimaAtualizacao ? (
              <TextFormat value={rankingEntity.ultimaAtualizacao} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="digitadoApp.ranking.sala">Sala</Translate>
          </dt>
          <dd>{rankingEntity.sala ? rankingEntity.sala.id : ''}</dd>
          <dt>
            <Translate contentKey="digitadoApp.ranking.aluno">Aluno</Translate>
          </dt>
          <dd>{rankingEntity.aluno ? rankingEntity.aluno.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/ranking" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/ranking/${rankingEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default RankingDetail;
