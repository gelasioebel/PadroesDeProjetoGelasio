package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado;


public interface Estado {
    void atualizar(long deltaTempoMs);

    String getStatus();

    boolean isTerminado();

    double getProgresso();

    String getEmoji();
}