package com.example.tiete_monitor.client;

import java.io.*;
import java.net.*;

/**
 * CLIENTE TEXTO (Modo Terminal)
 * ---------------------------------------------------------
 * Versão leve do inspetor, roda direto no prompt de comando.
 * Útil para testar se a comunicação TCP/IP está funcionando
 * mesmo sem abrir o front-end JavaFX.
 */
public class Cliente {

    private static final String HOST  = "localhost";
    private static final int    PORTA = 5000;

    public static void main(String[] args) throws IOException {

        System.out.println("=== Cliente – Sistema de Monitoramento do Rio Tietê ===");

        // System.in lê o que a pessoa digita no teclado físico
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Digite seu nome de inspetor: ");
        String nome = teclado.readLine();

        try (Socket socket = new Socket(HOST, PORTA);
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("Conectado ao servidor. Digite mensagens e pressione ENTER.\n");

            // Primeira coisa que envia para a rede: o próprio nome
            saida.println(nome);

            // Criamos uma Thread "Daemon" (que morre quando o programa fecha).
            // A função dela é APENAS escutar a rede e imprimir na tela.
            // Precisamos dela separada porque o método teclado.readLine() lá embaixo trava o programa.
            Thread threadEscuta = new Thread(() -> {
                try {
                    String linhaRecebida;
                    while ((linhaRecebida = entrada.readLine()) != null) {
                        System.out.println(">> Recebido: " + linhaRecebida);
                    }
                } catch (IOException e) {
                    System.out.println("[INFO] Conexão encerrada pelo servidor.");
                }
            });
            threadEscuta.setDaemon(true);
            threadEscuta.start();

            // Enquanto a thread acima ESCUTA, este loop principal aqui em baixo FALA.
            String linhaDigitada;
            while ((linhaDigitada = teclado.readLine()) != null) {
                // Se digitar só texto, precisaria empacotar no formato do Front-end se for falar com ele
                saida.println("[TEXTO|" + nome + "|Terminal|" + linhaDigitada + "]");

                if (linhaDigitada.equalsIgnoreCase("/sair")) {
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("[ERRO] Não foi possível conectar. O servidor está rodando?");
        }
    }
}
