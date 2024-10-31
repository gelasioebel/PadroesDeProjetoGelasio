package br.edu.ifsc.aluno.gelasioebel.padroesprojeto;

import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.dao.CorridaDAO;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado.EstadoPiloto;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Corrida;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Piloto;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.ResultadoCorrida;

import java.util.*;

public class SimuladorF1Facade {
    private static SimuladorF1Facade instancia;
    private final CorridaDAO corridaDAO;
    private final List<SimulacaoListener> listeners;
    private Corrida corridaAtual;
    private final Map<Piloto, EstadoPiloto> estadosPilotos;
    private double velocidade;
    private boolean emExecucao;
    private final Map<String, Map<String, Integer>> mapaCorridasPorAno; // novo campo

    private SimuladorF1Facade() {
        this.corridaDAO = CorridaDAO.getInstancia();
        this.estadosPilotos = new HashMap<>();
        this.velocidade = 1.0;
        this.emExecucao = false;
        this.listeners = new ArrayList<>();
        this.mapaCorridasPorAno = new HashMap<>();
    }

    public static SimuladorF1Facade getInstancia() {
        if (instancia == null) {
            synchronized (SimuladorF1Facade.class) {
                if (instancia == null) {
                    instancia = new SimuladorF1Facade();
                }
            }
        }
        return instancia;
    }

    // Novos métodos
    public Corrida getCorridaInfo(int ano, int round) {
        try {
            return corridaDAO.buscarCorridaBasica(ano, round);
        } catch (Exception e) {
            System.err.println("Erro ao buscar informações da corrida: " + e.getMessage());
            return null;
        }
    }

    public int getRoundPorNome(int ano, String nomeCorrida) {
        Map<String, Integer> corridasDoAno = mapaCorridasPorAno.computeIfAbsent(
                String.valueOf(ano), k -> new HashMap<>());

        Integer round = corridasDoAno.get(nomeCorrida);
        if (round == null) {
            // Se não encontrou no cache, busca todas as corridas do ano
            List<Integer> rounds = getRoundsDoAno(ano);
            for (Integer r : rounds) {
                Corrida corrida = getCorridaInfo(ano, r);
                if (corrida != null) {
                    corridasDoAno.put(corrida.getNome(), r);
                    if (corrida.getNome().equals(nomeCorrida)) {
                        round = r;
                    }
                }
            }
        }

        if (round == null) {
            throw new IllegalArgumentException("Corrida não encontrada: " + nomeCorrida);
        }
        return round;
    }


    public void carregarCorrida(int ano, int round) {
        try {
            this.corridaAtual = corridaDAO.buscarCorrida(ano, round);
            this.estadosPilotos.clear();

            for (Map.Entry<Piloto, ResultadoCorrida> entry : corridaAtual.getResultados().entrySet()) {
                estadosPilotos.put(entry.getKey(), entry.getValue().criarEstadoInicial());
            }

            notificarListeners();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar corrida: " + e.getMessage());
        }
    }

    public void setVelocidade(double velocidade) {
        this.velocidade = velocidade;
    }

    public void iniciar() {
        this.emExecucao = true;
        notificarListeners();
    }

    public void pausar() {
        this.emExecucao = false;
        notificarListeners();
    }

    public void parar() {
        this.emExecucao = false;
        this.estadosPilotos.clear();
        notificarListeners();
    }

    public void atualizarSimulacao(long deltaTempoMs) {
        if (!emExecucao) return;

        try {
            long deltaAjustado = (long) (deltaTempoMs * velocidade);
            for (EstadoPiloto estado : estadosPilotos.values()) {
                estado.atualizar(deltaAjustado);
            }
            notificarListeners();
        } catch (Exception e) {
            emExecucao = false;
            throw new RuntimeException("Erro durante atualização da simulação", e);
        }
    }

    public void addListener(SimulacaoListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(SimulacaoListener listener) {
        listeners.remove(listener);
    }

    private void notificarListeners() {
        for (SimulacaoListener listener : listeners) {
            try {
                listener.onSimulacaoAtualizada(this);
            } catch (Exception e) {
                System.err.println("Erro ao notificar listener: " + e.getMessage());
            }
        }
    }

    // Getters
    public boolean isEmExecucao() {
        return emExecucao;
    }

    public Map<Piloto, EstadoPiloto> getEstadosPilotos() {
        return Collections.unmodifiableMap(estadosPilotos);
    }

    public Corrida getCorridaAtual() {
        return corridaAtual;
    }

    public List<Integer> getAnosDisponiveis() {
        return corridaDAO.buscarAnosDisponiveis();
    }

    public List<Integer> getRoundsDoAno(int ano) {
        return corridaDAO.buscarRoundsDoAno(ano);
    }
}