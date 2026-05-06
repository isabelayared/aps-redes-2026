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
 * ============================================================================
 * CLASSE MAIN_APP: A INTERFACE GRÁFICA DO INSPETOR (FRONT-END)
 * ============================================================================
 * ARQUITETURA: Cliente-Servidor.
 * Esta é a tela do aplicativo que o usuário final (O Inspetor do Rio Tietê) vai usar.
 * Os dados trafegam pela rede em um protocolo padronizado: [TIPO|USER|LOCAL|MSG]
 */
@SuppressWarnings("FieldCanBeLocal")
public class MainApp extends Application {

    // ============================================================================
    // 1. CONFIGURAÇÕES E VARIÁVEIS DO SISTEMA
    // ============================================================================

    // Dados para achar o servidor na rede
    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    // Variáveis que mantêm a conexão viva com o servidor
    private Socket socket;
    private PrintWriter saida;       // Envia mensagens
    private BufferedReader entrada;  // Recebe mensagens
    private String nomeInspetor;     // Guarda quem está logado nesta tela

    // Estruturas de memória (marcadas com 'final' por boas práticas para não serem sobrescritas)
    private final List<String> historicoMensagens = new ArrayList<>(); // Guarda o histórico para exportar o .txt
    private final Set<String> inspetoresOnline = new TreeSet<>();      // Guarda quem tá online (TreeSet mantém em ordem alfabética)

    // Variáveis Visuais da Interface (Botões, Telas e Caixas de Texto)
    private Stage primaryStage;
    private Scene loginScene, chatScene;
    private VBox chatBox, listaInspetores;
    private TextField inputField;
    private ComboBox<String> comboLocal;
    private Label labelStatusCabecalho, labelOxigenio, labelPoluicao;
    private StackPane rootStack;

    // Design System (Cores e Estética do App - NÃO ALTERAR)
    private final String BG_COLOR = "#F5F5F7";
    private final String GREEN_ACCENT = "#34C759";
    private final String TEXT_DARK = "#1D1D1F";
    private final String RED_ALERT = "#FF3B30";
    private final String GRAY_TEXT = "#86868B";

    // Ícones vetorizados (SVG) para os botões ficarem bonitos e responsivos
    private final String SVG_CLIP = "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.31 2.69 6 6 6s6-2.69 6-6V6h-1.5z";
    private final String SVG_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    private final String SVG_EMOJI = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z";


    // ============================================================================
    // 2. MÉTODO DE INICIALIZAÇÃO DA JANELA (PONTO DE PARTIDA DO JAVAFX)
    // ============================================================================
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Regra importante: se o usuário clicar no X para fechar a janela, a conexão de rede também morre.
        primaryStage.setOnCloseRequest(e -> desconectar());

        // Chama as funções que desenham as telas do aplicativo
        buildLoginScene();
        buildChatScene();
        carregarDadosFakesDeTeste(); // Apenas para demonstrar que a interface funciona

