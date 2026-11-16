package io.github.deborahrod.defensoria.integracao;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Consulta o ViaCEP e, se ele falhar ou não conhecer o CEP, a BrasilAPI. Guarda em memória, por tempo
 * e tamanho limitados, o que os provedores responderam. Falha de rede nunca entra no cache. Só o CEP sai
 * do servidor, e ele não é registrado em log nem em evento.
 */
@Service
public class ServicoCep implements ConsultaCep {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoCep.class);

    private record Guardado(ResultadoCep resultado, Instant expiraEm) {
    }

    private final List<ProvedorCep> provedores;
    private final ServicoEventos eventos;
    private final Clock relogio;
    private final PropriedadesCep propriedades;
    private final Map<String, Guardado> cache;

    ServicoCep(
            ClienteViaCep viacep,
            ClienteBrasilApi brasilapi,
            ServicoEventos eventos,
            Clock relogio,
            PropriedadesCep propriedades) {
        this.provedores = List.of(viacep, brasilapi);
        this.eventos = eventos;
        this.relogio = relogio;
        this.propriedades = propriedades;
        var maximo = propriedades.cacheMaximo();
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Guardado> eldest) {
                return size() > maximo;
            }
        };
    }

    @Override
    public ResultadoCep consultar(String cep) {
        var inicio = System.nanoTime();
        var guardado = doCache(cep);
        if (guardado != null) {
            registrar("CACHE", "cache", inicio);
            return guardado;
        }
        var algumNaoConhece = false;
        for (var provedor : provedores) {
            try {
                var encontrado = provedor.buscar(cep);
                if (encontrado.isPresent()) {
                    var resultado = new ResultadoCep.Encontrado(encontrado.get());
                    guardar(cep, resultado);
                    registrar("ENCONTRADO", provedor.nome(), inicio);
                    return resultado;
                }
                algumNaoConhece = true;
            } catch (FalhaCepException falha) {
                LOG.info("consulta de CEP falhou no provedor {}: {}", provedor.nome(), falha.getMessage());
            }
        }
        if (algumNaoConhece) {
            var resultado = new ResultadoCep.NaoEncontrado();
            guardar(cep, resultado);
            registrar("NAO_ENCONTRADO", "provedores", inicio);
            return resultado;
        }
        registrar("INDISPONIVEL", "provedores", inicio);
        return new ResultadoCep.Indisponivel();
    }

    private synchronized ResultadoCep doCache(String cep) {
        var item = cache.get(cep);
        if (item == null) {
            return null;
        }
        if (item.expiraEm().isBefore(relogio.instant())) {
            cache.remove(cep);
            return null;
        }
        return item.resultado();
    }

    private synchronized void guardar(String cep, ResultadoCep resultado) {
        cache.put(cep, new Guardado(resultado, relogio.instant().plus(propriedades.cacheValidade())));
    }

    private void registrar(String status, String origem, long inicio) {
        eventos.registrar("CONSULTA_CEP", status, null, origem, (System.nanoTime() - inicio) / 1_000_000);
    }
}
