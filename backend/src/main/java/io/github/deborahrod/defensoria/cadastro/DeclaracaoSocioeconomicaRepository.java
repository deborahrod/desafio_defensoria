package io.github.deborahrod.defensoria.cadastro;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclaracaoSocioeconomicaRepository extends JpaRepository<DeclaracaoSocioeconomica, UUID> {

    List<DeclaracaoSocioeconomica> findByPessoaId(UUID pessoaId);
}
