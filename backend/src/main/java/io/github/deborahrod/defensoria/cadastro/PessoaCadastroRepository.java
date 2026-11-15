package io.github.deborahrod.defensoria.cadastro;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PessoaCadastroRepository extends JpaRepository<PessoaCadastro, UUID> {
}
