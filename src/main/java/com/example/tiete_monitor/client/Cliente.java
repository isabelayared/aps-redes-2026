package com.example.tietemonitor.client;

import java.io.*;
import java.net.*;

/**
 * Cliente TCP/IP do sistema de monitoramento do Rio Tietê.
 */
public class Cliente {

    private static final String HOST  = "localhost";
    private static final int    PORTA = 5000;

    public static void main(String[] args) throws IOException {

        System.out.println("=== Cliente – Sistema de Monitoramento do Rio Tietê ===");
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Digite seu nome de inspetor: ");
        String nome = teclado.readLine();

        try (Socket socket = new Socket(HOST, PORTA);
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("Conectado ao servidor em " + HOST + ":" + PORTA);
            System.out.println("Digite mensagens e pressione ENTER. Use /sair para encerrar.\n");

            saida.println(nome);

            Thread threadEscuta = new Thread(() -> {
                try {
                    String linhaRecebida;
                    while ((linhaRecebida = entrada.readLine()) != null) {
                        System.out.println(linhaRecebida);
                    }
                } catch (IOException e) {
                    System.out.println("[INFO] Conexão encerrada pelo servidor.");
                }
            });
            threadEscuta.setDaemon(true);
            threadEscuta.start();

            String linhaDigitada;
            while ((linhaDigitada = teclado.readLine()) != null) {
                saida.println(linhaDigitada);
                if (linhaDigitada.equalsIgnoreCase("/sair")) {
                    System.out.println("[INFO] Encerrando conexão...");
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("[ERRO] Não foi possível conectar ao servidor em "
                    + HOST + ":" + PORTA + ". Verifique se o servidor está rodando.");
        }

        System.out.println("[INFO] Cliente encerrado.");
    }
}