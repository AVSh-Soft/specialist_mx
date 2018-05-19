package ru.avsh.specialist.mx.gui.lib;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Класс "Широкая каретка".
 *
 * @author O'Reilly and -=AVSh=-
 */
public class WideCaret extends DefaultCaret {
    private static final long serialVersionUID = -8622932384077305706L;

    /**
     * Конструктор.
     *
     * @param rate the rate in milliseconds, 0 to stop blinking
     */
    WideCaret(final int rate) {
        setBlinkRate(rate);
    }

    @Override
    protected synchronized void damage(final Rectangle rectangle) {
        if (rectangle != null) {
            x = rectangle.x;
            y = rectangle.y;
            height = rectangle.height;
            if (width <= 0) {
                width = getComponent().getWidth();
            }
            repaint();
        }
    }

    @Override
    public void paint(final Graphics graphics) {
        final JTextComponent component = getComponent();
        if (component != null) {
            final char dotChar;
            final Rectangle rectangle;
            try {
                final int dot = getDot();
                    rectangle = component.modelToView(dot);
                if (rectangle == null) {
                    return;
                }
                dotChar = component.getText(dot, 1).charAt(0);
            } catch (BadLocationException e) {
                return;
            }
            if ((x != rectangle.x) || (y != rectangle.y)) {
                repaint();
                     x = rectangle.x;
                     y = rectangle.y;
                height = rectangle.height;
            }
            graphics.setColor  (component.getCaretColor());
            graphics.setXORMode(component.getBackground());
            width = (dotChar == '\n') ? 1 : graphics.getFontMetrics().charWidth(dotChar);
            if (isVisible()) {
                graphics.fillRect(rectangle.x, rectangle.y, width, rectangle.height);
            }
        }
    }
}