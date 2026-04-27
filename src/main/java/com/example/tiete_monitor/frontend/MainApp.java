package com.example.tiete_monitor.frontend;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Cliente Frontend para o Sistema de Monitoramento Ambiental - APS.
 * Escrito 100% em Java nativo via JavaFX, com design minimalista (estilo macOS).
 * Lida com Sockets TCP/IP em threads separadas para não travar a UI.
 */
public class MainApp extends Application {

    // Constantes de Rede
    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    // Recursos de Rede
    private Socket socket;
    private PrintWriter saida;
    private BufferedReader entrada;
    private String nomeInspetor;

    // Elementos de UI Compartilhados
    private Stage primaryStage;
    private Scene loginScene;
    private Scene chatScene;

    // Componentes do Chat
    private VBox chatBox;
    private TextField inputField;
    private Label labelStatusCabecalho;

    // Paleta de Cores (Design System)
    private final String BG_COLOR = "#F5F5F7"; // Cinza gelo macOS
    private final String GREEN_ACCENT = "#34C759"; // Verde iMessage
    private final String TEXT_DARK = "#1D1D1F"; // Cinza chumbo

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Define o evento de fechamento da janela para encerrar o socket com segurança
        primaryStage.setOnCloseRequest(e -> desconectar());

        buildLoginScene();
        buildChatScene();

        primaryStage.setTitle("Sistema de Monitoramento - Rio Tietê");
        primaryStage.setScene(loginScene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(700);
        primaryStage.show();
    }

    // ========================================================================
    // TELA 1: LOGIN (Identificação)
    // ========================================================================
    private void buildLoginScene() {
        VBox rootLogin = new VBox(20);
        rootLogin.setAlignment(Pos.CENTER);
        rootLogin.setStyle("-fx-background-color: " + BG_COLOR + "; -fx-font-family: 'Segoe UI', -apple-system, sans-serif;");

        Label lblTitulo = new Label("Monitoramento Ambiental");
        lblTitulo.setFont(Font.font("System", FontWeight.BOLD, 28));
        lblTitulo.setStyle("-fx-text-fill: " + TEXT_DARK + ";");

        Label lblSubtitulo = new Label("Acesso do Inspetor - Rio Tietê");
        lblSubtitulo.setStyle("-fx-text-fill: #86868B; -fx-font-size: 14px;");

        TextField txtNome = new TextField();
        txtNome.setPromptText("Digite seu nome de inspetor...");
        txtNome.setMaxWidth(300);
        txtNome.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 12 15; " +
                        "-fx-font-size: 14px; " +
                        "-fx-border-color: #E5E5EA; " +
                        "-fx-border-radius: 12;"
        );

        Button btnConectar = new Button("Conectar");
        btnConectar.setPrefWidth(300);
        btnConectar.setStyle(
                "-fx-background-color: " + GREEN_ACCENT + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 12; " +
                        "-fx-padding: 12; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-cursor: hand;"
        );

        Label lblErro = new Label();
        lblErro.setStyle("-fx-text-fill: #FF3B30; -fx-font-size: 12px;");

        // Ação de Conectar
        btnConectar.setOnAction(e -> {
            String nomeDigitado = txtNome.getText().trim();
            if (!nomeDigitado.isEmpty()) {
                boolean sucesso = conectarAoServidor(nomeDigitado);
                if (sucesso) {
                    this.nomeInspetor = nomeDigitado;
                    atualizarCabecalho();
                    primaryStage.setScene(chatScene);
                } else {
                    lblErro.setText("Erro: Servidor não encontrado em " + HOST + ":" + PORTA);
                }
            } else {
                lblErro.setText("Por favor, insira o seu nome.");
            }
        });

        // Conectar ao pressionar ENTER
        txtNome.setOnAction(e -> btnConectar.fire());

