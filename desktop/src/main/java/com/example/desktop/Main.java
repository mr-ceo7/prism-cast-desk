package com.example.desktop;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.robot.screenshotMethod", "dbusScreencast");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            new PrismCastApp().launch();
        });
    }
}
