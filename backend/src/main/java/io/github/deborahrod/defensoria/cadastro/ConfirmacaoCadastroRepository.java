package io.github.deborahrod.defensoria.cadastro;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmacaoCadastroRepository extends JpaRepository<ConfirmacaoCadastro, UUID> {

    Optional<ConfirmacaoCadastro> findBySessaoId(UUID sessaoId);
}
