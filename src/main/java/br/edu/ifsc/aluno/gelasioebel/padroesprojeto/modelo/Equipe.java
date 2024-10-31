package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo;

import java.util.Objects;
import java.util.regex.Pattern;

public class Equipe {
    private static final Pattern NOME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s\\-&.]+$");
    private static final int NOME_MIN_LENGTH = 2;
    private static final int NOME_MAX_LENGTH = 100;

    private final int id;
    private final String nome;
    private final String nacionalidade;
    private final String nomeNormalizado;

    private Equipe(EquipeBuilder builder) {
        validarBuilder(builder);
        this.id = builder.id;
        this.nome = normalizar(builder.nome);
        this.nacionalidade = normalizar(builder.nacionalidade);
        this.nomeNormalizado = normalizarParaComparacao(this.nome);
    }

    private void validarBuilder(EquipeBuilder builder) {
        if (builder.id <= 0) {
            throw new IllegalArgumentException("ID da equipe deve ser positivo");
        }
        validarNome(builder.nome);
        validarNacionalidade(builder.nacionalidade);
    }

    private void validarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da equipe é obrigatório");
        }

        String nomeNormalizado = nome.trim();

        if (nomeNormalizado.length() < NOME_MIN_LENGTH ||
                nomeNormalizado.length() > NOME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Nome da equipe deve ter entre %d e %d caracteres",
                            NOME_MIN_LENGTH, NOME_MAX_LENGTH));
        }

        if (!NOME_PATTERN.matcher(nomeNormalizado).matches()) {
            throw new IllegalArgumentException(
                    "Nome da equipe contém caracteres inválidos: " + nome);
        }
    }

    private void validarNacionalidade(String nacionalidade) {
        if (nacionalidade == null || nacionalidade.trim().isEmpty()) {
            throw new IllegalArgumentException("Nacionalidade da equipe é obrigatória");
        }

        String nacionalidadeNormalizada = nacionalidade.trim();

        if (nacionalidadeNormalizada.length() < NOME_MIN_LENGTH ||
                nacionalidadeNormalizada.length() > NOME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Nacionalidade da equipe tem tamanho inválido");
        }

        if (!NOME_PATTERN.matcher(nacionalidadeNormalizada).matches()) {
            throw new IllegalArgumentException(
                    "Nacionalidade da equipe contém caracteres inválidos: " + nacionalidade);
        }
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim();
    }

    private String normalizarParaComparacao(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9]", "");
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNacionalidade() {
        return nacionalidade;
    }

    public String getNomeNormalizado() {
        return nomeNormalizado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Equipe equipe = (Equipe) o;
        return id == equipe.id &&
                Objects.equals(nomeNormalizado, equipe.nomeNormalizado) &&
                Objects.equals(nacionalidade, equipe.nacionalidade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nomeNormalizado, nacionalidade);
    }

    @Override
    public String toString() {
        return String.format("Equipe{id=%d, nome='%s', nacionalidade='%s'}",
                id, nome, nacionalidade);
    }

    /**
     * Cria uma cópia defensiva da equipe
     *
     * @return Uma nova instância de Equipe com os mesmos dados
     */
    public Equipe copy() {
        return new EquipeBuilder()
                .setId(this.id)
                .setNome(this.nome)
                .setNacionalidade(this.nacionalidade)
                .build();
    }

    /**
     * Verifica se esta equipe tem o mesmo nome que outra, ignorando diferenças de caso
     * e caracteres especiais
     *
     * @param outraEquipe A equipe a ser comparada
     * @return true se os nomes são considerados iguais, false caso contrário
     */
    public boolean temMesmoNome(Equipe outraEquipe) {
        if (outraEquipe == null) {
            return false;
        }
        return this.nomeNormalizado.equals(outraEquipe.nomeNormalizado);
    }

    /**
     * Verifica se o nome da equipe contém o texto especificado, ignorando case
     *
     * @param texto O texto a ser procurado
     * @return true se o nome contém o texto, false caso contrário
     */
    public boolean nomeContem(String texto) {
        if (texto == null) {
            return false;
        }
        return this.nomeNormalizado.contains(normalizarParaComparacao(texto));
    }

    public static class EquipeBuilder {
        private int id;
        private String nome;
        private String nacionalidade;

        public EquipeBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public EquipeBuilder setNome(String nome) {
            this.nome = nome;
            return this;
        }

        public EquipeBuilder setNacionalidade(String nacionalidade) {
            this.nacionalidade = nacionalidade;
            return this;
        }

        public Equipe build() {
            return new Equipe(this);
        }
    }
}