package ru.avsh.specialist.mx.gui.swing.utils;

import javax.swing.*;
import java.net.URL;

/**
 * Заглушка основной формы для корректного вывода диалогов Swing в среде JavaFX.
 */
public final class StubMainFrame extends JFrame implements AutoCloseable {
    private static final long serialVersionUID = 4498710334059268597L;

    private StubMainFrame(String title, URL imageLocation) {
        super(title);
        if (imageLocation != null) {
            this.setIconImage(new ImageIcon(imageLocation).getImage());
        }
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    public static StubMainFrame create(final String title, final URL imageLocation) {
        return new StubMainFrame(title, imageLocation);
    }

    @Override
    public void close() {
        this.setVisible(false);
        this.dispose();
    }
}
