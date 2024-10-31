package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo;

import java.util.Objects;
import java.util.regex.Pattern;

public class Piloto {
    private static final int CODIGO_LENGTH = 3;
    private static final Pattern CODIGO_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern NUMERO_PATTERN = Pattern.compile("^[0-9]{1,2}$");

    private final int id;
    private final String codigo;
    private final String nome;
    private final String sobrenome;
    private final String nacionalidade;
    private final String numero;

    private Piloto(PilotoBuilder builder) {
        validarBuilder(builder);
        this.id = builder.id;
        this.codigo = normalizarCodigo(builder.codigo);
        this.nome = normalizar(builder.nome);
        this.sobrenome = normalizar(builder.sobrenome);
        this.nacionalidade = normalizar(builder.nacionalidade);
        this.numero = normalizarNumero(builder.numero);
    }

    private void validarBuilder(PilotoBuilder builder) {
        if (builder.id <= 0) {
            throw new IllegalArgumentException("ID do piloto deve ser positivo");
        }
        if (builder.codigo == null || builder.codigo.trim().isEmpty()) {
            throw new IllegalArgumentException("Código do piloto é obrigatório");
        }
        if (builder.nome == null || builder.nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do piloto é obrigatório");
        }
        if (builder.sobrenome == null || builder.sobrenome.trim().isEmpty()) {
            throw new IllegalArgumentException("Sobrenome do piloto é obrigatório");
        }
        if (builder.nacionalidade == null || builder.nacionalidade.trim().isEmpty()) {
            throw new IllegalArgumentException("Nacionalidade do piloto é obrigatória");
        }
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim();
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null) {
            throw new IllegalArgumentException("Código do piloto não pode ser nulo");
        }

        String codigoNormalizado = codigo.trim().toUpperCase();

        if (!CODIGO_PATTERN.matcher(codigoNormalizado).matches()) {
            throw new IllegalArgumentException(
                    "Código do piloto deve conter exatamente 3 letras maiúsculas: " + codigo);
        }

        return codigoNormalizado;
    }

    private String normalizarNumero(String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            return "";
        }

        String numeroNormalizado = numero.trim();

        if (!NUMERO_PATTERN.matcher(numeroNormalizado).matches()) {
            throw new IllegalArgumentException(
                    "Número do piloto deve conter 1 ou 2 dígitos: " + numero);
        }

        return numeroNormalizado;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getSobrenome() {
        return sobrenome;
    }

    public String getNomeCompleto() {
        return nome + " " + sobrenome;
    }

    public String getNacionalidade() {
        return nacionalidade;
    }

    public String getNumero() {
        return numero;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Piloto piloto = (Piloto) o;
        return id == piloto.id &&
                Objects.equals(codigo, piloto.codigo) &&
                Objects.equals(nome, piloto.nome) &&
                Objects.equals(sobrenome, piloto.sobrenome) &&
                Objects.equals(nacionalidade, piloto.nacionalidade) &&
                Objects.equals(numero, piloto.numero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codigo, nome, sobrenome, nacionalidade, numero);
    }

    @Override
    public String toString() {
        return String.format("Piloto{id=%d, codigo='%s', nome='%s %s', nacionalidade='%s', numero='%s'}",
                id, codigo, nome, sobrenome, nacionalidade, numero);
    }

    /**
     * Cria uma cópia defensiva do piloto
     *
     * @return Uma nova instância de Piloto com os mesmos dados
     */
    public Piloto copy() {
        return new PilotoBuilder()
                .setId(this.id)
                .setCodigo(this.codigo)
                .setNome(this.nome, this.sobrenome)
                .setNacionalidade(this.nacionalidade)
                .setNumero(this.numero)
                .build();
    }

    public static class PilotoBuilder {
        private int id;
        private String codigo;
        private String nome;
        private String sobrenome;
        private String nacionalidade;
        private String numero;

        public PilotoBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public PilotoBuilder setCodigo(String codigo) {
            this.codigo = codigo;
            return this;
        }

        public PilotoBuilder setNome(String nome, String sobrenome) {
            this.nome = nome;
            this.sobrenome = sobrenome;
            return this;
        }

        public PilotoBuilder setNacionalidade(String nacionalidade) {
            this.nacionalidade = nacionalidade;
            return this;
        }

        public PilotoBuilder setNumero(String numero) {
            this.numero = numero;
            return this;
        }

        public Piloto build() {
            return new Piloto(this);
        }
    }
}