package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado;

import java.util.Objects;

public class Estados {
    private static final double PROGRESSO_MINIMO = 0.0;
    private static final double PROGRESSO_MAXIMO = 1.0;
    private static final long TEMPO_MINIMO = 0L;

    public static class Correndo implements EstadoPiloto {
        private final long tempoTotal;
        private final int posicao;
        private long tempoDecorrido;
        private volatile boolean pausado;

        public Correndo(long tempoTotal, int posicao) {
            validarParametros(tempoTotal, posicao);
            this.tempoTotal = tempoTotal;
            this.tempoDecorrido = TEMPO_MINIMO;
            this.posicao = posicao;
            this.pausado = false;
        }

        private void validarParametros(long tempoTotal, int posicao) {
            if (tempoTotal <= TEMPO_MINIMO) {
                throw new IllegalArgumentException("Tempo total deve ser positivo");
            }
            if (posicao <= 0) {
                throw new IllegalArgumentException("Posição deve ser positiva");
            }
        }

        @Override
        public synchronized void atualizar(long deltaTempoMs) {
            if (deltaTempoMs < 0) {
                throw new IllegalArgumentException("Delta de tempo não pode ser negativo");
            }
            if (pausado || isTerminado()) {
                return;
            }
            tempoDecorrido = Math.min(tempoTotal, tempoDecorrido + deltaTempoMs);
        }

        @Override
        public String getStatus() {
            if (pausado) {
                return "Pausado";
            }
            return isTerminado() ? "Finalizado" : "Correndo";
        }

        @Override
        public boolean isTerminado() {
            return tempoDecorrido >= tempoTotal;
        }

        @Override
        public double getProgresso() {
            return Math.min(PROGRESSO_MAXIMO,
                    Math.max(PROGRESSO_MINIMO,
                            (double) tempoDecorrido / tempoTotal));
        }

        @Override
        public String getEmoji() {
            if (pausado) {
                return "⏸️";
            }
            if (isTerminado()) {
                return switch (posicao) {
                    case 1 -> "🥇";
                    case 2 -> "🥈";
                    case 3 -> "🥉";
                    default -> "🏁";
                };
            }
            return "🏎️";
        }

        public synchronized void pausar() {
            this.pausado = true;
        }

        public synchronized void continuar() {
            this.pausado = false;
        }

        @Override
        public EstadoPiloto copy() {
            Correndo copia = new Correndo(this.tempoTotal, this.posicao);
            copia.tempoDecorrido = this.tempoDecorrido;
            copia.pausado = this.pausado;
            return copia;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Correndo correndo = (Correndo) o;
            return tempoTotal == correndo.tempoTotal &&
                    tempoDecorrido == correndo.tempoDecorrido &&
                    posicao == correndo.posicao &&
                    pausado == correndo.pausado;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tempoTotal, tempoDecorrido, posicao, pausado);
        }

        @Override
        public String toString() {
            return String.format("Correndo{progresso=%.1f%%, posição=%d, %s}",
                    getProgresso() * 100, posicao, getStatus());
        }
    }

    public static class Abandonou implements EstadoPiloto {
        private final String motivo;
        private final int volta;
        private final double progressoFinal;
        private final long momentoAbandono;

        public Abandonou(String motivo, int volta, double progressoFinal) {
            validarParametros(motivo, volta, progressoFinal);
            this.motivo = motivo;
            this.volta = volta;
            this.progressoFinal = normalizarProgresso(progressoFinal);
            this.momentoAbandono = System.currentTimeMillis();
        }

        private void validarParametros(String motivo, int volta, double progressoFinal) {
            if (motivo == null || motivo.trim().isEmpty()) {
                throw new IllegalArgumentException("Motivo do abandono não pode ser vazio");
            }
            if (volta < 0) {
                throw new IllegalArgumentException("Volta não pode ser negativa");
            }
            if (progressoFinal < PROGRESSO_MINIMO || progressoFinal > PROGRESSO_MAXIMO) {
                throw new IllegalArgumentException(
                        "Progresso final deve estar entre 0.0 e 1.0: " + progressoFinal);
            }
        }

        private double normalizarProgresso(double progresso) {
            return Math.min(PROGRESSO_MAXIMO, Math.max(PROGRESSO_MINIMO, progresso));
        }

        @Override
        public void atualizar(long deltaTempoMs) {
            // Estado imutável - não faz nada
        }

        @Override
        public String getStatus() {
            return motivo + (volta > 0 ? " na volta " + volta : "");
        }

        @Override
        public boolean isTerminado() {
            return true;
        }

        @Override
        public double getProgresso() {
            return progressoFinal;
        }

        @Override
        public String getEmoji() {
            return switch (motivo.toLowerCase()) {
                case "acidente" -> "💥";
                case "problema mecânico" -> "🔧";
                case "desqualificado" -> "⛔";
                case "não largou" -> "🚫";
                case "corrida interrompida" -> "🔴";
                default -> "❌";
            };
        }

        public String getMotivo() {
            return motivo;
        }

        public int getVolta() {
            return volta;
        }

        public long getMomentoAbandono() {
            return momentoAbandono;
        }

        @Override
        public EstadoPiloto copy() {
            return new Abandonou(this.motivo, this.volta, this.progressoFinal);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Abandonou abandonou = (Abandonou) o;
            return volta == abandonou.volta &&
                    Double.compare(progressoFinal, abandonou.progressoFinal) == 0 &&
                    Objects.equals(motivo, abandonou.motivo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(motivo, volta, progressoFinal);
        }

        @Override
        public String toString() {
            return String.format("Abandonou{motivo='%s', volta=%d, progresso=%.1f%%}",
                    motivo, volta, progressoFinal * 100);
        }
    }
}