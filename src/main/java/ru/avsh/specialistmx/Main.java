package ru.avsh.specialistmx;

import javax.swing.*;
import java.awt.*;

class Main {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
             JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);

            SpMX   spMX  = new SpMX();
            JFrame frame = new MainFrame(spMX);

            spMX.setMainFrame(frame);

            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}