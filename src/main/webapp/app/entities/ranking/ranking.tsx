import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './ranking.reducer';

export const Ranking = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const rankingList = useAppSelector(state => state.ranking.entities);
  const loading = useAppSelector(state => state.ranking.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const order = sortState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <div>
      <h2 id="ranking-heading" data-cy="RankingHeading">
        <Translate contentKey="digitadoApp.ranking.home.title">Rankings</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.ranking.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/ranking/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.ranking.home.createLabel">Create new Ranking</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {rankingList && rankingList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.ranking.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('posicao')}>
                  <Translate contentKey="digitadoApp.ranking.posicao">Posicao</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('posicao')} />
                </th>
                <th className="hand" onClick={sort('pontuacaoTotal')}>
                  <Translate contentKey="digitadoApp.ranking.pontuacaoTotal">Pontuacao Total</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('pontuacaoTotal')} />
                </th>
                <th className="hand" onClick={sort('ultimaAtualizacao')}>
                  <Translate contentKey="digitadoApp.ranking.ultimaAtualizacao">Ultima Atualizacao</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('ultimaAtualizacao')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.ranking.sala">Sala</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.ranking.aluno">Aluno</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rankingList.map((ranking, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/ranking/${ranking.id}`} color="link" size="sm">
                      {ranking.id}
                    </Button>
                  </td>
                  <td>{ranking.posicao}</td>
                  <td>{ranking.pontuacaoTotal}</td>
                  <td>
                    {ranking.ultimaAtualizacao ? (
                      <TextFormat type="date" value={ranking.ultimaAtualizacao} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{ranking.sala ? <Link to={`/sala/${ranking.sala.id}`}>{ranking.sala.id}</Link> : ''}</td>
                  <td>{ranking.aluno ? <Link to={`/usuario/${ranking.aluno.id}`}>{ranking.aluno.id}</Link> : ''}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/ranking/${ranking.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/ranking/${ranking.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/ranking/${ranking.id}/delete`)}
                        color="danger"
                        size="sm"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="digitadoApp.ranking.home.notFound">No Rankings found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Ranking;
