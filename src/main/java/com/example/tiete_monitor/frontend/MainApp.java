package com.example.tiete_monitor.frontend;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * ARQUITETURA: Cliente-Servidor com Protocolo de Pacotes [TIPO|USER|LOCAL|MSG]
 */
public class MainApp extends Application {

    // Configurações de Rede
    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    private Socket socket;
    private PrintWriter saida;
    private BufferedReader entrada;
    private String nomeInspetor;
    private List<String> historicoMensagens = new ArrayList<>();

    // CORREÇÃO GARGALO 3: Estrutura de dados para Inspetores Online
    // Usamos TreeSet para manter os nomes em ordem alfabética.
    private Set<String> inspetoresOnline = new TreeSet<>();

    // Componentes de UI
    private Stage primaryStage;
    private Scene loginScene, chatScene;
    private VBox chatBox, listaInspetores;
    private TextField inputField;
    private ComboBox<String> comboLocal;
    private Label labelStatusCabecalho, labelOxigenio, labelPoluicao;
    private StackPane rootStack;

    // Design System (Paleta Oficial) - NÃO ALTERADO
    private final String BG_COLOR = "#F5F5F7";
    private final String GREEN_ACCENT = "#34C759";
    private final String TEXT_DARK = "#1D1D1F";
    private final String RED_ALERT = "#FF3B30";
    private final String GRAY_TEXT = "#86868B";

    // Ícones SVG - NÃO ALTERADO
    private final String SVG_CLIP = "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.31 2.69 6 6 6s6-2.69 6-6V6h-1.5z";
    private final String SVG_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    private final String SVG_EMOJI = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z";

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest(e -> desconectar());
        buildLoginScene();
        buildChatScene();

        carregarDadosFakesDeTeste();