        rootLogin.getChildren().addAll(lblTitulo, lblSubtitulo, txtNome, btnConectar, lblErro);
        loginScene = new Scene(rootLogin);
    }

    // ========================================================================
    // TELA 2: CHAT (Área de Trabalho)
    // ========================================================================
    private void buildChatScene() {
        BorderPane rootChat = new BorderPane();
        rootChat.setStyle("-fx-font-family: 'Segoe UI', -apple-system, sans-serif;");

        // --- 1. BARRA LATERAL ESQUERDA ---
        VBox sideBar = new VBox();
        sideBar.setPrefWidth(260);
        sideBar.setStyle("-fx-background-color: white; -fx-border-color: transparent #E5E5EA transparent transparent; -fx-border-width: 1;");

        Label lblZonas = new Label("ZONAS DE INSPEÇÃO");
        lblZonas.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #86868B; -fx-padding: 20 0 10 20;");

        sideBar.getChildren().addAll(lblZonas,
                criarItemLista("Metalúrgica Guaianases", "Guarulhos, SP", Color.web("#FF3B30"), true),
                criarItemLista("Papeleira Tietê", "Salesópolis, SP", Color.web(GREEN_ACCENT), false),
                criarItemLista("Ind. Química ABC", "Suzano, SP", Color.web(GREEN_ACCENT), false),
                criarItemLista("Têxtil Fios D'Água", "Mogi das Cruzes, SP", Color.web("#FF9500"), false)
        );

        // --- 2. CABEÇALHO SUPERIOR ---
        VBox topHeader = new VBox(5);
        topHeader.setPadding(new Insets(15, 20, 15, 20));
        topHeader.setStyle("-fx-background-color: " + BG_COLOR + "; -fx-border-color: transparent transparent #E5E5EA transparent; -fx-border-width: 1;");

        Label lblEmpresaTitulo = new Label("Metalúrgica Guaianases - Controle de Processos");
        lblEmpresaTitulo.setFont(Font.font("System", FontWeight.BOLD, 18));
        lblEmpresaTitulo.setStyle("-fx-text-fill: " + TEXT_DARK + ";");

        labelStatusCabecalho = new Label();
        labelStatusCabecalho.setStyle("-fx-text-fill: #86868B; -fx-font-size: 12px;");

        topHeader.getChildren().addAll(lblEmpresaTitulo, labelStatusCabecalho);

        // --- 3. ÁREA CENTRAL (MENSAGENS) ---
        chatBox = new VBox(15);
        chatBox.setPadding(new Insets(20));
        chatBox.setStyle("-fx-background-color: " + BG_COLOR + ";");

        ScrollPane scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + BG_COLOR + "; -fx-border-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Rolar automaticamente para a última mensagem
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue((Double) newVal));

        // --- 4. BARRA INFERIOR (DIGITAÇÃO) ---
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new Insets(15, 20, 20, 20));
        bottomBar.setStyle("-fx-background-color: " + BG_COLOR + ";");

        inputField = new TextField();
        inputField.setPromptText("Digite o relatório online ou comando (/sair)...");
        inputField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 20; " +
                        "-fx-padding: 12 20; " +
                        "-fx-font-size: 14px; " +
                        "-fx-border-color: #E5E5EA; " +
                        "-fx-border-radius: 20; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 5, 0, 0, 2);"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS); // Ocupar todo o espaço

        Button btnEnviar = new Button("Enviar");
        btnEnviar.setPrefWidth(80);
        btnEnviar.setPrefHeight(40);
        btnEnviar.setStyle(
                "-fx-background-color: " + GREEN_ACCENT + "; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 20; " +
                        "-fx-font-weight: bold; " +
                        "-fx-cursor: hand;"
        );

        // Ações de Envio
        btnEnviar.setOnAction(e -> enviarMensagem());
        inputField.setOnAction(e -> enviarMensagem());

        bottomBar.getChildren().addAll(inputField, btnEnviar);

        // --- MONTANDO O BORDER PANE ---
        BorderPane mainContent = new BorderPane();
        mainContent.setTop(topHeader);
        mainContent.setCenter(scrollPane);
        mainContent.setBottom(bottomBar);

        rootChat.setLeft(sideBar);
        rootChat.setCenter(mainContent);

        chatScene = new Scene(rootChat);
    }

    // Helper visual para os itens da barra lateral
    private HBox criarItemLista(String nome, String local, Color statusCor, boolean selecionado) {
        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(12, 20, 12, 20));
        if (selecionado) {
            container.setStyle("-fx-background-color: #F2F2F7; -fx-background-radius: 8; -fx-margin: 5 10;");
        } else {
            container.setStyle("-fx-cursor: hand;");
        }

        Circle status = new Circle(5, statusCor);

        VBox textos = new VBox(2);
        Label lblNome = new Label(nome);
        lblNome.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_DARK + "; -fx-font-size: 13px;");
        Label lblLocal = new Label(local);
        lblLocal.setStyle("-fx-text-fill: #86868B; -fx-font-size: 11px;");
        textos.getChildren().addAll(lblNome, lblLocal);

        container.getChildren().addAll(status, textos);
        return container;
    }

    private void atualizarCabecalho() {
        labelStatusCabecalho.setText(
                String.format("Inspetor: %s | Conexão: TCP/IP Segura | Status: Alerta Crítico", this.nomeInspetor)
        );
    }

    // ========================================================================
    // LÓGICA DE REDE E CONCORRÊNCIA (CRÍTICO)
    // ========================================================================

    private boolean conectarAoServidor(String nome) {
        try {
            socket = new Socket(HOST, PORTA);
            saida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envia protocolo de login para o servidor
            saida.println(nome);

            // Inicia Thread de Leitura em background (não bloqueia UI)
            Thread threadEscuta = new Thread(this::ouvirServidor);
            threadEscuta.setDaemon(true); // Garante fechamento ao fechar o app
            threadEscuta.start();

            return true;
        } catch (IOException e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
            return false;
        }
    }

    /**
     * Roda na THREAD SECUNDÁRIA (Background).
     * Fica lendo o que o servidor envia enquanto houver conexão.
     */
    private void ouvirServidor() {
        try {
            String linhaRecebida;
            while ((linhaRecebida = entrada.readLine()) != null) {
                final String msg = linhaRecebida; // Cópia final para passar ao Lambda

                // CRÍTICO: Atualiza a interface gráfica sempre na thread do JavaFX
                Platform.runLater(() -> processarMensagemRecebida(msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> adicionarBolhaUI("[SISTEMA] Conexão encerrada pelo servidor.", false, true));
        }
    }

    /**
     * Envia a mensagem pelo Socket. A própria bolha na UI será gerada
     * quando o Servidor fizer o broadcast para nós.
     */
    private void enviarMensagem() {
        String texto = inputField.getText().trim();
        if (!texto.isEmpty() && saida != null) {
            saida.println(texto);
            inputField.clear();

            if (texto.equalsIgnoreCase("/sair")) {
                desconectar();
                Platform.exit(); // Fecha a janela do JavaFX
            }
        }
        inputField.requestFocus();
    }

    /**
     * Roda na THREAD DO JAVAFX (via Platform.runLater).
     * Analisa de quem é a mensagem para colorir/alinhar corretamente.
     */
    private void processarMensagemRecebida(String msg) {
        // Verifica se a mensagem fui eu mesmo que enviei (Formato do Servidor: "[Nome]: mensagem")
        boolean isMinha = msg.startsWith("[" + this.nomeInspetor + "]:");
        boolean isSistema = msg.startsWith("[SISTEMA]");

        String displayMsg = msg;

        // Limpa a tag "[Nome]: " se for a minha própria mensagem, para ficar igual iMessage
        if (isMinha) {
            displayMsg = msg.replaceFirst("\\[" + this.nomeInspetor + "\\]:\\s*", "");
        }

        adicionarBolhaUI(displayMsg, isMinha, isSistema);
    }

    /**
     * Constrói a bolha e adiciona no VBox do chat.
     */
    private void adicionarBolhaUI(String texto, boolean isMinha, boolean isSistema) {
        HBox linhaContainer = new HBox();
        linhaContainer.setPadding(new Insets(0, 0, 5, 0));

        Label lblMensagem = new Label(texto);
        lblMensagem.setWrapText(true);
        lblMensagem.setMaxWidth(450); // Não deixa a bolha ocupar a tela toda
        lblMensagem.setPadding(new Insets(12, 16, 12, 16));
        lblMensagem.setFont(Font.font("System", 14));

        if (isSistema) {
            linhaContainer.setAlignment(Pos.CENTER);
            lblMensagem.setStyle("-fx-background-color: transparent; -fx-text-fill: #86868B; -fx-font-size: 12px; -fx-font-style: italic;");
        } else if (isMinha) {
            linhaContainer.setAlignment(Pos.CENTER_RIGHT);
            lblMensagem.setStyle(
                    "-fx-background-color: " + GREEN_ACCENT + "; " +
                            "-fx-text-fill: white; " +
                            "-fx-background-radius: 18 18 4 18; " + // Cantos arredondados, ponta direita inferior reta
                            "-fx-effect: dropshadow(three-pass-box, rgba(52,199,89,0.2), 10, 0, 0, 4);"
            );
        } else {
            linhaContainer.setAlignment(Pos.CENTER_LEFT);
            lblMensagem.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-text-fill: " + TEXT_DARK + "; " +
                            "-fx-background-radius: 18 18 18 4; " + // Ponta esquerda inferior reta
                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 8, 0, 0, 2);"
            );
        }

        linhaContainer.getChildren().add(lblMensagem);
        chatBox.getChildren().add(linhaContainer);
    }

    /**
     * Limpa recursos graciosamente
     */
    private void desconectar() {
        try {
            if (saida != null) saida.println("/sair"); // Avisa o servidor
            if (socket != null && !socket.isClosed()) socket.close();
            if (entrada != null) entrada.close();
            if (saida != null) saida.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}