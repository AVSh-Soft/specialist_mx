package avsh.specialist_mx;

import javax.swing.*;
import java.awt.*;

class Main {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
             JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);

            cSpMX  spMX  = new cSpMX();
            JFrame frame = new cSpMX_Frame(spMX);

            spMX.setMainFrame(frame);

            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}