        primaryStage.setTitle("Monitoramento Rio Tietê PRO - Secretaria do Meio Ambiente");
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }

    private void buildLoginScene() {
        VBox rootLogin = new VBox(25);
        rootLogin.setAlignment(Pos.CENTER);
        rootLogin.setStyle("-fx-background-color: " + BG_COLOR + ";");

        Label lblTitulo = new Label("Secretaria do Meio Ambiente");
        lblTitulo.setFont(Font.font("System", FontWeight.BOLD, 28));
        lblTitulo.setStyle("-fx-text-fill: " + TEXT_DARK + ";");

        TextField txtNome = new TextField();
        txtNome.setPromptText("Matrícula do Inspetor...");
        txtNome.setMaxWidth(300);
        txtNome.setStyle("-fx-background-radius: 12; -fx-padding: 15; -fx-border-color: #E5E5EA; -fx-border-radius: 12;");

        Button btnEntrar = new Button("Autenticar no Sistema");
        btnEntrar.setPrefWidth(300);
        btnEntrar.setStyle("-fx-background-color: " + GREEN_ACCENT + "; -fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 15; -fx-font-weight: bold; -fx-cursor: hand;");

        btnEntrar.setOnAction(e -> {
            String nome = txtNome.getText().trim();
            if (!nome.isEmpty() && conectarAoServidor(nome)) {
                this.nomeInspetor = nome;
                inspetoresOnline.add(nome); // Adiciona a si mesmo na lista
                atualizarCabecalho();
                atualizarListaInspetores();
                primaryStage.setScene(chatScene);
            } else {
                mostrarToastErro("Servidor Offline. Iniciando modo de demonstração.");
                this.nomeInspetor = nome.isEmpty() ? "Inspetora_Teste" : nome;
                inspetoresOnline.add(this.nomeInspetor);
                atualizarCabecalho();
                atualizarListaInspetores();
                primaryStage.setScene(chatScene);
            }
        });

        rootLogin.getChildren().addAll(lblTitulo, txtNome, btnEntrar);
        loginScene = new Scene(rootLogin, 1100, 750);
    }

    private void buildChatScene() {
        BorderPane mainLayout = new BorderPane();
        rootStack = new StackPane(mainLayout);

        VBox sideBar = new VBox(20);
        sideBar.setPrefWidth(280);
        sideBar.setPadding(new Insets(20, 0, 20, 0));
        sideBar.setStyle("-fx-background-color: white; -fx-border-color: transparent #E5E5EA transparent transparent;");

        Label lblOnline = new Label("INSPETORES ATIVOS");
        lblOnline.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + GRAY_TEXT + "; -fx-padding: 0 0 0 20;");

        listaInspetores = new VBox(10);

        VBox zonas = new VBox(5);
        zonas.setPadding(new Insets(20, 10, 0, 10));
        Label lblZonas = new Label("ZONAS DE SENSORES");
        lblZonas.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + GRAY_TEXT + "; -fx-padding: 0 0 10 10;");
        zonas.getChildren().addAll(lblZonas,
                criarBotaoZona("Nascente (Salesópolis)"),
                criarBotaoZona("Trecho Mogi"),
                criarBotaoZona("Barragem Suzano")
        );

        Button btnExportar = new Button("Exportar Relatório (.txt)");
        btnExportar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + GREEN_ACCENT + "; -fx-font-weight: bold; -fx-cursor: hand;");
        btnExportar.setOnAction(e -> exportarLogAuditoria());

        sideBar.getChildren().addAll(lblOnline, listaInspetores, zonas, btnExportar);

        HBox header = new HBox(30);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #E5E5EA transparent;");

        VBox titleArea = new VBox(5);
        Label lblRio = new Label("Rio Tietê - Monitoramento Governamental");
        lblRio.setFont(Font.font("System", FontWeight.BOLD, 18));
        labelStatusCabecalho = new Label("Aguardando Autenticação...");
        labelStatusCabecalho.setStyle("-fx-text-fill: " + GRAY_TEXT + ";");
        titleArea.getChildren().addAll(lblRio, labelStatusCabecalho);

        labelPoluicao = new Label("Nível: --");
        labelOxigenio = new Label("O2: -- mg/L");
        labelPoluicao.setStyle("-fx-font-weight: bold; -fx-background-color: #F2F2F7; -fx-padding: 5 12; -fx-background-radius: 10;");
        labelOxigenio.setStyle("-fx-font-weight: bold; -fx-background-color: #F2F2F7; -fx-padding: 5 12; -fx-background-radius: 10;");

        header.getChildren().addAll(titleArea, labelPoluicao, labelOxigenio);

        chatBox = new VBox(15);
        chatBox.setPadding(new Insets(25));
        chatBox.setStyle("-fx-background-color: " + BG_COLOR + ";");

        ScrollPane scroll = new ScrollPane(chatBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: " + BG_COLOR + ";");
        chatBox.heightProperty().addListener((obs, old, val) -> scroll.setVvalue(1.0));

        HBox inputBar = new HBox(12);
        inputBar.setPadding(new Insets(15, 20, 25, 20));
        inputBar.setAlignment(Pos.CENTER_LEFT);
        inputBar.setStyle("-fx-background-color: " + BG_COLOR + ";");

        Button btnAnexo = criarBotaoSVG(SVG_CLIP, "38", false);
        btnAnexo.setOnAction(e -> handleArquivo());

        MenuButton btnEmoji = new MenuButton();
        btnEmoji.setGraphic(getIcon(SVG_EMOJI, "20", TEXT_DARK));
        btnEmoji.setStyle("-fx-background-color: white; -fx-background-radius: 50; -fx-border-color: #E5E5EA; -fx-border-radius: 50;");
        Arrays.asList("⚠️", "✅", "📍", "🌊", "🐟").forEach(em -> {
            MenuItem item = new MenuItem(em);
            item.setOnAction(ev -> inputField.insertText(inputField.getCaretPosition(), em));
            btnEmoji.getItems().add(item);
        });

        comboLocal = new ComboBox<>();
        comboLocal.setPromptText("Local...");
        comboLocal.getItems().addAll("Nascente", "Mogi das Cruzes", "Suzano", "São Paulo");
        comboLocal.setStyle("-fx-background-radius: 20; -fx-background-color: white; -fx-border-color: #E5E5EA; -fx-border-radius: 20;");

        inputField = new TextField();
        inputField.setPromptText("Digite o relatório de inspeção...");
        inputField.setPrefHeight(45);
        inputField.setStyle("-fx-background-radius: 25; -fx-padding: 0 20; -fx-background-color: white; -fx-border-color: #E5E5EA; -fx-border-radius: 25;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button btnEnviar = criarBotaoSVG(SVG_SEND, "45", true);
        btnEnviar.setOnAction(e -> enviarMensagem("TEXTO", inputField.getText()));

        inputBar.getChildren().addAll(btnAnexo, btnEmoji, comboLocal, inputField, btnEnviar);

        mainLayout.setLeft(sideBar);
        mainLayout.setTop(header);
        mainLayout.setCenter(scroll);
        mainLayout.setBottom(inputBar);

        chatScene = new Scene(rootStack);
    }

    private void carregarDadosFakesDeTeste() {
        processarPacoteRecebido("[SISTEMA|REDE|CENTRAL|Terminal de monitoramento iniciado com sucesso.]");
        processarPacoteRecebido("[TEXTO|Inspetor_Carlos|Nascente|Nível de oxigênio em 6.2 mg/L. Tudo estável por aqui. ✅]");
        processarPacoteRecebido("[TEXTO|Fiscal_Leste|Suzano|⚠️ Alerta: Detectada mancha de óleo próximo à barragem.]");
        processarPacoteRecebido("[SISTEMA|REDE|INFO|Relatórios técnicos de Mogi das Cruzes foram sincronizados.]");

        Platform.runLater(() -> {
            labelOxigenio.setText("O2: 5.4 mg/L");
            labelPoluicao.setText("Nível: Moderado");
        });
    }

    private void atualizarCabecalho() {
        labelStatusCabecalho.setText("Inspetor: " + nomeInspetor + " | Posto: Central de Comando | Status: Online");
    }

    private void processarPacoteRecebido(String pacote) {
        try {
            // Remove as bordas do pacote preservando o interior (proteção caso o conteúdo tenha chaves)
            String raw = pacote;
            if (raw.startsWith("[")) raw = raw.substring(1);
            if (raw.endsWith("]")) raw = raw.substring(0, raw.length() - 1);

            String[] partes = raw.split("\\|", 4);
            if(partes.length < 4) return; // Pacote inválido

            String tipo = partes[0];
            String user = partes[1];
            String local = partes[2];
            String msg = partes[3];

            boolean eMinha = user.equals(nomeInspetor);

            // CORREÇÃO GARGALO 3: Eventos de Entrada e Saída
            if (tipo.equals("SISTEMA") && msg.startsWith("ENTROU:")) {
                String newUser = msg.split(":")[1];
                inspetoresOnline.add(newUser);
                Platform.runLater(this::atualizarListaInspetores);
                msg = newUser + " ingressou no sistema.";
                adicionarBolhaUI(msg, "SISTEMA", "REDE", false);
                return;
            } else if (tipo.equals("SISTEMA") && msg.startsWith("SAIU:")) {
                String outUser = msg.split(":")[1];
                inspetoresOnline.remove(outUser);
                Platform.runLater(this::atualizarListaInspetores);
                msg = outUser + " encerrou a conexão.";
                adicionarBolhaUI(msg, "SISTEMA", "REDE", false);
                return;
            }

            // CORREÇÃO GARGALO 2: Recebimento do Arquivo e Decodificação Base64
            if (tipo.equals("ARQUIVO")) {
                String[] arquivoPartes = msg.split("::", 2);
                String nomeArquivo = arquivoPartes[0];
                String base64Data = arquivoPartes[1];

                File dir = new File("downloads_tiete");
                if (!dir.exists()) dir.mkdirs();

                // Decodificamos o Base64 de volta para bytes brutos e salvamos no disco
                byte[] bytesArquivo = Base64.getDecoder().decode(base64Data);
                File arquivoSalvo = new File(dir, nomeArquivo);

                try (FileOutputStream fos = new FileOutputStream(arquivoSalvo)) {
                    fos.write(bytesArquivo);
                    // Ocultamos o Base64 da UI e trocamos por uma mensagem amigável (Requisito)
                    msg = "📎 Arquivo [" + nomeArquivo + "] recebido e salvo na pasta downloads_tiete.";
                } catch (IOException ex) {
                    msg = "⚠️ Falha ao salvar o arquivo recebido: " + nomeArquivo;
                }
            }

            // Historico e UI - Evitamos salvar a String Base64 gigante no LOG de auditoria
            String textoAuditoria = (tipo.equals("ARQUIVO") && msg.contains("downloads_tiete")) ? "[Transferência de Arquivo]" : msg;
            historicoMensagens.add(String.format("[%s] %s (%s): %s", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), user, local, textoAuditoria));

            adicionarBolhaUI(msg, user, local, eMinha);

        } catch (Exception e) {
            adicionarBolhaUI(pacote, "SISTEMA", "REDE", false);
        }
    }

    private void adicionarBolhaUI(String msg, String user, String local, boolean minha) {
        VBox container = new VBox(5);
        container.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label lblInfo = new Label(user + " @ " + local + " • " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        lblInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: " + GRAY_TEXT + ";");

        Label bolha = new Label(msg);
        bolha.setWrapText(true);
        bolha.setMaxWidth(400);
        bolha.setPadding(new Insets(12, 16, 12, 16));

        if (minha) {
            bolha.setStyle("-fx-background-color: " + GREEN_ACCENT + "; -fx-text-fill: white; -fx-background-radius: 18 18 2 18;");
        } else if (user.equals("SISTEMA")) {
            bolha.setStyle("-fx-background-color: transparent; -fx-text-fill: " + GRAY_TEXT + "; -fx-font-style: italic;");
            container.setAlignment(Pos.CENTER);
        } else {
            bolha.setStyle("-fx-background-color: white; -fx-text-fill: " + TEXT_DARK + "; -fx-background-radius: 18 18 18 2; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 1);");
        }

        container.getChildren().addAll(lblInfo, bolha);
        chatBox.getChildren().add(container);
    }

    private void enviarMensagem(String tipo, String conteudo) {
        String local = comboLocal.getValue();
        if (local == null) {
            mostrarToastErro("Selecione o trecho do rio!");
            return;
        }
        if (!conteudo.trim().isEmpty()) {
            String pacote = String.format("[%s|%s|%s|%s]", tipo, nomeInspetor, local, conteudo);
            if (saida != null) saida.println(pacote);
            else processarPacoteRecebido(pacote);
            inputField.clear();
        }
    }

    // CORREÇÃO GARGALO 2: Transferência de Arquivo Real
    private void handleArquivo() {
        FileChooser fc = new FileChooser();
        File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            String local = comboLocal.getValue();
            if (local == null) {
                mostrarToastErro("Selecione o trecho do rio antes de enviar!");
                return;
            }

            ProgressBar pb = new ProgressBar(0);
            pb.setPrefWidth(200);
            pb.setStyle("-fx-accent: " + GREEN_ACCENT + ";");
            HBox progressoContainer = new HBox(10, new Label("Enviando " + file.getName() + "..."), pb);
            chatBox.getChildren().add(progressoContainer);

            // Usamos uma Thread separada para operações pesadas de I/O para não engasgar a Interface Gráfica
            new Thread(() -> {
                try {
                    // Lemos todos os bytes do arquivo selecionado e codificamos em Base64
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String base64 = Base64.getEncoder().encodeToString(fileBytes);
                    String payloadArquivo = file.getName() + "::" + base64;

                    // Simulação visual agradável da barra de carregamento para UX
                    for (double i = 0; i <= 1.0; i += 0.1) {
                        final double p = i;
                        Platform.runLater(() -> pb.setProgress(p));
                        Thread.sleep(150);
                    }

                    // Envia via Socket após preparar tudo (sincronizando novamente com a main thread do FX)
                    Platform.runLater(() -> {
                        enviarMensagem("ARQUIVO", payloadArquivo);
                        chatBox.getChildren().remove(progressoContainer);
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> mostrarToastErro("Erro ao ler/enviar o arquivo."));
                }
            }).start();
        }
    }

    private void exportarLogAuditoria() {
        try (PrintWriter writer = new PrintWriter(new File("auditoria_tiete.txt"))) {
            writer.println("=== RELATÓRIO DE MONITORAMENTO - RIO TIETÊ ===");
            for (String log : historicoMensagens) writer.println(log);
            mostrarToastErro("Relatório salvo com sucesso!");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // CORREÇÃO GARGALO 3: Lista 100% Dinâmica
    private void atualizarListaInspetores() {
        listaInspetores.getChildren().clear();

        // Elemento fixo e prioritário do próprio usuário
        HBox me = new HBox(10, new Circle(4, Color.web(GREEN_ACCENT)), new Label(nomeInspetor + " (Você)"));
        me.setPadding(new Insets(5, 20, 5, 20));
        me.setAlignment(Pos.CENTER_LEFT);

        FadeTransition ft = new FadeTransition(Duration.seconds(1), me.getChildren().get(0));
        ft.setFromValue(1.0); ft.setToValue(0.2);
        ft.setCycleCount(Animation.INDEFINITE); ft.setAutoReverse(true);
        ft.play();

        listaInspetores.getChildren().add(me);

        // Varre a lista de usuários online sincronizada para adicionar os elementos UI
        for (String inspetor : inspetoresOnline) {
            if (!inspetor.equals(nomeInspetor)) {
                HBox p = new HBox(10, new Circle(4, Color.web("#C7C7CC")), new Label(inspetor));
                listaInspetores.getChildren().add(p);
            }
        }
    }

    private Button criarBotaoZona(String zona) {
        Button b = new Button(zona);
        b.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        b.setOnAction(e -> {
            labelOxigenio.setText("O2: " + String.format("%.1f", 3 + Math.random() * 4) + " mg/L");
            labelPoluicao.setText("Nível: " + (Math.random() > 0.5 ? "Crítica" : "Aceitável"));
        });
        return b;
    }

    private Button criarBotaoSVG(String path, String size, boolean principal) {
        Button b = new Button();
        b.setGraphic(getIcon(path, "18", principal ? "white" : TEXT_DARK));
        b.setPrefSize(Double.parseDouble(size), Double.parseDouble(size));
        b.setStyle(principal ? "-fx-background-color: " + GREEN_ACCENT + "; -fx-background-radius: 50; -fx-cursor: hand;" : "-fx-background-color: white; -fx-background-radius: 50; -fx-border-color: #E5E5EA; -fx-border-radius: 50; -fx-cursor: hand;");
        return b;
    }

    private SVGPath getIcon(String path, String scale, String color) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFill(Color.web(color));
        svg.setScaleX(0.8); svg.setScaleY(0.8);
        return svg;
    }

    private void mostrarToastErro(String msg) {
        Label toast = new Label(msg);
        toast.setStyle("-fx-background-color: " + RED_ALERT + "; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 20;");
        rootStack.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        toast.setTranslateY(20);
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> rootStack.getChildren().remove(toast));
        delay.play();
    }

    private boolean conectarAoServidor(String nome) {
        try {
            socket = new Socket(HOST, PORTA);
            saida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida.println(nome);
            new Thread(() -> {
                try {
                    String linha;
                    while ((linha = entrada.readLine()) != null) {
                        final String p = linha;
                        // Thread-safety: toda atualização na UI deve vir da Thread do JavaFX
                        Platform.runLater(() -> processarPacoteRecebido(p));
                    }
                } catch (IOException e) { Platform.runLater(() -> mostrarToastErro("Conexão Perdida!")); }
            }).start();
            return true;
        } catch (IOException e) { return false; }
    }

    private void desconectar() {
        try { if(saida!=null) saida.println("/sair"); if(socket!=null) socket.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) { launch(args); }
}
