package io.github.deborahrod.defensoria.conversa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemProcessadaRepository extends JpaRepository<MensagemProcessada, MensagemProcessada.Chave> {
}
