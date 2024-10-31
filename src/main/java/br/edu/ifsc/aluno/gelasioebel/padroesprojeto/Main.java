package br.edu.ifsc.aluno.gelasioebel.padroesprojeto;

import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.dao.DatabaseManager;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.ui.VisualizadorCorrida;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe principal da aplicação Simulador F1.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String APP_NAME = "Simulador F1 - IFSC";
    private static final String CONFIG_FILE = "/config.properties";
    private static final int MAX_STARTUP_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Ponto de entrada principal da aplicação.
     *
     * @param args argumentos da linha de comando (não utilizados)
     */
    public static void main(String[] args) {
        configurarLogger();
        LOGGER.info("Iniciando aplicação " + APP_NAME);

        // Configura tratamento de exceções não capturadas
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.severe("Erro não tratado em " + thread.getName() + ": " + throwable.getMessage());
            mostrarErroFatal("Erro não tratado", throwable);
        });

        // Tenta iniciar a aplicação com retentativas
        for (int tentativa = 1; tentativa <= MAX_STARTUP_ATTEMPTS; tentativa++) {
            try {
                iniciarAplicacao();
                return;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Tentativa " + tentativa + " falhou", e);
                if (tentativa < MAX_STARTUP_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.severe("Interrompido durante espera para nova tentativa");
                        mostrarErroFatal("Inicialização interrompida", ie);
                        return;
                    }
                } else {
                    mostrarErroFatal("Falha ao iniciar aplicação", e);
                }
            }
        }
    }

    private static void iniciarAplicacao() throws Exception {
        // Carrega configurações
        Properties config = carregarConfiguracoes();

        // Configura look and feel
        configurarLookAndFeel();

        // Inicializa banco de dados
        DatabaseManager dbManager = DatabaseManager.getInstancia();
        if (!dbManager.verificarConexao()) {
            throw new RuntimeException("Não foi possível conectar ao banco de dados");
        }

        // Verifica estrutura do banco
        verificarEstruturaBancoDados(dbManager);

        // Inicia a interface gráfica no EDT
        SwingUtilities.invokeLater(() -> {
            try {
                LOGGER.info("Criando interface gráfica");
                VisualizadorCorrida visualizador = new VisualizadorCorrida();
                configurarJanelaPrincipal(visualizador);
                visualizador.setVisible(true);
                LOGGER.info("Aplicação iniciada com sucesso");
            } catch (Exception e) {
                LOGGER.severe("Erro ao criar interface gráfica: " + e.getMessage());
                mostrarErro("Erro ao iniciar aplicação", e);
            }
        });
    }

    private static void configurarLogger() {
        try {
            // Aqui você pode adicionar configuração personalizada do logger
            LOGGER.setLevel(Level.ALL);
        } catch (Exception e) {
            System.err.println("Erro ao configurar logger: " + e.getMessage());
        }
    }

    private static Properties carregarConfiguracoes() throws IOException {
        Properties config = new Properties();
        try (var input = Main.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                config.load(input);
                LOGGER.info("Configurações carregadas com sucesso");
            } else {
                LOGGER.warning("Arquivo de configuração não encontrado, usando valores padrão");
            }
        }
        return config;
    }

    private static void configurarLookAndFeel() {
        try {
            // Tenta usar o look and feel do sistema
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // Configura fonte padrão para melhor legibilidade
            setUIFont(new FontUIResource("Arial", Font.PLAIN, 12));

            LOGGER.info("Look and feel configurado com sucesso");
        } catch (Exception e) {
            LOGGER.warning("Não foi possível configurar look and feel: " + e.getMessage());
        }
    }

    private static void setUIFont(FontUIResource f) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }

    private static void verificarEstruturaBancoDados(DatabaseManager dbManager) {
        LOGGER.info("Verificando estrutura do banco de dados");
        dbManager.mostrarEstruturaDaTabela("races");
        dbManager.mostrarConteudoTabela("races");
    }

    private static void configurarJanelaPrincipal(VisualizadorCorrida visualizador) {
        visualizador.setLocationRelativeTo(null);

        // Configura comportamento ao fechar
        visualizador.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        visualizador.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmarESair(visualizador);
            }
        });
    }

    private static void confirmarESair(Window janela) {
        int resposta = JOptionPane.showConfirmDialog(
                janela,
                "Deseja realmente sair da aplicação?",
                "Confirmar Saída",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (resposta == JOptionPane.YES_OPTION) {
            LOGGER.info("Encerrando aplicação");
            janela.dispose();
            System.exit(0);
        }
    }

    private static void mostrarErro(String mensagem, Exception e) {
        LOGGER.log(Level.SEVERE, mensagem, e);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    null,
                    mensagem + ":\n" + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE
            );
        });
    }

    private static void mostrarErroFatal(String mensagem, Throwable e) {
        LOGGER.log(Level.SEVERE, mensagem, e);
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    null,
                    mensagem + ":\n" + e.getMessage() + "\n\nA aplicação será encerrada.",
                    "Erro Fatal",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        });
    }
}