package ru.avsh.specialistmx;

import javax.swing.*;
import java.awt.*;

/**
 * Главный класс для запуска.
 */
class Main {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
             JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);

            SpecialistMX spMX  = new SpecialistMX( );
                  JFrame frame = new MainFrame(spMX);

            spMX.setMainFrame(frame);

            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}