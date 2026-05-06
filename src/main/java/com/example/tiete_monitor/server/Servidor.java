package com.example.tiete_monitor.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ============================================================================
 * CLASSE SERVIDOR: O CORAÇÃO DO SISTEMA DE MONITORAMENTO
 * ============================================================================
 * Esta classe atua como a central de controle. Ela não tem tela (interface gráfica).
 * O papel dela é ficar rodando em segundo plano, recebendo as conexões dos
 * inspetores (MainApp) e repassando as mensagens de um para todos os outros.
 */
public class Servidor {

    // ============================================================================
    // 1. CONFIGURAÇÕES GERAIS E ARMAZENAMENTO
    // ============================================================================

    // A "porta" é como se fosse o número da sala onde o servidor atende as conexões.
    private static final int PORTA = 5000;

    // Este mapa guarda todos os inspetores que estão conectados no momento.
    // Usamos 'synchronizedMap' para evitar erros caso duas pessoas entrem no exato mesmo milissegundo.
    private static final Map<String, PrintWriter> clientesConectados = Collections.synchronizedMap(new HashMap<>());

    // ============================================================================
    // 2. MÉTODO PRINCIPAL (PONTO DE PARTIDA DO SERVIDOR)
    // ============================================================================
    public static void main(String[] args) throws IOException {
        System.out.println("=== Servidor de Monitoramento do Rio Tietê ===");
        System.out.println("Aguardando conexões na porta " + PORTA + "...\n");

        // Abre a porta 5000 do computador para escutar quem quer se conectar
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {

            // O loop infinito mantém o servidor sempre acordado aguardando novos inspetores.
            // O SuppressWarnings avisa a IDE que o loop infinito foi feito de propósito.
            while (true) {
                // Quando alguém tenta conectar, o servidor "aceita" e cria um canal (socket) exclusivo com essa pessoa
                Socket socketCliente = serverSocket.accept();

                // Para não travar o servidor, criamos uma Thread (um processo paralelo) para cuidar desse inspetor recém-chegado
                Thread threadCliente = new Thread(new ManipuladorCliente(socketCliente));
                threadCliente.setDaemon(true);
                threadCliente.start();
            }
        }
    }

    // ============================================================================
    // 3. MÉTODOS DE GERENCIAMENTO DE REDE (MENSAGENS E USUÁRIOS)
    // ============================================================================

    /**
     * O método Broadcast funciona como um alto-falante.
     * Ele pega uma mensagem recebida de um inspetor e espalha para TODOS os outros conectados.
     */
    static void broadcast(String mensagem) {
        synchronized (clientesConectados) {
            for (Map.Entry<String, PrintWriter> entrada : clientesConectados.entrySet()) {
                entrada.getValue().println(mensagem); // Dispara a mensagem para a tela do cliente
            }
        }
    }

    /**
     * Adiciona o inspetor no mapa do servidor quando ele entra no aplicativo.
     */
    static void registrarCliente(String nome, PrintWriter saida) {
        clientesConectados.put(nome, saida);
        System.out.println("[+] Inspetor conectado: " + nome + " | Total online: " + clientesConectados.size());
    }

    /**
     * Remove o inspetor do mapa do servidor quando ele fecha o aplicativo.
     */
    static void removerCliente(String nome) {
        clientesConectados.remove(nome);
        System.out.println("[-] Inspetor desconectado: " + nome + " | Total online: " + clientesConectados.size());
    }

    // ============================================================================
    // 4. CLASSE INTERNA: MANIPULADOR DE CLIENTE (ATENDENTE INDIVIDUAL)
    // ============================================================================
    /**
     * Esta classe existe para que o servidor possa conversar com várias pessoas ao mesmo tempo.
     * Para cada pessoa que entra, o servidor cria um "ManipuladorCliente" exclusivo para ela.
     */
    static class ManipuladorCliente implements Runnable {
        private final Socket socket;
        private String nomeInspetor;

        ManipuladorCliente(Socket socket) {
            this.socket = socket; // Guarda a conexão específica deste usuário
        }

        @Override
        public void run() {
            try (
                    // "entrada" lê o que o cliente digitou. "saida" envia texto para o cliente.
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                // 1º Passo: Assim que conecta, lê o nome de login que o inspetor digitou
                nomeInspetor = entrada.readLine();
                if (nomeInspetor == null || nomeInspetor.isBlank()) {
                    nomeInspetor = "Inspetor-" + socket.getPort();
                }

                // 2º Passo: Salva ele na lista de ativos e avisa a rede que ele entrou
                registrarCliente(nomeInspetor, saida);
                broadcast("[SISTEMA|REDE|INFO|ENTROU:" + nomeInspetor + "]");

                String linha;
                // 3º Passo: Fica lendo infinitamente as mensagens que este inspetor mandar no chat
                while ((linha = entrada.readLine()) != null) {
                    if (linha.equalsIgnoreCase("/sair")) {
                        break; // Se ele enviou o comando de sair, encerra o loop
                    }

                    System.out.println("Pacote roteado: " + linha);

                    // Repassa o relatório/mensagem do inspetor para todos os outros na rede
                    broadcast(linha);
                }

            } catch (IOException e) {
                // Trata desconexões repentinas (ex: a internet do cliente caiu)
                System.err.println("[ERRO] Conexão perdida com " + nomeInspetor + ": " + e.getMessage());
            } finally {
                // 4º Passo: Limpeza. Quando o cliente sai de propósito ou a conexão cai, remove da lista e avisa geral.
                if (nomeInspetor != null) {
                    removerCliente(nomeInspetor);
                    broadcast("[SISTEMA|REDE|INFO|SAIU:" + nomeInspetor + "]");
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
