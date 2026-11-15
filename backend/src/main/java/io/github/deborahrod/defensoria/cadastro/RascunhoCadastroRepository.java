package io.github.deborahrod.defensoria.cadastro;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RascunhoCadastroRepository extends JpaRepository<RascunhoCadastro, UUID> {
}
