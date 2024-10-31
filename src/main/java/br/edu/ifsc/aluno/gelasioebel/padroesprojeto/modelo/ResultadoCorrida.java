package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo;

import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado.EstadoPiloto;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado.Estados;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ResultadoCorrida {
    private static final Map<Integer, String> STATUS_DESCRIPTIONS;
    private static final double PROGRESSO_MINIMO = 0.0;
    private static final double PROGRESSO_MAXIMO = 1.0;

    static {
        Map<Integer, String> status = new HashMap<>();
        status.put(1, "Finished");
        status.put(2, "Disqualified");
        status.put(3, "Accident");
        status.put(4, "Collision");
        status.put(5, "Engine");
        status.put(6, "Gearbox");
        status.put(7, "Transmission");
        status.put(8, "Clutch");
        status.put(9, "Hydraulics");
        status.put(10, "Electrical");
        status.put(11, "+1 Lap");
        status.put(12, "+2 Laps");
        status.put(13, "+3 Laps");
        status.put(14, "+4 Laps");
        status.put(15, "+5 Laps");
        status.put(16, "+6 Laps");
        status.put(17, "+7 Laps");
        status.put(18, "+8 Laps");
        status.put(19, "+9 Laps");
        status.put(20, "Spun off");
        status.put(21, "Radiator");
        status.put(22, "Suspension");
        status.put(23, "Brakes");
        status.put(24, "Differential");
        status.put(25, "Overheating");
        status.put(26, "Mechanical");
        status.put(27, "Tyre");
        status.put(28, "Driver Seat");
        status.put(29, "Puncture");
        status.put(30, "Driveshaft");
        status.put(31, "Retired");
        status.put(32, "Fuel pressure");
        status.put(33, "Front wing");
        status.put(34, "Water pressure");
        status.put(35, "Race cancelled");
        status.put(36, "Race stopped");
        status.put(37, "Did not qualify");
        status.put(38, "Did not pre-qualify");
        status.put(39, "Excluded");
        status.put(40, "Did not start");
        STATUS_DESCRIPTIONS = Collections.unmodifiableMap(status);
    }

    private final int posicao;
    private final long tempoMs;
    private final int statusId;
    private final Equipe equipe;

    public ResultadoCorrida(int posicao, long tempoMs, int statusId, Equipe equipe) {
        validarParametros(posicao, tempoMs, statusId, equipe);
        this.posicao = posicao;
        this.tempoMs = tempoMs;
        this.statusId = statusId;
        this.equipe = equipe;
    }

    private void validarParametros(int posicao, long tempoMs, int statusId, Equipe equipe) {
        if (posicao < 0) {
            throw new IllegalArgumentException("Posição não pode ser negativa: " + posicao);
        }
        if (tempoMs < 0) {
            throw new IllegalArgumentException("Tempo não pode ser negativo: " + tempoMs);
        }
        if (!STATUS_DESCRIPTIONS.containsKey(statusId)) {
            throw new IllegalArgumentException("Status ID inválido: " + statusId);
        }
        Objects.requireNonNull(equipe, "Equipe não pode ser nula");
    }

    public EstadoPiloto criarEstadoInicial() {
        try {
            if (tempoMs <= 0) {
                return criarEstadoAbandono("Não completou", 0);
            }

            return switch (statusId) {
                case 1, 11, 12, 13, 14, 15, 16, 17, 18, 19 -> new Estados.Correndo(tempoMs, posicao);
                case 3, 4, 20 -> criarEstadoAbandono("Acidente", calcularVoltaAbandono());
                case 5, 6, 7, 8, 9, 10, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34 ->
                        criarEstadoAbandono("Problema Mecânico", calcularVoltaAbandono());
                case 2, 37, 38, 39 -> criarEstadoAbandono("Desqualificado", 0);
                case 31 -> criarEstadoAbandono("Abandonou", calcularVoltaAbandono());
                case 35, 36 -> criarEstadoAbandono("Corrida Interrompida", calcularVoltaAbandono());
                case 40 -> criarEstadoAbandono("Não Largou", 0);
                default -> criarEstadoAbandono("Não Completou", 0);
            };
        } catch (Exception e) {
            System.err.println("Erro ao criar estado inicial: " + e.getMessage());
            return criarEstadoAbandono("Erro", 0);
        }
    }

    private Estados.Abandonou criarEstadoAbandono(String motivo, int volta) {
        double progressoFinal = calcularProgressoFinal(volta);
        return new Estados.Abandonou(motivo, volta, progressoFinal);
    }

    private int calcularVoltaAbandono() {
        // Se não tiver tempo registrado, considera que não completou nenhuma volta
        if (tempoMs <= 0) {
            return 0;
        }

        // Estima a volta do abandono baseado no progresso da corrida
        double progressoEstimado = calcularProgressoEstimado();
        return (int) Math.ceil(progressoEstimado * 50); // Assume média de 50 voltas
    }

    private double calcularProgressoEstimado() {
        // Tempo médio de uma corrida em ms (2 horas = 7.200.000 ms)
        final long TEMPO_MEDIO_CORRIDA = 7_200_000;
        return Math.min(PROGRESSO_MAXIMO,
                Math.max(PROGRESSO_MINIMO,
                        (double) tempoMs / TEMPO_MEDIO_CORRIDA));
    }

    private double calcularProgressoFinal(int volta) {
        if (volta <= 0) {
            return PROGRESSO_MINIMO;
        }
        // Considera progresso máximo de 1.0 (100%)
        return Math.min(PROGRESSO_MAXIMO, volta / 50.0);
    }

    // Getters
    public int getPosicao() {
        return posicao;
    }

    public long getTempoMs() {
        return tempoMs;
    }

    public int getStatusId() {
        return statusId;
    }

    public String getStatusDescricao() {
        return STATUS_DESCRIPTIONS.getOrDefault(statusId, "Status Desconhecido");
    }

    public Equipe getEquipe() {
        return equipe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultadoCorrida that = (ResultadoCorrida) o;
        return posicao == that.posicao &&
                tempoMs == that.tempoMs &&
                statusId == that.statusId &&
                Objects.equals(equipe, that.equipe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(posicao, tempoMs, statusId, equipe);
    }

    @Override
    public String toString() {
        return String.format("ResultadoCorrida{posicao=%d, tempo=%dms, status='%s', equipe=%s}",
                posicao, tempoMs, getStatusDescricao(), equipe);
    }
}