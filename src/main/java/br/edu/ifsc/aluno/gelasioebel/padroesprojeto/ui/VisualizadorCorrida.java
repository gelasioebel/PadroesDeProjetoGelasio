package br.edu.ifsc.aluno.gelasioebel.padroesprojeto.ui;

import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.SimulacaoListener;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.SimuladorF1Facade;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.estado.EstadoPiloto;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Corrida;
import br.edu.ifsc.aluno.gelasioebel.padroesprojeto.modelo.Piloto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VisualizadorCorrida extends JFrame implements SimulacaoListener {
    private static final int FRAME_WIDTH = 1200;
    private static final int FRAME_HEIGHT = 800;
    private static final int UPDATE_INTERVAL = 32; // ~60 FPS
    private final SimuladorF1Facade simulador;
    private final Map<String, Color> coresPilotos;
    private Timer timer;
    private JPanel painelCorrida;
    private JComboBox<Integer> seletorAno;
    private JComboBox<String> seletorCorrida;
    private JComboBox<String> seletorVelocidade;
    private JLabel labelInfo;
    private JButton btnIniciar;
    private JButton btnPausar;
    private JButton btnParar;
    private volatile boolean isRunning;
    private Map<String, Integer> corridasAnoAtual; // Novo campo para mapear nomes para rounds

    public VisualizadorCorrida() {
        this.simulador = SimuladorF1Facade.getInstancia();
        this.coresPilotos = new ConcurrentHashMap<>();
        this.isRunning = false;
        this.corridasAnoAtual = new HashMap<>();

        this.btnIniciar = new JButton("Iniciar");
        this.btnPausar = new JButton("Pausar");
        this.btnParar = new JButton("Parar");

        inicializarCoresPilotos();
        simulador.addListener(this);
        inicializarInterface();
        configurarEventos();
    }

    private void atualizarCorridas(int ano) {
        seletorCorrida.removeAllItems();
        corridasAnoAtual.clear();
        try {
            List<Integer> rounds = simulador.getRoundsDoAno(ano);
            for (Integer round : rounds) {
                Corrida corrida = simulador.getCorridaInfo(ano, round);
                if (corrida != null) {
                    String nomeCorrida = corrida.getNome();
                    seletorCorrida.addItem(nomeCorrida);
                    corridasAnoAtual.put(nomeCorrida, round);
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao carregar corridas: " + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }


    private synchronized void iniciarSimulacao() {
        if (seletorAno.getSelectedItem() == null || seletorCorrida.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um ano e uma corrida primeiro!",
                    "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int ano = (Integer) seletorAno.getSelectedItem();
            String nomeCorrida = (String) seletorCorrida.getSelectedItem();
            Integer round = corridasAnoAtual.get(nomeCorrida);

            if (round == null) {
                throw new IllegalArgumentException("Corrida não encontrada: " + nomeCorrida);
            }

            String velStr = (String) seletorVelocidade.getSelectedItem();
            if (velStr == null || !velStr.endsWith("x")) {
                throw new IllegalArgumentException("Formato de velocidade inválido");
            }

            double velocidade = Double.parseDouble(velStr.replace("x", ""));
            if (velocidade <= 0) {
                throw new IllegalArgumentException("Velocidade deve ser maior que zero");
            }

            simulador.carregarCorrida(ano, round);
            simulador.setVelocidade(velocidade);
            simulador.iniciar();

            if (timer != null) {
                timer.stop();
            }

            timer = new Timer(UPDATE_INTERVAL, e -> {
                try {
                    simulador.atualizarSimulacao(UPDATE_INTERVAL);
                } catch (Exception ex) {
                    pararSimulacao();
                    JOptionPane.showMessageDialog(this,
                            "Erro durante simulação: " + ex.getMessage(),
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
            timer.start();

            Corrida corrida = simulador.getCorridaAtual();
            if (corrida != null) {
                labelInfo.setText(String.format("%s - %s, %s",
                        corrida.getNome(), corrida.getCircuito(), corrida.getPais()));
            }

            isRunning = true;
            atualizarEstadoBotoes();

            // Desabilitar seletores durante a simulação
            seletorAno.setEnabled(false);
            seletorCorrida.setEnabled(false);
            seletorVelocidade.setEnabled(false);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao iniciar simulação: " + e.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private synchronized void pausarSimulacao() {
        if (timer != null) {
            timer.stop();
        }
        simulador.pausar();
        isRunning = false;
        atualizarEstadoBotoes();

        // Manter seletores desabilitados durante a pausa
        seletorAno.setEnabled(false);
        seletorCorrida.setEnabled(false);
        seletorVelocidade.setEnabled(true); // Permitir mudança de velocidade durante pausa
    }

    private synchronized void pararSimulacao() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }

        simulador.parar();
        isRunning = false;

        // Limpar informações
        labelInfo.setText("Selecione uma corrida para começar");

        // Reabilitar seletores
        seletorAno.setEnabled(true);
        seletorCorrida.setEnabled(true);
        seletorVelocidade.setEnabled(true);

        // Atualizar estado dos botões
        atualizarEstadoBotoes();

        // Forçar repaint do painel da corrida
        painelCorrida.repaint();
    }

    private void atualizarEstadoBotoes() {
        SwingUtilities.invokeLater(() -> {
            btnIniciar.setEnabled(!isRunning);
            btnPausar.setEnabled(isRunning);
            btnParar.setEnabled(isRunning || simulador.getCorridaAtual() != null);

            // Atualizar texto do botão iniciar
            btnIniciar.setText(isRunning ? "Continuar" : "Iniciar");
        });
    }


    // [Resto dos métodos permanecem iguais...]


    private void inicializarCoresPilotos() {
        // Cores por equipe (baseadas nas cores oficiais de 2024)
        Map<String, Color> cores = new HashMap<>();
        cores.put("VER", new Color(22, 20, 140));    // Red Bull
        cores.put("PER", new Color(22, 20, 140));
        cores.put("HAM", new Color(0, 144, 255));    // Mercedes
        cores.put("RUS", new Color(0, 144, 255));
        cores.put("LEC", new Color(220, 0, 0));      // Ferrari
        cores.put("SAI", new Color(220, 0, 0));
        cores.put("NOR", new Color(255, 135, 0));    // McLaren
        cores.put("PIA", new Color(255, 135, 0));
        cores.put("ALO", new Color(0, 111, 98));     // Aston Martin
        cores.put("STR", new Color(0, 111, 98));
        cores.put("GAS", new Color(47, 128, 237));   // Alpine
        cores.put("OCO", new Color(47, 128, 237));
        cores.put("BOT", new Color(144, 0, 0));      // Sauber
        cores.put("ZHO", new Color(144, 0, 0));
        cores.put("RIC", new Color(43, 69, 98));     // RB
        cores.put("TSU", new Color(43, 69, 98));
        cores.put("MAG", new Color(100, 100, 100));  // Haas
        cores.put("HUL", new Color(100, 100, 100));
        cores.put("ALB", new Color(0, 90, 255));     // Williams
        cores.put("SAR", new Color(0, 90, 255));

        //a
        cores.put("HAM", new Color(43, 69, 98));
        cores.put("HEI", new Color(43, 69, 98));
        cores.put("ROS", new Color(43, 69, 98));
        cores.put("ALO", new Color(43, 69, 98));
        cores.put("KOV", new Color(43, 69, 98));
        cores.put("NAK", new Color(43, 69, 98));
        cores.put("BOU", new Color(43, 69, 98));
        cores.put("RAI", new Color(43, 69, 98));
        cores.put("KUB", new Color(43, 69, 98));
        cores.put("GLO", new Color(43, 69, 98));
        cores.put("SAT", new Color(43, 69, 98));
        cores.put("PIQ", new Color(43, 69, 98));
        cores.put("MAS", new Color(43, 69, 98));
        cores.put("COU", new Color(43, 69, 98));
        cores.put("TRU", new Color(43, 69, 98));
        cores.put("SUT", new Color(43, 69, 98));
        cores.put("WEB", new Color(43, 69, 98));
        cores.put("BUT", new Color(43, 69, 98));
        cores.put("DAV", new Color(43, 69, 98));
        cores.put("VET", new Color(43, 69, 98));
        cores.put("FIS", new Color(43, 69, 98));
        cores.put("BAR", new Color(43, 69, 98));
        cores.put("SCH", new Color(43, 69, 98));
        cores.put("LIU", new Color(43, 69, 98));
        cores.put("WUR", new Color(43, 69, 98));
        cores.put("SPE", new Color(43, 69, 98));
        cores.put("ALB", new Color(43, 69, 98));
        cores.put("WIN", new Color(43, 69, 98));
        cores.put("YAM", new Color(43, 69, 98));
        cores.put("MSC", new Color(43, 69, 98));
        cores.put("MON", new Color(43, 69, 98));
        cores.put("KLI", new Color(43, 69, 98));
        cores.put("TMO", new Color(43, 69, 98));
        cores.put("IDE", new Color(43, 69, 98));
        cores.put("VIL", new Color(43, 69, 98));
        cores.put("FMO", new Color(43, 69, 98));
        cores.put("DLR", new Color(43, 69, 98));
        cores.put("DOO", new Color(43, 69, 98));
        cores.put("KAR", new Color(43, 69, 98));
        cores.put("FRI", new Color(43, 69, 98));
        cores.put("ZON", new Color(43, 69, 98));
        cores.put("PIZ", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BUE", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BAD", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("MAG", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("ALG", new Color(43, 69, 98));
        cores.put("GRO", new Color(43, 69, 98));
        cores.put("KOB", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BIA", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("GAS", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("HUL", new Color(43, 69, 98));
        cores.put("PET", new Color(43, 69, 98));
        cores.put("DIG", new Color(43, 69, 98));
        cores.put("SEN", new Color(43, 69, 98));
        cores.put("CHA", new Color(43, 69, 98));
        cores.put("MAL", new Color(43, 69, 98));
        cores.put("DIR", new Color(43, 69, 98));
        cores.put("PER", new Color(43, 69, 98));
        cores.put("DAM", new Color(43, 69, 98));
        cores.put("RIC", new Color(43, 69, 98));
        cores.put("VER", new Color(43, 69, 98));
        cores.put("PIC", new Color(43, 69, 98));
        cores.put("CHI", new Color(43, 69, 98));
        cores.put("GUT", new Color(43, 69, 98));
        cores.put("BOT", new Color(43, 69, 98));
        cores.put("VDG", new Color(43, 69, 98));
        cores.put("BIA", new Color(43, 69, 98));
        cores.put("MAG", new Color(43, 69, 98));
        cores.put("KVY", new Color(43, 69, 98));
        cores.put("LOT", new Color(43, 69, 98));
        cores.put("ERI", new Color(43, 69, 98));
        cores.put("STE", new Color(43, 69, 98));
        cores.put("VER", new Color(43, 69, 98));
        cores.put("NAS", new Color(43, 69, 98));
        cores.put("SAI", new Color(43, 69, 98));
        cores.put("MER", new Color(43, 69, 98));
        cores.put("RSS", new Color(43, 69, 98));
        cores.put("PAL", new Color(43, 69, 98));
        cores.put("WEH", new Color(43, 69, 98));
        cores.put("HAR", new Color(43, 69, 98));
        cores.put("VAN", new Color(43, 69, 98));
        cores.put("OCO", new Color(43, 69, 98));
        cores.put("STR", new Color(43, 69, 98));
        cores.put("GIO", new Color(43, 69, 98));
        cores.put("HAR", new Color(43, 69, 98));
        cores.put("LEC", new Color(43, 69, 98));
        cores.put("SIR", new Color(43, 69, 98));
        cores.put("NOR", new Color(43, 69, 98));
        cores.put("RUS", new Color(43, 69, 98));
        cores.put("ALB", new Color(43, 69, 98));
        cores.put("LAT", new Color(43, 69, 98));
        cores.put("FIT", new Color(43, 69, 98));
        cores.put("AIT", new Color(43, 69, 98));
        cores.put("TSU", new Color(43, 69, 98));
        cores.put("MAZ", new Color(43, 69, 98));
        cores.put("MSC", new Color(43, 69, 98));
        cores.put("ZHO", new Color(43, 69, 98));
        cores.put("DEV", new Color(43, 69, 98));
        cores.put("PIA", new Color(43, 69, 98));
        cores.put("SAR", new Color(43, 69, 98));
        cores.put("LAW", new Color(43, 69, 98));
        cores.put("BEA", new Color(43, 69, 98));
        cores.put("COL", new Color(43, 69, 98));

        cores.put("HAM", new Color(43, 69, 98));
        cores.put("HEI", new Color(43, 69, 98));
        cores.put("ROS", new Color(43, 69, 98));
        cores.put("ALO", new Color(43, 69, 98));
        cores.put("KOV", new Color(43, 69, 98));
        cores.put("NAK", new Color(43, 69, 98));
        cores.put("BOU", new Color(43, 69, 98));
        cores.put("RAI", new Color(43, 69, 98));
        cores.put("KUB", new Color(43, 69, 98));
        cores.put("GLO", new Color(43, 69, 98));
        cores.put("SAT", new Color(43, 69, 98));
        cores.put("PIQ", new Color(43, 69, 98));
        cores.put("MAS", new Color(43, 69, 98));
        cores.put("COU", new Color(43, 69, 98));
        cores.put("TRU", new Color(43, 69, 98));
        cores.put("SUT", new Color(43, 69, 98));
        cores.put("WEB", new Color(43, 69, 98));
        cores.put("BUT", new Color(43, 69, 98));
        cores.put("DAV", new Color(43, 69, 98));
        cores.put("VET", new Color(43, 69, 98));
        cores.put("FIS", new Color(43, 69, 98));
        cores.put("BAR", new Color(43, 69, 98));
        cores.put("SCH", new Color(43, 69, 98));
        cores.put("LIU", new Color(43, 69, 98));
        cores.put("WUR", new Color(43, 69, 98));
        cores.put("SPE", new Color(43, 69, 98));
        cores.put("ALB", new Color(43, 69, 98));
        cores.put("WIN", new Color(43, 69, 98));
        cores.put("YAM", new Color(43, 69, 98));
        cores.put("MSC", new Color(43, 69, 98));
        cores.put("MON", new Color(43, 69, 98));
        cores.put("KLI", new Color(43, 69, 98));
        cores.put("TMO", new Color(43, 69, 98));
        cores.put("IDE", new Color(43, 69, 98));
        cores.put("VIL", new Color(43, 69, 98));
        cores.put("FMO", new Color(43, 69, 98));
        cores.put("DLR", new Color(43, 69, 98));
        cores.put("DOO", new Color(43, 69, 98));
        cores.put("KAR", new Color(43, 69, 98));
        cores.put("FRI", new Color(43, 69, 98));
        cores.put("ZON", new Color(43, 69, 98));
        cores.put("PIZ", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BUE", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BAD", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("MAG", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("ALG", new Color(43, 69, 98));
        cores.put("GRO", new Color(43, 69, 98));
        cores.put("KOB", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("BIA", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("GAS", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("\\N", new Color(43, 69, 98));
        cores.put("HUL", new Color(43, 69, 98));
        cores.put("PET", new Color(43, 69, 98));
        cores.put("DIG", new Color(43, 69, 98));
        cores.put("SEN", new Color(43, 69, 98));
        cores.put("CHA", new Color(43, 69, 98));
        cores.put("MAL", new Color(43, 69, 98));
        cores.put("DIR", new Color(43, 69, 98));
        cores.put("PER", new Color(43, 69, 98));
        cores.put("DAM", new Color(43, 69, 98));
        cores.put("RIC", new Color(43, 69, 98));
        cores.put("VER", new Color(43, 69, 98));
        cores.put("PIC", new Color(43, 69, 98));
        cores.put("CHI", new Color(43, 69, 98));
        cores.put("GUT", new Color(43, 69, 98));
        cores.put("BOT", new Color(43, 69, 98));
        cores.put("VDG", new Color(43, 69, 98));
        cores.put("BIA", new Color(43, 69, 98));
        cores.put("MAG", new Color(43, 69, 98));
        cores.put("KVY", new Color(43, 69, 98));
        cores.put("LOT", new Color(43, 69, 98));
        cores.put("ERI", new Color(43, 69, 98));
        cores.put("STE", new Color(43, 69, 98));
        cores.put("VER", new Color(43, 69, 98));
        cores.put("NAS", new Color(43, 69, 98));
        cores.put("SAI", new Color(43, 69, 98));
        cores.put("MER", new Color(43, 69, 98));
        cores.put("RSS", new Color(43, 69, 98));
        cores.put("PAL", new Color(43, 69, 98));
        cores.put("WEH", new Color(43, 69, 98));
        cores.put("HAR", new Color(43, 69, 98));
        cores.put("VAN", new Color(43, 69, 98));
        cores.put("OCO", new Color(43, 69, 98));
        cores.put("STR", new Color(43, 69, 98));
        cores.put("GIO", new Color(43, 69, 98));
        cores.put("HAR", new Color(43, 69, 98));
        cores.put("LEC", new Color(43, 69, 98));
        cores.put("SIR", new Color(43, 69, 98));
        cores.put("NOR", new Color(43, 69, 98));
        cores.put("RUS", new Color(43, 69, 98));
        cores.put("ALB", new Color(43, 69, 98));
        cores.put("LAT", new Color(43, 69, 98));
        cores.put("FIT", new Color(43, 69, 98));
        cores.put("AIT", new Color(43, 69, 98));
        cores.put("TSU", new Color(43, 69, 98));
        cores.put("MAZ", new Color(43, 69, 98));
        cores.put("MSC", new Color(43, 69, 98));
        cores.put("ZHO", new Color(43, 69, 98));
        cores.put("DEV", new Color(43, 69, 98));
        cores.put("PIA", new Color(43, 69, 98));
        cores.put("SAR", new Color(43, 69, 98));
        cores.put("LAW", new Color(43, 69, 98));
        cores.put("BEA", new Color(43, 69, 98));
        cores.put("COL", new Color(43, 69, 98));
        Color defaultColor = new Color(255, 140, 0); // Orange
        cores.put("DEFAULT", defaultColor);

        coresPilotos.putAll(cores);
    }

    private void configurarEventos() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pararSimulacao();
                if (timer != null) {
                    timer.stop();
                    timer = null;
                }
                simulador.removeListener(VisualizadorCorrida.this);
            }
        });

        ComponentAdapter resizeListener = new ComponentAdapter() {
            private Timer resizeTimer;

            @Override
            public void componentResized(ComponentEvent e) {
                if (resizeTimer != null && resizeTimer.isRunning()) {
                    resizeTimer.restart();
                } else {
                    resizeTimer = new Timer(150, evt -> {
                        painelCorrida.revalidate();
                        painelCorrida.repaint();
                        ((Timer) evt.getSource()).stop();
                    });
                    resizeTimer.setRepeats(false);
                    resizeTimer.start();
                }
            }
        };

        addComponentListener(resizeListener);
    }

    private void inicializarInterface() {
        setTitle("Simulador F1 - IFSC");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 600));

        // Usar BorderLayout para organização principal
        setLayout(new BorderLayout(10, 10));

        // Painel superior para controles
        JPanel painelSuperior = new JPanel(new BorderLayout(10, 10));
        painelSuperior.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Adicionar controles e info
        painelSuperior.add(criarPainelControles(), BorderLayout.NORTH);
        painelSuperior.add(criarPainelInfo(), BorderLayout.CENTER);

        // Criar painel de corrida com scroll
        painelCorrida = criarPainelCorrida();
        JScrollPane scrollPane = new JScrollPane(painelCorrida);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // Adicionar painéis ao frame
        add(painelSuperior, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Inicializar seletores
        if (seletorAno.getItemCount() > 0) {
            atualizarCorridas((Integer) seletorAno.getSelectedItem());
        }
    }

    private JPanel criarPainelControles() {
        JPanel painelControles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        painelControles.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Controles"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Initialize selectors
        List<Integer> anosDisponiveis = simulador.getAnosDisponiveis();
        seletorAno = new JComboBox<>(anosDisponiveis.toArray(new Integer[0]));
        seletorCorrida = new JComboBox<>();
        seletorVelocidade = new JComboBox<>(new String[]{"1x", "2x", "5x", "10x", "50x", "100x", "1000x"});

        // Configure components
        Dimension seletorSize = new Dimension(100, 25);
        seletorAno.setPreferredSize(seletorSize);
        seletorCorrida.setPreferredSize(new Dimension(250, 25)); // Maior para nomes de corridas
        seletorVelocidade.setPreferredSize(new Dimension(80, 25));

        // Add listeners
        seletorAno.addActionListener(e -> {
            if (seletorAno.getSelectedItem() != null) {
                atualizarCorridas((Integer) seletorAno.getSelectedItem());
            }
        });

        // Configure buttons
        btnIniciar.addActionListener(e -> iniciarSimulacao());
        btnPausar.addActionListener(e -> pausarSimulacao());
        btnParar.addActionListener(e -> pararSimulacao());

        // Initial button states
        btnPausar.setEnabled(false);
        btnParar.setEnabled(false);

        // Add components to panel
        painelControles.add(new JLabel("Ano:"));
        painelControles.add(seletorAno);
        painelControles.add(new JLabel("Corrida:"));
        painelControles.add(seletorCorrida);
        painelControles.add(new JLabel("Velocidade:"));
        painelControles.add(seletorVelocidade);
        painelControles.add(Box.createHorizontalStrut(20));
        painelControles.add(btnIniciar);
        painelControles.add(btnPausar);
        painelControles.add(btnParar);

        return painelControles;
    }




    private JButton criarBotao(String texto, Color cor, ActionListener action) {
        JButton botao = new JButton(texto);
        botao.setBackground(cor);
        botao.setForeground(Color.WHITE);
        botao.setFocusPainted(false);
        botao.addActionListener(action);
        return botao;
    }

    private JPanel criarPainelInfo() {
        JPanel painelInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelInfo.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        labelInfo = new JLabel("Selecione uma corrida para começar");
        labelInfo.setFont(new Font("Arial", Font.BOLD, 14));
        painelInfo.add(labelInfo);

        return painelInfo;
    }

    private JPanel criarPainelCorrida() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                desenharCorrida(g);
            }

            @Override
            public Dimension getPreferredSize() {
                int height = Math.max(400, simulador.getEstadosPilotos().size() * 40 + 60);
                return new Dimension(getWidth(), height);
            }
        };
    }


    @Override
    public void onSimulacaoAtualizada(SimuladorF1Facade simulador) {
        SwingUtilities.invokeLater(() -> {
            // Verificar se todos os pilotos terminaram
            boolean todosTerminaram = true;
            for (EstadoPiloto estado : simulador.getEstadosPilotos().values()) {
                if (!estado.isTerminado()) {
                    todosTerminaram = false;
                    break;
                }
            }

            // Se todos terminaram, parar a simulação automaticamente
            if (todosTerminaram && isRunning) {
                timer.stop();
                isRunning = false;
                atualizarEstadoBotoes();
            }

            painelCorrida.revalidate();
            painelCorrida.repaint();
        });
    }

    private void desenharCorrida(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            configurarRenderizacao(g2d);
            Map<Piloto, EstadoPiloto> estados = simulador.getEstadosPilotos();

            if (estados.isEmpty()) {
                return;
            }

            int y = 30;
            int margemEsquerda = 20;
            int larguraPiloto = 60;
            int larguraMaxima = painelCorrida.getWidth() - 250;

            desenharCabecalho(g2d, margemEsquerda, y, larguraMaxima);
            y += 30;

            List<Map.Entry<Piloto, EstadoPiloto>> pilotosOrdenados = new ArrayList<>(estados.entrySet());
            pilotosOrdenados.sort((a, b) -> Double.compare(b.getValue().getProgresso(),
                    a.getValue().getProgresso()));
            pilotosOrdenados.sort((a, b) -> Double.compare(b.getValue().getProgresso(),
                    a.getValue().getProgresso()));

            int posicao = 1;
            for (Map.Entry<Piloto, EstadoPiloto> entry : pilotosOrdenados) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    desenharLinhaPiloto(g2d, entry.getKey(), entry.getValue(), posicao,
                            margemEsquerda, y, larguraPiloto, larguraMaxima);
                    y += 35;
                    posicao++;
                }
            }
        } finally {
            g2d.dispose();
        }
    }

    private void configurarRenderizacao(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }

    private void desenharCabecalho(Graphics2D g2d, int x, int y, int larguraMaxima) {
        Font boldFont = new Font("Arial", Font.BOLD, 14);
        g2d.setFont(boldFont);
        g2d.setColor(Color.BLACK);

        FontMetrics fm = g2d.getFontMetrics();
        int alturaLinha = fm.getHeight();

        // Draw header text
        g2d.drawString("POS", x, y);
        g2d.drawString("PILOTO", x + 60, y);
        g2d.drawString("PROGRESSO", x + 150, y);
        g2d.drawString("STATUS", larguraMaxima + 50, y);

        // Draw separator line
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawLine(x, y + 5, larguraMaxima + 200, y + 5);
    }

    private void desenharLinhaPiloto(Graphics2D g2d, Piloto piloto, EstadoPiloto estado,
                                     int posicao, int x, int y, int larguraPiloto,
                                     int larguraMaxima) {
        try {
            // Draw position number
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(String.format("%2d", posicao), x, y);

            // Get pilot code and color
            String codigo = piloto.getCodigo();
            Color corPiloto = codigo != null && !codigo.trim().isEmpty() ?
                    coresPilotos.getOrDefault(codigo, coresPilotos.get("DEFAULT")) :
                    coresPilotos.get("DEFAULT");

            // Draw color bar
            g2d.setColor(corPiloto);
            g2d.fillRect(x + 50, y - 15, 3, 20);

            // Draw pilot code or placeholder
            g2d.setColor(Color.BLACK);
            String displayCode = codigo != null && !codigo.trim().isEmpty() ?
                    codigo : "???";
            g2d.drawString(displayCode, x + 60, y);

            // Rest of the drawing code...
            desenharBarraProgresso(g2d, estado, x + 150, y - 12, larguraMaxima - 200, 16,
                    codigo);

            g2d.setColor(Color.BLACK);
            String status = estado.getStatus();
            String emoji = estado.getEmoji();
            if (status != null && emoji != null) {
                g2d.drawString(status + " " + emoji, larguraMaxima + 50, y);
            }

            g2d.setColor(new Color(240, 240, 240));
            g2d.drawLine(x, y + 10, larguraMaxima + 200, y + 10);

        } catch (Exception e) {
            System.err.println("Erro ao desenhar linha do piloto: " + e.getMessage());
        }
    }

    private void desenharBarraProgresso(Graphics2D g2d, EstadoPiloto estado,
                                        int x, int y, int larguraTotal, int altura,
                                        String codigoPiloto) {
        if (estado == null) return;

        // Background
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(x, y, larguraTotal, altura);

        // Progress bar
        int larguraProgresso = (int) (estado.getProgresso() * larguraTotal);

        // Use default color (orange) if code is null
        Color corPiloto = codigoPiloto != null && !codigoPiloto.trim().isEmpty() ?
                coresPilotos.getOrDefault(codigoPiloto, coresPilotos.get("DEFAULT")) :
                coresPilotos.get("DEFAULT");

        g2d.setColor(corPiloto.darker());
        g2d.fillRect(x, y, larguraProgresso, altura);

        // Border
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawRect(x, y, larguraTotal, altura);

        // Progress text
        String progresso = String.format("%.1f%%", estado.getProgresso() * 100);
        FontMetrics fm = g2d.getFontMetrics();
        int larguraTexto = fm.stringWidth(progresso);
        int xTexto = x + (larguraProgresso / 2) - (larguraTexto / 2);

        // Adjust text position and color based on progress width
        if (larguraProgresso < larguraTexto + 10) {
            xTexto = x + larguraProgresso + 5;
            g2d.setColor(Color.BLACK);
        } else {
            g2d.setColor(Color.WHITE);
        }

        int yTexto = y + ((altura + fm.getAscent() - fm.getDescent()) / 2);
        g2d.drawString(progresso, xTexto, yTexto);
    }

}