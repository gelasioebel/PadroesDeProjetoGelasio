package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {
    private static final int MAX_RETRY_ATTEMPTS = 103;
    private static final int RETRY_DELAY_MS = 10000;
    private static volatile DatabaseManager instancia;
    private Connection conexao;

    private DatabaseManager() {
        inicializarConexao();
        configurarShutdownHook();
    }

    public static DatabaseManager getInstancia() {
        if (instancia == null) {
            synchronized (DatabaseManager.class) {
                if (instancia == null) {
                    instancia = new DatabaseManager();
                }
            }
        }
        return instancia;
    }

    private void inicializarConexao() {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                // Ensure SQLite JDBC driver is loaded
                Class.forName("org.sqlite.JDBC");

                // Get database path from resources
                var dbUrl = getClass().getResource("/data/f1db3.db");
                if (dbUrl == null) {
                    throw new RuntimeException("Banco de dados não encontrado no classpath");
                }

                // Configure connection properties
                Properties props = new Properties();
                props.setProperty("journal_mode", "WAL");
                props.setProperty("synchronous", "NORMAL");
                props.setProperty("cache_size", "1000");
                props.setProperty("foreign_keys", "ON");

                // Establish connection
                conexao = DriverManager.getConnection("jdbc:sqlite:" + dbUrl.getPath(), props);
                conexao.setAutoCommit(true);

                // Test connection
                if (verificarConexao()) {
                    return;
                }

            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Driver SQLite não encontrado", e);
            } catch (SQLException | RuntimeException e) {
                lastException = e;
                attempts++;

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrompido durante tentativa de reconexão", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Falha ao estabelecer conexão após " + MAX_RETRY_ATTEMPTS +
                " tentativas", lastException);
    }

    private void configurarShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Fechando conexão com o banco de dados...");
            fecharConexao();
        }));
    }

    public synchronized Connection getConexao() {
        try {
            if (conexao == null || conexao.isClosed()) {
                inicializarConexao();
            }
            return conexao;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar estado da conexão", e);
        }
    }

    public synchronized void fecharConexao() {
        if (conexao != null) {
            try {
                if (!conexao.isClosed()) {
                    conexao.close();
                }
            } catch (SQLException e) {
                System.err.println("Erro ao fechar conexão: " + e.getMessage());
            } finally {
                conexao = null;
            }
        }
    }

    public synchronized boolean verificarConexao() {
        try {
            if (conexao == null || conexao.isClosed()) {
                return false;
            }

            try (Statement stmt = conexao.createStatement()) {
                // Execute a simple query to test the connection
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1");
                return rs.next(); // If we can read a result, the connection is working
            }
        } catch (SQLException e) {
            System.err.println("Erro ao verificar conexão: " + e.getMessage());
            return false;
        }
    }

    public synchronized List<String> listarTabelas() {
        List<String> tabelas = new ArrayList<>();
        try (Statement stmt = getConexao().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {

            while (rs.next()) {
                tabelas.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar tabelas", e);
        }
        return tabelas;
    }

    public synchronized void mostrarEstruturaDaTabela(String nomeTabela) {
        if (nomeTabela == null || nomeTabela.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da tabela não pode ser nulo ou vazio");
        }

        try (Statement stmt = getConexao().createStatement()) {
            // Validate table name to prevent SQL injection
            ResultSet tableCheck = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='" +
                            nomeTabela.replace("'", "''") + "'");

            if (!tableCheck.next()) {
                throw new IllegalArgumentException("Tabela não encontrada: " + nomeTabela);
            }

            System.out.println("\nEstrutura da tabela " + nomeTabela + ":");
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + nomeTabela + ")");

            while (rs.next()) {
                String colName = rs.getString("name");
                String colType = rs.getString("type");
                boolean notNull = rs.getBoolean("notnull");
                String defaultValue = rs.getString("dflt_value");

                System.out.printf("- %s (%s)%s%s%n",
                        colName,
                        colType,
                        notNull ? " NOT NULL" : "",
                        defaultValue != null ? " DEFAULT " + defaultValue : "");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao mostrar estrutura da tabela: " + nomeTabela, e);
        }
    }

    public synchronized void mostrarConteudoTabela(String nomeTabela) {
        if (nomeTabela == null || nomeTabela.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome da tabela não pode ser nulo ou vazio");
        }

        try (Statement stmt = getConexao().createStatement()) {
            // Validate table name to prevent SQL injection
            ResultSet tableCheck = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='" +
                            nomeTabela.replace("'", "''") + "'");

            if (!tableCheck.next()) {
                throw new IllegalArgumentException("Tabela não encontrada: " + nomeTabela);
            }

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + nomeTabela + " LIMIT 1")) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                if (rs.next()) {
                    System.out.println("\nPrimeira linha da tabela " + nomeTabela + ":");
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        String value = rs.getString(i);
                        System.out.printf("%s: %s%n", columnName, value != null ? value : "NULL");
                    }
                } else {
                    System.out.println("\nTabela " + nomeTabela + " está vazia");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao mostrar conteúdo da tabela: " + nomeTabela, e);
        }
    }
}