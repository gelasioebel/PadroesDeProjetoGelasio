package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado;

/**
 * Interface que define o contrato para os diferentes estados de um piloto durante uma corrida.
 * Implementa o padrão State para gerenciar as transições de estado dos pilotos.
 */
public interface EstadoPiloto {
    /**
     * Atualiza o estado do piloto com base no tempo decorrido.
     *
     * @param deltaTempoMs Tempo decorrido desde a última atualização em milissegundos.
     * @throws IllegalArgumentException se deltaTempoMs for negativo
     */
    void atualizar(long deltaTempoMs);

    /**
     * Retorna a descrição textual do status atual do piloto.
     *
     * @return String descrevendo o status atual (ex: "Correndo", "Abandonou", etc.)
     */
    String getStatus();

    /**
     * Verifica se o piloto terminou sua participação na corrida (seja por completar ou abandonar).
     *
     * @return true se o piloto terminou sua participação, false caso contrário
     */
    boolean isTerminado();

    /**
     * Retorna o progresso atual do piloto na corrida.
     *
     * @return valor entre 0.0 e 1.0 representando o progresso (0% a 100%)
     */
    double getProgresso();

    /**
     * Retorna o emoji correspondente ao estado atual do piloto.
     *
     * @return String contendo o emoji apropriado para o estado
     */
    String getEmoji();

    /**
     * Verifica se o estado atual é válido.
     *
     * @return true se o estado é válido, false caso contrário
     */
    default boolean isValido() {
        try {
            double progresso = getProgresso();
            return progresso >= 0.0 && progresso <= 1.0 &&
                    getStatus() != null &&
                    getEmoji() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cria uma cópia do estado atual.
     * Implementações devem garantir uma cópia profunda de todos os dados.
     *
     * @return Uma nova instância de EstadoPiloto com os mesmos dados
     */
    default EstadoPiloto copy() {
        throw new UnsupportedOperationException("Método copy() não implementado");
    }

    /**
     * Verifica se o estado representa um abandono da corrida.
     *
     * @return true se o piloto abandonou a corrida, false caso contrário
     */
    default boolean isAbandono() {
        return isTerminado() && getProgresso() < 1.0;
    }

    /**
     * Verifica se o estado representa uma finalização normal da corrida.
     *
     * @return true se o piloto completou a corrida normalmente, false caso contrário
     */
    default boolean isFinalizacaoNormal() {
        return isTerminado() && getProgresso() >= 1.0;
    }

    /**
     * Retorna uma descrição detalhada do estado atual.
     *
     * @return String com informações detalhadas sobre o estado
     */
    default String getDescricaoDetalhada() {
        return String.format("Estado: %s (Progresso: %.1f%%, %s) %s",
                getStatus(),
                getProgresso() * 100,
                isTerminado() ? "Terminado" : "Em andamento",
                getEmoji());
    }
}