package br.edu.ifsc.aluno.gelasioebel.padroesprojeto;

import java.util.EventListener;

/**
 * Interface para notificar mudanças na simulação da corrida.
 * Implementa o padrão Observer para atualizar a interface gráfica e outros componentes interessados
 * no estado da simulação.
 */
public interface SimulacaoListener extends EventListener {

    /**
     * Factory method para criar um SimulacaoListener a partir de um lambda.
     * Útil para casos onde apenas uma única operação precisa ser executada em resposta
     * às atualizações da simulação.
     *
     * @param handler O handler lambda para processar as atualizações
     * @return Uma nova instância de SimulacaoListener
     * @throws IllegalArgumentException se o handler for null
     */
    static SimulacaoListener from(SimulacaoHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler não pode ser null");
        }

        return simulador -> {
            try {
                handler.handle(simulador);
            } catch (Exception e) {
                System.err.println("Erro ao processar atualização da simulação: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    /**
     * Chamado quando houver atualização na simulação.
     * Este método é invocado pelo SimuladorF1Facade sempre que o estado da simulação mudar,
     * incluindo atualizações de progresso, mudanças de estado dos pilotos, início, pausa,
     * ou término da corrida.
     *
     * @param simulador Referência para o SimuladorF1Facade que contém o estado atual da simulação
     * @throws IllegalArgumentException se o simulador for null
     * @throws IllegalStateException    se chamado quando o simulador estiver em um estado inválido
     */
    void onSimulacaoAtualizada(SimuladorF1Facade simulador);

    /**
     * Implementação padrão para validar o simulador antes de processar a atualização.
     * Implementações podem sobrescrever este método para adicionar validações adicionais.
     *
     * @param simulador O simulador a ser validado
     * @throws IllegalArgumentException se o simulador for null
     * @throws IllegalStateException    se o simulador estiver em um estado inválido
     */
    default void validarSimulador(SimuladorF1Facade simulador) {
        if (simulador == null) {
            throw new IllegalArgumentException("Simulador não pode ser null");
        }
    }

    /**
     * Interface funcional para manipular atualizações da simulação.
     * Usada em conjunto com o método factory {@link #from(SimulacaoHandler)}.
     */
    @FunctionalInterface
    interface SimulacaoHandler {
        /**
         * Processa uma atualização da simulação.
         *
         * @param simulador O simulador contendo o estado atual
         * @throws Exception se ocorrer algum erro durante o processamento
         */
        void handle(SimuladorF1Facade simulador) throws Exception;
    }
}