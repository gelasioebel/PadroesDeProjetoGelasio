package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.dao;

import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Corrida;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Equipe;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Piloto;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.ResultadoCorrida;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CorridaDAO {
    private static class SingletonHelper {
        private static final CorridaDAO INSTANCE = new CorridaDAO();
    }

    public static CorridaDAO getInstancia() {
        return SingletonHelper.INSTANCE;
    }

    private final DatabaseManager dbManager;

    private CorridaDAO() {
        this.dbManager = DatabaseManager.getInstancia();
    }

    public List<Integer> buscarAnosDisponiveis() {
        String sql = """
                    SELECT DISTINCT year FROM races 
                    ORDER BY year DESC
                """;
        List<Integer> anos = new ArrayList<>();
        try (Connection conn = dbManager.getConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                anos.add(rs.getInt("year"));
            }
            return anos;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar anos: " + e.getMessage(), e);
        }
    }

    public List<Integer> buscarRoundsDoAno(int ano) {
        String sql = """
                    SELECT round FROM races 
                    WHERE year = ?
                    ORDER BY round
                """;
        List<Integer> rounds = new ArrayList<>();
        try (Connection conn = dbManager.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ano);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rounds.add(rs.getInt("round"));
                }
                return rounds;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar rounds: " + e.getMessage(), e);
        }
    }

    public Corrida buscarCorrida(int ano, int round) {
        String sql = """
                SELECT 
                    r.raceId, r.name, r.date, r.time,
                    c.name AS circuitName, c.country,
                    res.driverId, res.constructorId, res.position,
                    res.positionText, res.milliseconds, res.statusId,
                    d.code, d.forename, d.surname, d.nationality, d.number,
                    con.name AS constructorName, con.nationality AS constructorNationality
                FROM races r
                JOIN circuits c ON r.circuitId = c.circuitId
                JOIN results res ON r.raceId = res.raceId
                JOIN drivers d ON res.driverId = d.driverId
                JOIN constructors con ON res.constructorId = con.constructorId
                WHERE r.year = ? 
                AND r.round = ?
                ORDER BY 
                    CASE 
                        WHEN COALESCE(res.position, '\\N') = '\\N' THEN 999
                        ELSE CAST(res.position AS INTEGER)
                    END
                """;

        try (Connection conn = dbManager.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ano);
            stmt.setInt(2, round);

            try (ResultSet rs = stmt.executeQuery()) {
                Corrida.CorridaBuilder corridaBuilder = null;

                while (rs.next()) {
                    if (corridaBuilder == null) {
                        String dateStr = rs.getString("date");
                        String timeStr = rs.getString("time");

                        LocalDateTime dataHora = LocalDateTime.of(
                                LocalDate.parse(dateStr),
                                timeStr != null && !timeStr.equals("\\N") ?
                                        LocalTime.parse(timeStr) :
                                        LocalTime.MIDNIGHT
                        );

                        corridaBuilder = new Corrida.CorridaBuilder()
                                .setId(rs.getInt("raceId"))
                                .setAno(ano)
                                .setRound(round)
                                .setNome(rs.getString("name"))
                                .setCircuito(rs.getString("circuitName"), rs.getString("country"))
                                .setDataHora(dataHora);
                    }

                    String position = rs.getString("position");
                    int positionInt = 999;

                    if (position != null && !position.equals("\\N")) {
                        try {
                            positionInt = Integer.parseInt(position);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid position format: " + position);
                        }
                    }

                    String driverCode = rs.getString("code");
                    String surname = rs.getString("surname");
                    String finalCode = (driverCode != null && !driverCode.equals("\\N")) ?
                            driverCode :
                            surname.substring(0, Math.min(3, surname.length())).toUpperCase();

                    Piloto piloto = new Piloto.PilotoBuilder()
                            .setId(rs.getInt("driverId"))
                            .setCodigo(finalCode)
                            .setNome(rs.getString("forename"), surname)
                            .setNacionalidade(rs.getString("nationality"))
                            .setNumero(getNullSafeString(rs, "number"))
                            .build();

                    Equipe equipe = new Equipe.EquipeBuilder()
                            .setId(rs.getInt("constructorId"))
                            .setNome(rs.getString("constructorName"))
                            .setNacionalidade(rs.getString("constructorNationality"))
                            .build();

                    String millisStr = rs.getString("milliseconds");
                    long millis = 0;
                    if (millisStr != null && !millisStr.equals("\\N")) {
                        try {
                            millis = Long.parseLong(millisStr);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid milliseconds format: " + millisStr);
                        }
                    }

                    ResultadoCorrida resultado = new ResultadoCorrida(
                            positionInt,
                            millis,
                            rs.getInt("statusId"),
                            equipe
                    );

                    corridaBuilder.addResultado(piloto, resultado);
                }

                if (corridaBuilder == null) {
                    throw new RuntimeException("Corrida não encontrada para ano=" + ano + " e round=" + round);
                }

                return corridaBuilder.build();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar corrida: " + e.getMessage(), e);
        }
    }

    public Corrida buscarCorridaBasica(int ano, int round) {
        String sql = """
        SELECT r.raceId, r.name, r.date, r.time,
               c.name AS circuitName, c.country
        FROM races r
        JOIN circuits c ON r.circuitId = c.circuitId
        WHERE r.year = ? AND r.round = ?
    """;

        try (Connection conn = dbManager.getConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ano);
            stmt.setInt(2, round);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Corrida.CorridaBuilder()
                            .setId(rs.getInt("raceId"))
                            .setAno(ano)
                            .setRound(round)
                            .setNome(rs.getString("name"))
                            .setCircuito(rs.getString("circuitName"), rs.getString("country"))
                            .setDataHora(LocalDateTime.of(
                                    LocalDate.parse(rs.getString("date")),
                                    rs.getString("time") != null ? LocalTime.parse(rs.getString("time")) : LocalTime.MIDNIGHT
                            ))
                            .build();
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar informações básicas da corrida: " + e.getMessage(), e);
        }
    }

    private String getNullSafeString(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return (value != null && !value.equals("\\N")) ? value : "";
    }



    public void verificarEstrutura() {
        String sql = "SELECT * FROM races LIMIT 1";
        try (Connection conn = dbManager.getConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            System.out.println("\nEstrutura da tabela races:");
            for (int i = 1; i <= columnCount; i++) {
                System.out.printf("- %s (%s)%n",
                        metaData.getColumnName(i),
                        metaData.getColumnTypeName(i));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar estrutura: " + e.getMessage(), e);
        }
    }

    public void verificarDados() {
        String sql = "SELECT * FROM races LIMIT 1";
        try (Connection conn = dbManager.getConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                System.out.println("\nPrimeira linha da tabela races:");
                for (int i = 1; i <= columnCount; i++) {
                    System.out.printf("%s: %s%n",
                            metaData.getColumnName(i),
                            rs.getString(i));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar dados: " + e.getMessage(), e);
        }
    }


}
