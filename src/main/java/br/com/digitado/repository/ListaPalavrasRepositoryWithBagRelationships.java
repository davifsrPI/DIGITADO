package br.com.digitado.repository;

import br.com.digitado.domain.ListaPalavras;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface ListaPalavrasRepositoryWithBagRelationships {
    Optional<ListaPalavras> fetchBagRelationships(Optional<ListaPalavras> listaPalavras);

    List<ListaPalavras> fetchBagRelationships(List<ListaPalavras> listaPalavras);

    Page<ListaPalavras> fetchBagRelationships(Page<ListaPalavras> listaPalavras);
}
