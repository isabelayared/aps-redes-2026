package com.example.tietemonitor.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Servidor TCP/IP para o sistema de monitoramento do Rio Tietê.
 * Utiliza Sockets de Berkeley (ServerSocket / Socket) para aceitar
 * múltiplas conexões simultâneas de inspetores ambientais.
 */
public class Servidor {

    private static final int PORTA = 5000;
    private static final Map<String, PrintWriter> clientesConectados =
            Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws IOException {

        System.out.println("=== Servidor de Monitoramento do Rio Tietê ===");
        System.out.println("Aguardando conexões na porta " + PORTA + "...\n");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                Socket socketCliente = serverSocket.accept();
                Thread threadCliente = new Thread(new ManipuladorCliente(socketCliente));
                threadCliente.setDaemon(true);
                threadCliente.start();
            }
        }
    }

    static void broadcast(String mensagem, String remetente) {
        synchronized (clientesConectados) {
            for (Map.Entry<String, PrintWriter> entrada : clientesConectados.entrySet()) {
                // O servidor apenas repassa a String bruta para os clientes
                entrada.getValue().println(mensagem);
            }
        }
    }

    static void registrarCliente(String nome, PrintWriter saida) {
        clientesConectados.put(nome, saida);
        System.out.println("[+] Inspetor conectado: " + nome +
                " | Total online: " + clientesConectados.size());
    }

    static void removerCliente(String nome) {
        clientesConectados.remove(nome);
        System.out.println("[-] Inspetor desconectado: " + nome +
                " | Total online: " + clientesConectados.size());
    }

    static class ManipuladorCliente implements Runnable {
        private final Socket socket;
        private String nomeInspetor;

        ManipuladorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader entrada = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                nomeInspetor = entrada.readLine();
                if (nomeInspetor == null || nomeInspetor.isBlank()) {
                    nomeInspetor = "Inspetor-" + socket.getPort();
                }

                registrarCliente(nomeInspetor, saida);

                // CORREÇÃO GARGALO 1: Protocolo de Sistema
                // Encapsula o aviso de entrada no protocolo exato do Front-end
                broadcast("[SISTEMA|REDE|INFO|ENTROU:" + nomeInspetor + "]", nomeInspetor);

                String linha;
                while ((linha = entrada.readLine()) != null) {
                    if (linha.equalsIgnoreCase("/sair")) {
                        break;
                    }

                    // CORREÇÃO GARGALO 1: Roteador Passivo
                    // Removemos a concatenação suja ("[" + nomeInspetor + "]: " + linha).
                    // Agora o servidor apenas faz o broadcast do pacote original intacto.
                    System.out.println("Pacote roteado: " + linha);
                    broadcast(linha, nomeInspetor);
                }

            } catch (IOException e) {
                System.err.println("[ERRO] Conexão perdida com " + nomeInspetor + ": " + e.getMessage());
            } finally {
                if (nomeInspetor != null) {
                    removerCliente(nomeInspetor);

                    // CORREÇÃO GARGALO 1: Protocolo de Sistema
                    // Encapsula o aviso de saída no protocolo exato do Front-end
                    broadcast("[SISTEMA|REDE|INFO|SAIU:" + nomeInspetor + "]", nomeInspetor);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
