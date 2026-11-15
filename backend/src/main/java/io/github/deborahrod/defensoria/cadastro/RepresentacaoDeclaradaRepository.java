package io.github.deborahrod.defensoria.cadastro;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepresentacaoDeclaradaRepository extends JpaRepository<RepresentacaoDeclarada, UUID> {
}
