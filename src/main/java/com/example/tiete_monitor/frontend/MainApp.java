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
import java.util.*;

/**
 * CLIENTE GRÁFICO (FRONT-END)
 * ---------------------------------------------------------
 * Aplicação JavaFX que se conecta ao Servidor TCP.
 * Padrão de Comunicação de Rede criado para o projeto:
 * Toda string trafegada na rede obedece o formato: [TIPO|USER|LOCAL|MSG]
 */
public class MainApp extends Application {

    // Endereço do Servidor (localhost = a própria máquina)
    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    // Variáveis de Rede (Sockets de Berkeley)
    private Socket socket;
    private PrintWriter saida;       // Envia dados para o servidor
    private BufferedReader entrada;  // Recebe dados do servidor
    private String nomeInspetor;

    // Armazena as mensagens para salvar o arquivo de auditoria depois
    private final List<String> historicoMensagens = new ArrayList<>();
    // Lista automática de quem está online (TreeSet mantém ordem alfabética)
    private final Set<String> inspetoresOnline = new TreeSet<>();

    // Elementos da Interface Gráfica que precisam ser atualizados pela rede
    private Stage primaryStage;
    private Scene loginScene, chatScene;
    private VBox chatBox, listaInspetores;
    private TextField inputField;
    private ComboBox<String> comboLocal;
    private Label labelStatusCabecalho, labelOxigenio, labelPoluicao;
    private StackPane rootStack;

    // Paleta de cores estática
    private static final String BG_COLOR = "#F5F5F7";
    private static final String GREEN_ACCENT = "#34C759";
    private static final String TEXT_DARK = "#1D1D1F";
    private static final String RED_ALERT = "#FF3B30";
    private static final String GRAY_TEXT = "#86868B";

    // Ícones vetoriais (SVG)
    private static final String SVG_CLIP = "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.31 2.69 6 6 6s6-2.69 6-6V6h-1.5z";
    private static final String SVG_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    private static final String SVG_EMOJI = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z";

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

    // --- MÉTODOS DE CONSTRUÇÃO DE INTERFACE (UI) OMITIDOS PARCIALMENTE NOS COMENTÁRIOS PARA FOCAR EM REDES ---

