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

import { getEntities } from './resposta.reducer';

export const Resposta = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const respostaList = useAppSelector(state => state.resposta.entities);
  const loading = useAppSelector(state => state.resposta.loading);

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
      <h2 id="resposta-heading" data-cy="RespostaHeading">
        <Translate contentKey="digitadoApp.resposta.home.title">Respostas</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.resposta.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/resposta/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.resposta.home.createLabel">Create new Resposta</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {respostaList && respostaList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.resposta.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('respostaDigitada')}>
                  <Translate contentKey="digitadoApp.resposta.respostaDigitada">Resposta Digitada</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('respostaDigitada')} />
                </th>
                <th className="hand" onClick={sort('correta')}>
                  <Translate contentKey="digitadoApp.resposta.correta">Correta</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('correta')} />
                </th>
                <th className="hand" onClick={sort('tempoResposta')}>
                  <Translate contentKey="digitadoApp.resposta.tempoResposta">Tempo Resposta</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tempoResposta')} />
                </th>
                <th className="hand" onClick={sort('pontuacao')}>
                  <Translate contentKey="digitadoApp.resposta.pontuacao">Pontuacao</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('pontuacao')} />
                </th>
                <th className="hand" onClick={sort('dataResposta')}>
                  <Translate contentKey="digitadoApp.resposta.dataResposta">Data Resposta</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('dataResposta')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.resposta.atividade">Atividade</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.resposta.aluno">Aluno</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.resposta.palavra">Palavra</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {respostaList.map((resposta, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/resposta/${resposta.id}`} color="link" size="sm">
                      {resposta.id}
                    </Button>
                  </td>
                  <td>{resposta.respostaDigitada}</td>
                  <td>{resposta.correta ? 'true' : 'false'}</td>
                  <td>{resposta.tempoResposta}</td>
                  <td>{resposta.pontuacao}</td>
                  <td>
                    {resposta.dataResposta ? <TextFormat type="date" value={resposta.dataResposta} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>{resposta.atividade ? <Link to={`/atividade/${resposta.atividade.id}`}>{resposta.atividade.id}</Link> : ''}</td>
                  <td>{resposta.aluno ? <Link to={`/usuario/${resposta.aluno.id}`}>{resposta.aluno.id}</Link> : ''}</td>
                  <td>{resposta.palavra ? <Link to={`/palavra/${resposta.palavra.id}`}>{resposta.palavra.id}</Link> : ''}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/resposta/${resposta.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/resposta/${resposta.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/resposta/${resposta.id}/delete`)}
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
              <Translate contentKey="digitadoApp.resposta.home.notFound">No Respostas found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Resposta;
