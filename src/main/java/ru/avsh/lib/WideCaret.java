package ru.avsh.lib;

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
    /**
     * Конструктор.
     * @param rate the rate in milliseconds, 0 to stop blinking
     */
    public WideCaret(int rate) {
        setBlinkRate(rate);
    }

    @Override
    protected synchronized void damage(Rectangle r) {
        if (r != null) {
            x = r.x;
            y = r.y;
            height = r.height;
            if (width <= 0) {
                width = getComponent().getWidth();
            }
            repaint();
        }
    }

    @Override
    public void paint(Graphics g) {
        JTextComponent comp = getComponent();
        if (comp != null) {
            char dotChar;
            Rectangle rect;
            try {
                int  dot = getDot();
                    rect = comp.modelToView(dot);
                if (rect == null) {
                    return;
                }
                dotChar = comp.getText(dot, 1).charAt(0);
            } catch (BadLocationException e) {
                return;
            }
            if ((x != rect.x) || (y != rect.y)) {
                repaint();
                     x = rect.x;
                     y = rect.y;
                height = rect.height;
            }
            g.setColor  (comp.getCaretColor());
            g.setXORMode(comp.getBackground());
            width = (dotChar == '\n') ? 1 : g.getFontMetrics().charWidth(dotChar);
            if (isVisible()) {
                g.fillRect(rect.x, rect.y, width, rect.height);
            }
        }
    }
}