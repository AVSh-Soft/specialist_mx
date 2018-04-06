package ru.avsh.lib;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.MaskFormatter;
import java.awt.*;
import java.text.ParseException;

/**
 * Класс "Расширенный JFormattedTextField" (настроен для быстрого применения).
 *
 * @author -=AVSh=-
 */
public class ExtFormattedTextField extends JFormattedTextField {
    private static final long serialVersionUID = 8878373188408325948L;

    /**
     * Конструктор.
     *
     * @param mask        Sets the mask dictating the legal characters.
     * @param placeholder Character used when formatting if the value does not completely fill the mask.
     */
    public ExtFormattedTextField(String mask, char placeholder) {
        super();

        final MaskFormatter mf = new MaskFormatter();
        mf.setPlaceholderCharacter(placeholder);
        try {
            mf.setMask(mask);
        } catch (ParseException e) {
            // Если маска задана некорректно, то маска не будет применяться
        }
        setFormatterFactory(new DefaultFormatterFactory(mf));

        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        setCaret(new WideCaret(getCaret().getBlinkRate()));
    }
}
