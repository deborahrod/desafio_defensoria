package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessaoCadastroRepository extends JpaRepository<SessaoCadastro, UUID> {

    Optional<SessaoCadastro> findBySegredoHash(String segredoHash);

    // Segura a linha da sessão: confirmações simultâneas passam uma de cada vez.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessaoCadastro s where s.id = :id")
    Optional<SessaoCadastro> buscarComTrava(UUID id);

    List<SessaoCadastro> findByEstadoInAndExpiraEmLessThanEqual(Collection<EstadoSessao> estados, Instant limite);

    // Atualização direta: renovar o prazo não deve concorrer com mudanças de estado da sessão.
    @Modifying(clearAutomatically = true)
    @Query("update SessaoCadastro s set s.ultimoAcessoEm = :agora, s.expiraEm = :expiraEm where s.id = :id")
    void renovarPrazo(UUID id, Instant agora, Instant expiraEm);

    @Modifying
    @Query("delete from SessaoCadastro s where s.estado in :estados and s.expiraEm <= :limite")
    int apagarEncerradasAte(Collection<EstadoSessao> estados, Instant limite);
}
