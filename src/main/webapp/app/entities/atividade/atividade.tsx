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

import { getEntities } from './atividade.reducer';

export const Atividade = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const atividadeList = useAppSelector(state => state.atividade.entities);
  const loading = useAppSelector(state => state.atividade.loading);

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
      <h2 id="atividade-heading" data-cy="AtividadeHeading">
        <Translate contentKey="digitadoApp.atividade.home.title">Atividades</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.atividade.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/atividade/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.atividade.home.createLabel">Create new Atividade</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {atividadeList && atividadeList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.atividade.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('titulo')}>
                  <Translate contentKey="digitadoApp.atividade.titulo">Titulo</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('titulo')} />
                </th>
                <th className="hand" onClick={sort('modo')}>
                  <Translate contentKey="digitadoApp.atividade.modo">Modo</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('modo')} />
                </th>
                <th className="hand" onClick={sort('dataInicio')}>
                  <Translate contentKey="digitadoApp.atividade.dataInicio">Data Inicio</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('dataInicio')} />
                </th>
                <th className="hand" onClick={sort('dataFim')}>
                  <Translate contentKey="digitadoApp.atividade.dataFim">Data Fim</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('dataFim')} />
                </th>
                <th className="hand" onClick={sort('tempoLimite')}>
                  <Translate contentKey="digitadoApp.atividade.tempoLimite">Tempo Limite</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tempoLimite')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="digitadoApp.atividade.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.atividade.sala">Sala</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.atividade.lista">Lista</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {atividadeList.map((atividade, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/atividade/${atividade.id}`} color="link" size="sm">
                      {atividade.id}
                    </Button>
                  </td>
                  <td>{atividade.titulo}</td>
                  <td>
                    <Translate contentKey={`digitadoApp.ModoAtividade.${atividade.modo}`} />
                  </td>
                  <td>{atividade.dataInicio ? <TextFormat type="date" value={atividade.dataInicio} format={APP_DATE_FORMAT} /> : null}</td>
                  <td>{atividade.dataFim ? <TextFormat type="date" value={atividade.dataFim} format={APP_DATE_FORMAT} /> : null}</td>
                  <td>{atividade.tempoLimite}</td>
                  <td>
                    <Translate contentKey={`digitadoApp.StatusAtividade.${atividade.status}`} />
                  </td>
                  <td>{atividade.sala ? <Link to={`/sala/${atividade.sala.id}`}>{atividade.sala.id}</Link> : ''}</td>
                  <td>{atividade.lista ? <Link to={`/lista-palavras/${atividade.lista.id}`}>{atividade.lista.id}</Link> : ''}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/atividade/${atividade.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/atividade/${atividade.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/atividade/${atividade.id}/delete`)}
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
              <Translate contentKey="digitadoApp.atividade.home.notFound">No Atividades found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Atividade;
