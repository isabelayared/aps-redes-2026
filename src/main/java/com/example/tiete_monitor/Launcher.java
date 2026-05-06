package com.example.tiete_monitor;

import javafx.application.Application;
import com.example.tiete_monitor.frontend.MainApp;

/**
 * CLASSE LAUNCHER (Inicializador)
 * ---------------------------------------------------------
 * O JavaFX exige que a classe principal (que herda de Application)
 * seja carregada corretamente pelo motor da máquina virtual.
 * Esta classe isolada garante que o método Application.launch()
 * seja executado sem erros de carregamento de módulos.
 */
public class Launcher {
    public static void main(String[] args) {
        // Aponta para a classe MainApp, que contém as telas do nosso Front-end.
        Application.launch(MainApp.class, args);
    }
}