    private void buildLoginScene() {
        VBox rootLogin = new VBox(25);
        rootLogin.setAlignment(Pos.CENTER);
        rootLogin.setStyle("-fx-background-color: " + BG_COLOR + ";");

        Label lblTitulo = new Label("Secretaria do Meio Ambiente");
        lblTitulo.setFont(Font.font("System", FontWeight.BOLD, 28));

        TextField txtNome = new TextField();
        txtNome.setPromptText("Matrícula do Inspetor...");
        txtNome.setMaxWidth(300);

        Button btnEntrar = new Button("Autenticar no Sistema");
        btnEntrar.setPrefWidth(300);
        btnEntrar.setStyle("-fx-background-color: " + GREEN_ACCENT + "; -fx-text-fill: white; -fx-padding: 15;");

        // Ação de clique do botão de login
        btnEntrar.setOnAction(e -> {
            String nome = txtNome.getText().trim();
            // Tenta criar o Socket com o servidor
            if (!nome.isEmpty() && conectarAoServidor(nome)) {
                this.nomeInspetor = nome;
                inspetoresOnline.add(nome);
                atualizarCabecalho();
                atualizarListaInspetores();
                primaryStage.setScene(chatScene); // Troca a tela
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
        // Constrói o layout principal (Painel Esquerdo, Cabeçalho, Chat central, Barra inferior)
        BorderPane mainLayout = new BorderPane();
        rootStack = new StackPane(mainLayout);

        // ... (Construção visual da tela omitida nos comentários, a lógica de rede está nos botões)

        VBox sideBar = new VBox(20);
        sideBar.setPrefWidth(280);
        sideBar.setStyle("-fx-background-color: white;");

        Label lblOnline = new Label("INSPETORES ATIVOS");
        listaInspetores = new VBox(10);

        VBox zonas = new VBox(5);
        zonas.getChildren().addAll(new Label("ZONAS"), criarBotaoZona("Nascente"), criarBotaoZona("Suzano"));

        Button btnExportar = new Button("Exportar Relatório (.txt)");
        btnExportar.setOnAction(e -> exportarLogAuditoria());

        sideBar.getChildren().addAll(lblOnline, listaInspetores, zonas, btnExportar);

        HBox header = new HBox(30);
        VBox titleArea = new VBox(5);
        labelStatusCabecalho = new Label("Aguardando Autenticação...");
        titleArea.getChildren().addAll(new Label("Rio Tietê"), labelStatusCabecalho);
        labelPoluicao = new Label("Nível: --");
        labelOxigenio = new Label("O2: -- mg/L");
        header.getChildren().addAll(titleArea, labelPoluicao, labelOxigenio);

        chatBox = new VBox(15);
        chatBox.setPadding(new Insets(25));
        ScrollPane scroll = new ScrollPane(chatBox);
        chatBox.heightProperty().addListener((obs, old, val) -> scroll.setVvalue(1.0)); // Auto-scroll para baixo

        HBox inputBar = new HBox(12);

        // Botão de Anexo chama o método de upload de arquivo
        Button btnAnexo = criarBotaoSVG(SVG_CLIP, "38", false);
        btnAnexo.setOnAction(e -> handleArquivo());

        comboLocal = new ComboBox<>();
        comboLocal.getItems().addAll("Nascente", "Mogi das Cruzes", "Suzano", "São Paulo");

        inputField = new TextField();

        // Botão de Enviar empacota o texto no protocolo [TEXTO|User|Local|Mensagem]
        Button btnEnviar = criarBotaoSVG(SVG_SEND, "45", true);
        btnEnviar.setOnAction(e -> enviarMensagem("TEXTO", inputField.getText()));

        inputBar.getChildren().addAll(btnAnexo, comboLocal, inputField, btnEnviar);

        mainLayout.setLeft(sideBar);
        mainLayout.setTop(header);
        mainLayout.setCenter(scroll);
        mainLayout.setBottom(inputBar);
        chatScene = new Scene(rootStack);
    }

    private void carregarDadosFakesDeTeste() {
        // Função apenas para popular a tela caso o servidor não esteja ligado
        processarPacoteRecebido("[SISTEMA|REDE|CENTRAL|Terminal iniciado.]");
        processarPacoteRecebido("[TEXTO|Inspetor_Carlos|Nascente|O2 estável. ✅]");
    }

    private void atualizarCabecalho() {
        labelStatusCabecalho.setText("Inspetor: " + nomeInspetor + " | Status: Online");
    }

    /**
     * MÉTODOS DE REDE E PROTOCOLO
     * ---------------------------------------------------------
     * Desempacota a string recebida pela rede.
     * Como a string chega: "[TEXTO|Maria|Suzano|Nível normal]"
     * O método .split("\\|") recorta a string nas barras (Pipe).
     */
    private void processarPacoteRecebido(String pacote) {
        try {
            String raw = pacote;
            if (raw.startsWith("[")) raw = raw.substring(1);
            if (raw.endsWith("]")) raw = raw.substring(0, raw.length() - 1);

            String[] partes = raw.split("\\|", 4);
            if(partes.length < 4) return;

            String tipo = partes[0]; // ex: TEXTO, ARQUIVO, SISTEMA
            String user = partes[1]; // ex: Maria
            String local = partes[2];// ex: Suzano
            String msg = partes[3];  // ex: Nível normal

            boolean eMinha = user.equals(nomeInspetor);

            // Verifica se é uma notificação do servidor avisando que alguém entrou
            if (tipo.equals("SISTEMA") && msg.startsWith("ENTROU:")) {
                String newUser = msg.split(":")[1];
                inspetoresOnline.add(newUser);
                Platform.runLater(this::atualizarListaInspetores);
                adicionarBolhaUI(newUser + " ingressou no sistema.", "SISTEMA", "REDE", false);
                return;
            } else if (tipo.equals("SISTEMA") && msg.startsWith("SAIU:")) {
                String outUser = msg.split(":")[1];
                inspetoresOnline.remove(outUser);
                Platform.runLater(this::atualizarListaInspetores);
                adicionarBolhaUI(outUser + " encerrou a conexão.", "SISTEMA", "REDE", false);
                return;
            }

            // Transferência de Arquivo (Transformando Base64 de volta em Arquivo físico)
            if (tipo.equals("ARQUIVO")) {
                String[] arquivoPartes = msg.split("::", 2);
                String nomeArquivo = arquivoPartes[0];
                String base64Data = arquivoPartes[1];

                File dir = new File("downloads_tiete");
                if (!dir.exists() && !dir.mkdirs()) {
                    Platform.runLater(() -> mostrarToastErro("Falha ao criar diretório."));
                }

                // Descompacta o texto gigante de volta em bytes de computador
                byte[] bytesArquivo = Base64.getDecoder().decode(base64Data);
                File arquivoSalvo = new File(dir, nomeArquivo);

                try (FileOutputStream fos = new FileOutputStream(arquivoSalvo)) {
                    fos.write(bytesArquivo); // Salva no HD
                    msg = "📎 Arquivo [" + nomeArquivo + "] recebido na pasta downloads_tiete.";
                } catch (IOException ex) {
                    msg = "⚠️ Falha ao salvar: " + nomeArquivo;
                }
            }

            // Registra no histórico para gerar relatório depois
            historicoMensagens.add(String.format("[%s] %s: %s", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), user, msg));
            // Adiciona a bolha de chat na tela visualmente
            adicionarBolhaUI(msg, user, local, eMinha);

        } catch (Exception e) {
            adicionarBolhaUI(pacote, "SISTEMA", "REDE", false);
        }
    }

    private void adicionarBolhaUI(String msg, String user, String local, boolean minha) {
        // Criação visual da "bolhinha" de mensagem estilo WhatsApp
        VBox container = new VBox(5);
        container.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label lblInfo = new Label(user + " @ " + local);
        Label bolha = new Label(msg);
        bolha.setWrapText(true);
        bolha.setPadding(new Insets(12, 16, 12, 16));

        if (minha) { // Minhas mensagens ficam verdes
            bolha.setStyle("-fx-background-color: " + GREEN_ACCENT + "; -fx-text-fill: white; -fx-background-radius: 18 18 2 18;");
        } else { // Mensagens dos outros ficam brancas
            bolha.setStyle("-fx-background-color: white; -fx-background-radius: 18 18 18 2;");
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
            // Empacota a mensagem antes de enviar para o socket
            String pacote = String.format("[%s|%s|%s|%s]", tipo, nomeInspetor, local, conteudo);
            if (saida != null) saida.println(pacote); // Joga no túnel da rede!
            else processarPacoteRecebido(pacote); // Modo offline
            inputField.clear();
        }
    }

    private void handleArquivo() {
        FileChooser fc = new FileChooser();
        File file = fc.showOpenDialog(primaryStage); // Abre janela do Windows para escolher arquivo
        if (file != null) {
            String local = comboLocal.getValue();
            if (local == null) return;

            // Tarefas demoradas (como ler um arquivo grande) devem rodar em Threads separadas
            // para não congelar ("travar") a interface visual gráfica.
            new Thread(() -> {
                try {
                    // Lê o arquivo todo e transforma num texto gigante (Base64)
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String base64 = Base64.getEncoder().encodeToString(fileBytes);
                    String payloadArquivo = file.getName() + "::" + base64;

                    // Platform.runLater sincroniza essa Thread secundaria de volta com a interface visual
                    Platform.runLater(() -> enviarMensagem("ARQUIVO", payloadArquivo));
                } catch (Exception ex) {}
            }).start();
        }
    }

    private void exportarLogAuditoria() {
        try (PrintWriter writer = new PrintWriter("auditoria_tiete.txt")) {
            for (String log : historicoMensagens) writer.println(log);
            mostrarToastErro("Relatório salvo!");
        } catch (Exception e) {}
    }

    private void atualizarListaInspetores() {
        listaInspetores.getChildren().clear();
        for (String inspetor : inspetoresOnline) {
            listaInspetores.getChildren().add(new Label(inspetor));
        }
    }

    private Button criarBotaoZona(String zona) {
        Button b = new Button(zona);
        b.setOnAction(e -> labelOxigenio.setText("O2 Atualizado"));
        return b;
    }

    private Button criarBotaoSVG(String path, String size, boolean principal) {
        Button b = new Button();
        SVGPath svg = new SVGPath(); svg.setContent(path);
        b.setGraphic(svg);
        return b;
    }

    private void mostrarToastErro(String msg) {
        Label toast = new Label(msg);
        rootStack.getChildren().add(toast);
        // Exibe o erro e depois some automaticamente após 3 segundos
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> rootStack.getChildren().remove(toast));
        delay.play();
    }

    /**
     * CONEXÃO INICIAL AO SERVIDOR
     */
    private boolean conectarAoServidor(String nome) {
        try {
            // Cria o cano de comunicação TCP
            socket = new Socket(HOST, PORTA);
            saida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida.println(nome); // Manda o nome como primeiro pacote

            // Thread dedicada apenas para ficar escutando a rede o tempo todo
            new Thread(() -> {
                try {
                    String linha;
                    // Fica bloqueado aqui esperando novidades do Servidor
                    while ((linha = entrada.readLine()) != null) {
                        final String p = linha;
                        // Sempre que a rede trouxer algo, usamos o runLater para avisar a Tela de atualizar
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
}
