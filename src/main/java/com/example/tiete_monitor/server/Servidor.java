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
                broadcast("[SISTEMA] " + nomeInspetor + " entrou no canal.", nomeInspetor);

                String linha;
                while ((linha = entrada.readLine()) != null) {
                    if (linha.equalsIgnoreCase("/sair")) {
                        break;
                    }
                    String mensagemFormatada = "[" + nomeInspetor + "]: " + linha;
                    System.out.println(mensagemFormatada);
                    broadcast(mensagemFormatada, nomeInspetor);
                }

            } catch (IOException e) {
                System.err.println("[ERRO] Conexão perdida com " + nomeInspetor + ": " + e.getMessage());
            } finally {
                if (nomeInspetor != null) {
                    removerCliente(nomeInspetor);
                    broadcast("[SISTEMA] " + nomeInspetor + " saiu do canal.", nomeInspetor);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}