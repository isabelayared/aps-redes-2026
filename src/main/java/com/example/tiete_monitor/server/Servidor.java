package com.example.tiete_monitor.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * SERVIDOR TCP/IP - CENTRAL DE MONITORAMENTO
 * ---------------------------------------------------------
 * Esta classe é responsável por aceitar conexões de múltiplos
 * inspetores simultaneamente. Ela utiliza a arquitetura de
 * Sockets de Berkeley para a comunicação de rede.
 */
public class Servidor {

    // A porta lógica onde o servidor ficará "escutando" a rede.
    private static final int PORTA = 5000;

    // Lista de clientes conectados. Usamos Collections.synchronizedMap para
    // evitar que o servidor trave (Thread-safe) se duas pessoas entrarem/saírem ao mesmo tempo.
    private static final Map<String, PrintWriter> clientesConectados =
            Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws IOException {
        System.out.println("=== Servidor de Monitoramento do Rio Tietê ===");
        System.out.println("Aguardando conexões na porta " + PORTA + "...\n");

        // O ServerSocket abre a porta no sistema operacional e aguarda conexões.
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                // O método .accept() "trava" o código até que um cliente se conecte.
                Socket socketCliente = serverSocket.accept();

                // Quando um cliente chega, criamos uma nova "Thread" (linha de execução)
                // exclusiva para ele. Isso permite que o servidor atenda 100 clientes ao mesmo tempo.
                Thread threadCliente = new Thread(new ManipuladorCliente(socketCliente));
                threadCliente.setDaemon(true);
                threadCliente.start();
            }
        }
    }

    /**
     * Pega uma mensagem recebida e envia (faz o roteamento) para TODOS
     * os inspetores que estão na lista de clientesConectados.
     */
    static void broadcast(String mensagem, String remetente) {
        synchronized (clientesConectados) {
            for (Map.Entry<String, PrintWriter> entrada : clientesConectados.entrySet()) {
                entrada.getValue().println(mensagem);
            }
        }
    }

    // Adiciona um novo inspetor ao "Dicionário" de clientes ativos.
    static void registrarCliente(String nome, PrintWriter saida) {
        clientesConectados.put(nome, saida);
        System.out.println("[+] Inspetor conectado: " + nome + " | Total online: " + clientesConectados.size());
    }

    // Remove um inspetor que fechou o programa ou perdeu a conexão.
    static void removerCliente(String nome) {
        clientesConectados.remove(nome);
        System.out.println("[-] Inspetor desconectado: " + nome + " | Total online: " + clientesConectados.size());
    }

    /**
     * CLASSE INTERNA: ManipuladorCliente
     * O Runnable significa que esta classe vai rodar em paralelo (Concorrência).
     * Ela é responsável por ficar ouvindo tudo o que UM cliente específico envia.
     */
    static class ManipuladorCliente implements Runnable {
        private final Socket socket;
        private String nomeInspetor;

        ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    // buffer de entrada (lê o que o cliente enviou)
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    // buffer de saída (envia dados para o cliente) - o 'true' é o auto-flush
                    PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                // A primeira mensagem que o cliente manda é sempre o próprio nome.
                nomeInspetor = entrada.readLine();
                if (nomeInspetor == null || nomeInspetor.isBlank()) {
                    nomeInspetor = "Inspetor-" + socket.getPort();
                }

                registrarCliente(nomeInspetor, saida);
                // Avisa a todos que alguém entrou usando o protocolo do front-end
                broadcast("[SISTEMA|REDE|INFO|ENTROU:" + nomeInspetor + "]", nomeInspetor);

                // Loop infinito: fica escutando tudo o que este inspetor digita
                String linha;
                while ((linha = entrada.readLine()) != null) {
                    if (linha.equalsIgnoreCase("/sair")) {
                        break; // Se ele digitar /sair, quebra o loop e desconecta
                    }
                    // Repassa a mensagem (já no formato [TIPO|USER|LOCAL|MSG]) para todos
                    broadcast(linha, nomeInspetor);
                }

            } catch (IOException e) {
                System.err.println("[ERRO] Conexão perdida com " + nomeInspetor);
            } finally {
                // Bloco executado obrigatoriamente quando o cliente se desconecta
                if (nomeInspetor != null) {
                    removerCliente(nomeInspetor);
                    broadcast("[SISTEMA|REDE|INFO|SAIU:" + nomeInspetor + "]", nomeInspetor);
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
