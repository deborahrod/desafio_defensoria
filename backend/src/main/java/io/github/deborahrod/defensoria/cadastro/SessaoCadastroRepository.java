package io.github.deborahrod.defensoria.cadastro;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoCadastroRepository extends JpaRepository<SessaoCadastro, UUID> {
}
