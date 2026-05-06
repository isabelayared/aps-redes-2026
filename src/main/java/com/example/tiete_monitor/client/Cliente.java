package com.example.tiete_monitor.client;

import java.io.*;
import java.net.*;

/**
 * ============================================================================
 * CLASSE CLIENTE (MODO TEXTO): O ACESSO RÁPIDO DO INSPETOR
 * ============================================================================
 * Esta classe é uma versão "limpa" e sem interface gráfica (apenas tela preta do terminal)
 * para conectar ao servidor do Rio Tietê. É excelente para rodar testes rápidos ou
 * para inspetores que estejam usando computadores com poucos recursos.
 */
public class Cliente {

    // ============================================================================
    // 1. CONFIGURAÇÕES DE REDE
    // ============================================================================

    // "localhost" significa que o servidor está rodando no mesmo computador que este cliente.
    private static final String HOST  = "localhost";

    // A porta 5000 é a "porta de entrada" que combinamos lá no arquivo do Servidor.
    private static final int    PORTA = 5000;


    // ============================================================================
    // 2. INÍCIO DO PROGRAMA E IDENTIFICAÇÃO
    // ============================================================================
    public static void main(String[] args) throws IOException {

        System.out.println("=== Cliente – Sistema de Monitoramento do Rio Tietê ===");

        // Prepara o sistema para ler tudo o que o usuário digitar no teclado
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Digite seu nome de inspetor: ");
        String nome = teclado.readLine();


        // ============================================================================
        // 3. CONEXÃO COM O SERVIDOR
        // ============================================================================
        // O bloco "try" tenta estabelecer a conexão. Se a internet cair ou o servidor
        // estiver desligado, ele pula lá para baixo para o bloco "catch" para avisar do erro.
        try (
                // 'Socket' é o nosso cabo de rede virtual conectando ao Servidor
                Socket socket = new Socket(HOST, PORTA);

                // 'saida' é o tubo por onde enviaremos nossas mensagens para o servidor
                PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);

                // 'entrada' é o tubo por onde leremos as mensagens que os outros mandam
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("Conectado ao servidor em " + HOST + ":" + PORTA);
            System.out.println("Digite mensagens e pressione ENTER. Use /sair para encerrar.\n");

            // A primeira coisa que o servidor espera receber é o nome de quem conectou
            saida.println(nome);


            // ============================================================================
            // 4. OUVINDO OS OUTROS (PROCESSO EM SEGUNDO PLANO)
            // ============================================================================
            // Se o programa ficasse travado esperando a gente digitar algo, não conseguiríamos
            // ler as mensagens dos colegas. Por isso, criamos uma 'Thread' (um trabalhador extra)
            // só para ficar de olho na 'entrada' e imprimir na tela tudo que chegar.
            Thread threadEscuta = new Thread(() -> {
                try {
                    String linhaRecebida;
                    // Fica rodando infinitamente: assim que chega uma mensagem, imprime na tela.
                    while ((linhaRecebida = entrada.readLine()) != null) {
                        System.out.println(linhaRecebida);
                    }
                } catch (IOException e) {
                    System.out.println("[INFO] Conexão encerrada pelo servidor.");
                }
            });
            // O modo 'Daemon' garante que esse trabalhador extra morra quando fecharmos o programa principal
            threadEscuta.setDaemon(true);
            threadEscuta.start();


            // ============================================================================
            // 5. FALANDO COM OS OUTROS (LOOP PRINCIPAL)
            // ============================================================================
            String linhaDigitada;
            // Enquanto o usuário estiver digitando coisas no teclado...
            while ((linhaDigitada = teclado.readLine()) != null) {

                // Envia a mensagem digitada para o servidor espalhar
                saida.println(linhaDigitada);

                // Regra de saída: se digitar /sair, o programa quebra o loop e desliga a conexão
                if (linhaDigitada.equalsIgnoreCase("/sair")) {
                    System.out.println("[INFO] Encerrando conexão...");
                    break;
                }
            }


            // ============================================================================
            // 6. TRATAMENTO DE ERROS (QUANDO O SERVIDOR ESTÁ FORA DO AR)
            // ============================================================================
        } catch (ConnectException e) {
            // Em vez do programa fechar e cuspir códigos de erro feios vermelhos,
            // ele mostra uma mensagem amigável ensinando o que fazer.
            System.err.println("[ERRO] Não foi possível conectar ao servidor em "
                    + HOST + ":" + PORTA + ". Verifique se o servidor está rodando.");
        }

        System.out.println("[INFO] Cliente encerrado.");
    }
}
