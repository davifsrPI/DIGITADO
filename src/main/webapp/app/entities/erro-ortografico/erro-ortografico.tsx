import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './erro-ortografico.reducer';

export const ErroOrtografico = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const erroOrtograficoList = useAppSelector(state => state.erroOrtografico.entities);
  const loading = useAppSelector(state => state.erroOrtografico.loading);

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
      <h2 id="erro-ortografico-heading" data-cy="ErroOrtograficoHeading">
        <Translate contentKey="digitadoApp.erroOrtografico.home.title">Erro Ortograficos</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.erroOrtografico.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/erro-ortografico/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.erroOrtografico.home.createLabel">Create new Erro Ortografico</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {erroOrtograficoList && erroOrtograficoList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.erroOrtografico.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('tipoErro')}>
                  <Translate contentKey="digitadoApp.erroOrtografico.tipoErro">Tipo Erro</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tipoErro')} />
                </th>
                <th className="hand" onClick={sort('descricao')}>
                  <Translate contentKey="digitadoApp.erroOrtografico.descricao">Descricao</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('descricao')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.erroOrtografico.resposta">Resposta</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {erroOrtograficoList.map((erroOrtografico, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/erro-ortografico/${erroOrtografico.id}`} color="link" size="sm">
                      {erroOrtografico.id}
                    </Button>
                  </td>
                  <td>
                    <Translate contentKey={`digitadoApp.TipoErro.${erroOrtografico.tipoErro}`} />
                  </td>
                  <td>{erroOrtografico.descricao}</td>
                  <td>
                    {erroOrtografico.resposta ? (
                      <Link to={`/resposta/${erroOrtografico.resposta.id}`}>{erroOrtografico.resposta.id}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/erro-ortografico/${erroOrtografico.id}`}
                        color="info"
                        size="sm"
                        data-cy="entityDetailsButton"
                      >
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/erro-ortografico/${erroOrtografico.id}/edit`}
                        color="primary"
                        size="sm"
                        data-cy="entityEditButton"
                      >
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/erro-ortografico/${erroOrtografico.id}/delete`)}
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
              <Translate contentKey="digitadoApp.erroOrtografico.home.notFound">No Erro Ortograficos found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default ErroOrtografico;