        // Configura a janela principal e exibe a tela de login
        primaryStage.setTitle("Monitoramento Rio Tietê PRO - Secretaria do Meio Ambiente");
        primaryStage.setScene(loginScene);
        primaryStage.show();
    }


    // ============================================================================
    // 3. CONSTRUÇÃO VISUAL: TELA DE LOGIN
    // ============================================================================
    /**
     * Monta todos os componentes visuais da tela inicial onde o usuário digita a matrícula.
     */
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

        // O que acontece quando clica no botão "Entrar"
        btnEntrar.setOnAction(e -> {
            String nome = txtNome.getText().trim();
            // Tenta plugar no servidor. Se conseguir, muda de tela.
            if (!nome.isEmpty() && conectarAoServidor(nome)) {
                this.nomeInspetor = nome;
                inspetoresOnline.add(nome);
                atualizarCabecalho();
                atualizarListaInspetores();
                primaryStage.setScene(chatScene);
            } else {
                // Se o servidor estiver desligado, permite que a pessoa veja a tela mesmo assim (Modo Demo Offline)
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


    // ============================================================================
    // 4. CONSTRUÇÃO VISUAL: TELA PRINCIPAL DE CHAT E DASHBOARD
    // ============================================================================
    /**
     * Monta o layout complexo da tela principal (Menu Lateral, Topo e Chat Central).
     */
    private void buildChatScene() {
        BorderPane mainLayout = new BorderPane();
        rootStack = new StackPane(mainLayout); // StackPane permite jogar alertas (Toasts) por cima de tudo

        // ---- MENU LATERAL ESQUERDO ----
        VBox sideBar = new VBox(20);
        sideBar.setPrefWidth(280);
        sideBar.setPadding(new Insets(20, 0, 20, 0));
        sideBar.setStyle("-fx-background-color: white; -fx-border-color: transparent #E5E5EA transparent transparent;");

        Label lblOnline = new Label("INSPETORES ATIVOS");
        lblOnline.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + GRAY_TEXT + "; -fx-padding: 0 0 0 20;");
        listaInspetores = new VBox(10); // Aqui é onde a lista de pessoas online vai ser populada dinamicamente

        VBox zonas = new VBox(5);
        zonas.setPadding(new Insets(20, 10, 0, 10));
        Label lblZonas = new Label("ZONAS DE SENSORES");
        lblZonas.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + GRAY_TEXT + "; -fx-padding: 0 0 10 10;");
        zonas.getChildren().addAll(lblZonas, criarBotaoZona("Nascente (Salesópolis)"), criarBotaoZona("Trecho Mogi"), criarBotaoZona("Barragem Suzano"));

        Button btnExportar = new Button("Exportar Relatório (.txt)");
        btnExportar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + GREEN_ACCENT + "; -fx-font-weight: bold; -fx-cursor: hand;");
        btnExportar.setOnAction(e -> exportarLogAuditoria());

        sideBar.getChildren().addAll(lblOnline, listaInspetores, zonas, btnExportar);

        // ---- CABEÇALHO SUPERIOR ----
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

        // ---- ÁREA DE MENSAGENS (CHATBOX) ----
        chatBox = new VBox(15);
        chatBox.setPadding(new Insets(25));
        chatBox.setStyle("-fx-background-color: " + BG_COLOR + ";");

        ScrollPane scroll = new ScrollPane(chatBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: " + BG_COLOR + ";");
        // Truque de usabilidade: Se o tamanho do chat crescer (nova mensagem), rola a barra lá pra baixo automaticamente
        chatBox.heightProperty().addListener((obs, old, val) -> scroll.setVvalue(1.0));

        // ---- BARRA DE DIGITAÇÃO E CONTROLES INFERIORES ----
        HBox inputBar = new HBox(12);
        inputBar.setPadding(new Insets(15, 20, 25, 20));
        inputBar.setAlignment(Pos.CENTER_LEFT);
        inputBar.setStyle("-fx-background-color: " + BG_COLOR + ";");

        Button btnAnexo = criarBotaoSVG(SVG_CLIP, "38", false);
        btnAnexo.setOnAction(e -> handleArquivo()); // Chama a janela de selecionar foto/arquivo

        MenuButton btnEmoji = new MenuButton();
        btnEmoji.setGraphic(getIcon(SVG_EMOJI, TEXT_DARK));
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
        HBox.setHgrow(inputField, Priority.ALWAYS); // Faz a barra de texto esticar pra preencher o espaço vazio

        Button btnEnviar = criarBotaoSVG(SVG_SEND, "45", true);
        btnEnviar.setOnAction(e -> enviarMensagem("TEXTO", inputField.getText()));

        inputBar.getChildren().addAll(btnAnexo, btnEmoji, comboLocal, inputField, btnEnviar);

        // Agrupa tudo e joga na tela
        mainLayout.setLeft(sideBar);
        mainLayout.setTop(header);
        mainLayout.setCenter(scroll);
        mainLayout.setBottom(inputBar);

        chatScene = new Scene(rootStack);
    }


    // ============================================================================
    // 5. REGRAS DE REDE E PROCESSAMENTO DE MENSAGENS (CORAÇÃO DO CÓDIGO)
    // ============================================================================

    /**
     * Esta função é ativada toda vez que o servidor joga alguma informação nova pela rede.
     * Ela "desempacota" a string que chega e decide o que fazer com ela no visual do sistema.
     */
    private void processarPacoteRecebido(String pacote) {
        try {
            // Limpeza: Tira os colchetes [ ] que encapsulam a mensagem do protocolo
            String raw = pacote;
            if (raw.startsWith("[")) raw = raw.substring(1);
            if (raw.endsWith("]")) raw = raw.substring(0, raw.length() - 1);

            // Corta a string nos símbolos de barra vertical | em exatas 4 partes
            String[] partes = raw.split("\\|", 4);
            if(partes.length < 4) return; // Aborta se veio uma mensagem defeituosa

            String tipo = partes[0];   // Ex: TEXTO, SISTEMA, ARQUIVO
            String user = partes[1];   // Ex: Isabela, Henrique, SISTEMA
            String local = partes[2];  // Ex: Nascente, REDE
            String msg = partes[3];    // Ex: "O rio melhorou muito hoje."

            boolean eMinha = user.equals(nomeInspetor); // Identifica se fui eu mesmo que mandei essa mensagem

            // --- LÓGICA DE DETECTAR QUEM ENTROU/SAIU ---
            if (tipo.equals("SISTEMA") && msg.startsWith("ENTROU:")) {
                String newUser = msg.split(":")[1];
                inspetoresOnline.add(newUser);
                Platform.runLater(this::atualizarListaInspetores); // Atualiza o visual da aba lateral
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

            // --- LÓGICA DE RECEBIMENTO DE ARQUIVOS ---
            if (tipo.equals("ARQUIVO")) {
                // Arquivos viajam pela rede como textos gigantes ilegíveis (Formato Base64).
                // Precisamos quebrar isso em Nome do Arquivo + Texto Gigante.
                String[] arquivoPartes = msg.split("::", 2);
                String nomeArquivo = arquivoPartes[0];
                String base64Data = arquivoPartes[1];

                // Cria uma pasta local no seu computador chamada "downloads_tiete"
                File dir = new File("downloads_tiete");
                if (!dir.exists() && !dir.mkdirs()) {
                    System.err.println("Erro ao criar diretório downloads_tiete");
                }

                // Descriptografa o Base64 de volta para um arquivo físico (PDF, Imagem, etc)
                byte[] bytesArquivo = Base64.getDecoder().decode(base64Data);
                File arquivoSalvo = new File(dir, nomeArquivo);

                try (FileOutputStream fos = new FileOutputStream(arquivoSalvo)) {
                    fos.write(bytesArquivo); // Escreve o arquivo no SSD do computador
                    msg = "📎 Arquivo [" + nomeArquivo + "] recebido e salvo na pasta downloads_tiete."; // Mensagem elegante pra UI
                } catch (IOException ex) {
                    msg = "⚠️ Falha ao salvar o arquivo recebido: " + nomeArquivo;
                }
            }

            // --- LÓGICA DE REGISTRO E INTERFACE ---
            // Salva a interação na memória pra depois exportar um TXT bonitinho pra professora
            String textoAuditoria = (tipo.equals("ARQUIVO") && msg.contains("downloads_tiete")) ? "[Transferência de Arquivo]" : msg;
            historicoMensagens.add(String.format("[%s] %s (%s): %s", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), user, local, textoAuditoria));

            // Joga a bolha de fala na tela de chat
            adicionarBolhaUI(msg, user, local, eMinha);

        } catch (Exception e) {
            adicionarBolhaUI(pacote, "SISTEMA", "REDE", false);
        }
    }

    /**
     * Pega os textos do usuário, empacota com o protocolo certo e dispara pelo Socket (Cabo de rede virtual).
     */
    private void enviarMensagem(String tipo, String conteudo) {
        String local = comboLocal.getValue();
        if (local == null) {
            mostrarToastErro("Selecione o trecho do rio!"); // Impede enviar sem preencher requisitos
            return;
        }
        if (!conteudo.trim().isEmpty()) {
            String pacote = String.format("[%s|%s|%s|%s]", tipo, nomeInspetor, local, conteudo);
            if (saida != null) saida.println(pacote);
            else processarPacoteRecebido(pacote); // Se a rede cair, finge o envio no visual offline
            inputField.clear();
        }
    }


    // ============================================================================
    // 6. FUNÇÕES ESPECIAIS (UPLOAD DE ARQUIVOS E AUDITORIA)
    // ============================================================================

    /**
     * Disparada pelo botão de clip. Abre as pastas do Windows/Mac, lê o arquivo, transforma em Base64 e envia.
     */
    private void handleArquivo() {
        FileChooser fc = new FileChooser(); // Invoca a tela natural do sistema operacional
        File file = fc.showOpenDialog(primaryStage);
        if (file != null) {
            String local = comboLocal.getValue();
            if (local == null) {
                mostrarToastErro("Selecione o trecho do rio antes de enviar!");
                return;
            }

            // Cria uma barrinha de carregamento verde no chat para mostrar que tá processando
            ProgressBar pb = new ProgressBar(0);
            pb.setPrefWidth(200);
            pb.setStyle("-fx-accent: " + GREEN_ACCENT + ";");
            HBox progressoContainer = new HBox(10, new Label("Enviando " + file.getName() + "..."), pb);
            chatBox.getChildren().add(progressoContainer);

            // Operações pesadas como ler arquivos e criptografar matam o visual (tudo trava).
            // Por isso jogamos o processo de upload numa Thread separada do processador.
            new Thread(() -> {
                try {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String base64 = Base64.getEncoder().encodeToString(fileBytes);
                    String payloadArquivo = file.getName() + "::" + base64; // Pacote no padrão "Nome.jpg::ASDFH37DSHG8"

                    // Animação charmosinha simulando um upload
                    for (double i = 0; i <= 1.0; i += 0.1) {
                        final double p = i;
                        Platform.runLater(() -> pb.setProgress(p)); // Platform.runLater sincroniza essa thread com a do visual
                        Thread.sleep(150);
                    }

                    Platform.runLater(() -> {
                        enviarMensagem("ARQUIVO", payloadArquivo);
                        chatBox.getChildren().remove(progressoContainer); // Some com a barra de carregamento
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> mostrarToastErro("Erro ao ler/enviar o arquivo."));
                }
            }).start();
        }
    }

    /**
     * Pega o histórico guardado na memória e converte num arquivo de texto real pro usuário salvar.
     */
    private void exportarLogAuditoria() {
        try (PrintWriter writer = new PrintWriter("auditoria_tiete.txt")) {
            writer.println("=== RELATÓRIO DE MONITORAMENTO - RIO TIETÊ ===");
            for (String log : historicoMensagens) writer.println(log);
            mostrarToastErro("Relatório salvo com sucesso na pasta raiz do projeto!");
        } catch (Exception e) {
            System.err.println("Erro ao exportar: " + e.getMessage());
        }
    }


    // ============================================================================
    // 7. COMPONENTES VISUAIS DINÂMICOS (ATUALIZAÇÃO DE INTERFACE / MÉTODOS AUXILIARES)
    // ============================================================================

    /**
     * Constrói e injeta graficamente a bolha de mensagem verde (se for você) ou branca (se for os outros) no chat.
     */
    private void adicionarBolhaUI(String msg, String user, String local, boolean minha) {
        VBox container = new VBox(5);
        container.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label lblInfo = new Label(user + " @ " + local + " • " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        lblInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: " + GRAY_TEXT + ";");

        Label bolha = new Label(msg);
        bolha.setWrapText(true);
        bolha.setMaxWidth(400);
        bolha.setPadding(new Insets(12, 16, 12, 16));

        // Define o design da bolha baseado no remetente (Você x Colegas x Sistema)
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

    /**
     * Limpa e redesenha a lista de quem está online na barra lateral esquerda.
     */
    private void atualizarListaInspetores() {
        listaInspetores.getChildren().clear();

        // Cria o indicador "Você" com uma bolinha verde pulsante indicando sua presença
        HBox me = new HBox(10, new Circle(4, Color.web(GREEN_ACCENT)), new Label(nomeInspetor + " (Você)"));
        me.setPadding(new Insets(5, 20, 5, 20));
        me.setAlignment(Pos.CENTER_LEFT);

        FadeTransition ft = new FadeTransition(Duration.seconds(1), me.getChildren().getFirst());
        ft.setFromValue(1.0); ft.setToValue(0.2);
        ft.setCycleCount(Animation.INDEFINITE); ft.setAutoReverse(true);
        ft.play();

        listaInspetores.getChildren().add(me);

        // Varre a lista de usuários armazenada e desenha os colegas na tela com pontinho cinza
        for (String inspetor : inspetoresOnline) {
            if (!inspetor.equals(nomeInspetor)) {
                HBox p = new HBox(10, new Circle(4, Color.web("#C7C7CC")), new Label(inspetor));
                listaInspetores.getChildren().add(p);
            }
        }
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

    /** MÉTODOS VISUAIS GENÉRICOS DE UTILIDADE (CRIAR BOTÕES E POPUPS) **/

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
        b.setGraphic(getIcon(path, principal ? "white" : TEXT_DARK));
        b.setPrefSize(Double.parseDouble(size), Double.parseDouble(size));
        b.setStyle(principal ? "-fx-background-color: " + GREEN_ACCENT + "; -fx-background-radius: 50; -fx-cursor: hand;" : "-fx-background-color: white; -fx-background-radius: 50; -fx-border-color: #E5E5EA; -fx-border-radius: 50; -fx-cursor: hand;");
        return b;
    }

    private SVGPath getIcon(String path, String color) {
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


    // ============================================================================
    // 8. INFRAESTRUTURA DE COMUNICAÇÃO DE REDE DO CLIENTE (O SOCKET)
    // ============================================================================

    /**
     * Cria a ponte de acesso (Socket) do aplicativo com o Servidor remoto.
     */
    private boolean conectarAoServidor(String nome) {
        try {
            socket = new Socket(HOST, PORTA);
            saida = new PrintWriter(socket.getOutputStream(), true); // "saida" despacha as mensagens para a rede
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream())); // "entrada" lê as mensagens que a rede nos entrega

            saida.println(nome); // Manda o nome pro servidor como primeiro contato (Login)

            // Cria um processo à parte apenas para ficar aguardando respostas e novas mensagens o tempo todo
            new Thread(() -> {
                try {
                    String linha;
                    while ((linha = entrada.readLine()) != null) {
                        final String p = linha;
                        // Em JavaFX, processos externos de rede não podem mexer na tela.
                        // O 'Platform.runLater' faz a ponte devolvendo as mensagens para a thread visual redesenhar tudo com sucesso.
                        Platform.runLater(() -> processarPacoteRecebido(p));
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> mostrarToastErro("Conexão Perdida com o Servidor!"));
                }
            }).start();
            return true;
        } catch (IOException e) { return false; } // Servidor falhou ou está desligado
    }

    /**
     * Método seguro para fechar conexões caso a pessoa feche no "X" vermelho do sistema operacional.
     */
    private void desconectar() {
        try {
            if(saida!=null) saida.println("/sair");
            if(socket!=null) socket.close();
        } catch (Exception ignored) {}
    }
}
