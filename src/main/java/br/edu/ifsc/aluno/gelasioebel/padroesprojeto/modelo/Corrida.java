package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * Representa uma corrida de Fórmula 1.
 * Implementa o padrão Builder para criação segura de instâncias.
 */
public class Corrida {
    private static final int ANO_MINIMO = 1950;
    private static final int ROUND_MINIMO = 1;
    private static final int ROUND_MAXIMO = 30;

    private final int id;
    private final int ano;
    private final int round;
    private final String nome;
    private final String circuito;
    private final String pais;
    private final LocalDateTime dataHora;
    private final Map<Piloto, ResultadoCorrida> resultados;
    private final String nomeCircuitoNormalizado;

    private Corrida(CorridaBuilder builder) {
        validarBuilder(builder);
        this.id = builder.id;
        this.ano = builder.ano;
        this.round = builder.round;
        this.nome = normalizar(builder.nome);
        this.circuito = normalizar(builder.circuito);
        this.pais = normalizar(builder.pais);
        this.dataHora = builder.dataHora;
        this.resultados = Collections.unmodifiableMap(new HashMap<>(builder.resultados));
        this.nomeCircuitoNormalizado = normalizarParaComparacao(this.circuito);
    }

    private void validarBuilder(CorridaBuilder builder) {
        List<String> erros = new ArrayList<>();

        if (builder.id <= 0) {
            erros.add("ID da corrida deve ser positivo");
        }

        if (builder.ano < ANO_MINIMO || builder.ano > Year.now().getValue()) {
            erros.add("Ano inválido: " + builder.ano);
        }

        if (builder.round < ROUND_MINIMO || builder.round > ROUND_MAXIMO) {
            erros.add("Round inválido: " + builder.round);
        }

        if (builder.nome == null || builder.nome.trim().isEmpty()) {
            erros.add("Nome da corrida é obrigatório");
        }

        if (builder.circuito == null || builder.circuito.trim().isEmpty()) {
            erros.add("Nome do circuito é obrigatório");
        }

        if (builder.pais == null || builder.pais.trim().isEmpty()) {
            erros.add("País é obrigatório");
        }

        if (builder.dataHora == null) {
            erros.add("Data e hora são obrigatórios");
        } else if (builder.dataHora.isAfter(LocalDateTime.now().plusYears(1))) {
            erros.add("Data da corrida não pode ser superior a um ano no futuro");
        }

        if (!erros.isEmpty()) {
            throw new IllegalStateException("Erros de validação:\n- " +
                    String.join("\n- ", erros));
        }
    }

    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private String normalizarParaComparacao(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }

    // Getters
    public int getId() {
        return id;
    }

    public int getAno() {
        return ano;
    }

    public int getRound() {
        return round;
    }

    public String getNome() {
        return nome;
    }

    public String getCircuito() {
        return circuito;
    }

    public String getPais() {
        return pais;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public Map<Piloto, ResultadoCorrida> getResultados() {
        return resultados;
    }

    /**
     * Verifica se o circuito tem o nome especificado, ignorando case e acentos.
     */
    public boolean verificarCircuito(String nomeCircuito) {
        if (nomeCircuito == null) {
            return false;
        }
        return nomeCircuitoNormalizado.equals(normalizarParaComparacao(nomeCircuito));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Corrida corrida = (Corrida) o;
        return id == corrida.id &&
                ano == corrida.ano &&
                round == corrida.round &&
                Objects.equals(nome, corrida.nome) &&
                Objects.equals(nomeCircuitoNormalizado, corrida.nomeCircuitoNormalizado) &&
                Objects.equals(pais, corrida.pais) &&
                Objects.equals(dataHora, corrida.dataHora);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ano, round, nome, nomeCircuitoNormalizado, pais, dataHora);
    }

    @Override
    public String toString() {
        return String.format(
                "Corrida{id=%d, nome='%s', circuito='%s', país='%s', data='%s', pilotos=%d}",
                id, nome, circuito, pais, dataHora, resultados.size());
    }

    /**
     * Cria uma cópia defensiva da corrida.
     */
    public Corrida copy() {
        CorridaBuilder builder = new CorridaBuilder()
                .setId(this.id)
                .setAno(this.ano)
                .setRound(this.round)
                .setNome(this.nome)
                .setCircuito(this.circuito, this.pais)
                .setDataHora(this.dataHora);

        this.resultados.forEach(builder::addResultado);
        return builder.build();
    }

    public static class CorridaBuilder {
        private final Map<Piloto, ResultadoCorrida> resultados = new HashMap<>();
        private int id;
        private int ano;
        private int round;
        private String nome;
        private String circuito;
        private String pais;
        private LocalDateTime dataHora;

        public CorridaBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public CorridaBuilder setAno(int ano) {
            this.ano = ano;
            return this;
        }

        public CorridaBuilder setRound(int round) {
            this.round = round;
            return this;
        }

        public CorridaBuilder setNome(String nome) {
            this.nome = nome;
            return this;
        }

        public CorridaBuilder setCircuito(String circuito, String pais) {
            this.circuito = circuito;
            this.pais = pais;
            return this;
        }

        public CorridaBuilder setDataHora(LocalDateTime dataHora) {
            this.dataHora = dataHora;
            return this;
        }

        public CorridaBuilder addResultado(Piloto piloto, ResultadoCorrida resultado) {
            if (piloto == null) {
                throw new IllegalArgumentException("Piloto não pode ser null");
            }
            if (resultado == null) {
                throw new IllegalArgumentException("Resultado não pode ser null");
            }
            this.resultados.put(piloto, resultado);
            return this;
        }

        public CorridaBuilder addResultados(Map<Piloto, ResultadoCorrida> resultados) {
            if (resultados == null) {
                throw new IllegalArgumentException("Map de resultados não pode ser null");
            }
            resultados.forEach(this::addResultado);
            return this;
        }

        public Corrida build() {
            return new Corrida(this);
        }
    }
}