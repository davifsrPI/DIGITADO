import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './lista-palavras.reducer';

export const ListaPalavras = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const listaPalavrasList = useAppSelector(state => state.listaPalavras.entities);
  const loading = useAppSelector(state => state.listaPalavras.loading);

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
      <h2 id="lista-palavras-heading" data-cy="ListaPalavrasHeading">
        <Translate contentKey="digitadoApp.listaPalavras.home.title">Lista Palavras</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.listaPalavras.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/lista-palavras/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.listaPalavras.home.createLabel">Create new Lista Palavras</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {listaPalavrasList && listaPalavrasList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.listaPalavras.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('nomeLista')}>
                  <Translate contentKey="digitadoApp.listaPalavras.nomeLista">Nome Lista</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('nomeLista')} />
                </th>
                <th className="hand" onClick={sort('descricao')}>
                  <Translate contentKey="digitadoApp.listaPalavras.descricao">Descricao</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('descricao')} />
                </th>
                <th className="hand" onClick={sort('ativo')}>
                  <Translate contentKey="digitadoApp.listaPalavras.ativo">Ativo</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('ativo')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.listaPalavras.palavras">Palavras</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.listaPalavras.professor">Professor</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {listaPalavrasList.map((listaPalavras, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/lista-palavras/${listaPalavras.id}`} color="link" size="sm">
                      {listaPalavras.id}
                    </Button>
                  </td>
                  <td>{listaPalavras.nomeLista}</td>
                  <td>{listaPalavras.descricao}</td>
                  <td>{listaPalavras.ativo ? 'true' : 'false'}</td>
                  <td>
                    {listaPalavras.palavras
                      ? listaPalavras.palavras.map((val, j) => (
                          <span key={j}>
                            <Link to={`/palavra/${val.id}`}>{val.id}</Link>
                            {j === listaPalavras.palavras.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td>
                    {listaPalavras.professor ? <Link to={`/usuario/${listaPalavras.professor.id}`}>{listaPalavras.professor.id}</Link> : ''}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/lista-palavras/${listaPalavras.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/lista-palavras/${listaPalavras.id}/edit`}
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
                        onClick={() => (window.location.href = `/lista-palavras/${listaPalavras.id}/delete`)}
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
              <Translate contentKey="digitadoApp.listaPalavras.home.notFound">No Lista Palavras found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default ListaPalavras;
