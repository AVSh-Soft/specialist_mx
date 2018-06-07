package ru.avsh.specialist.mx.gui.lib;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Arrays;

import static javafx.scene.control.ButtonBar.ButtonData;
import static javafx.scene.control.ButtonType.CANCEL;
import static javafx.scene.control.ButtonType.OK;

public final class AlertUtil {

    private AlertUtil() {
        //
    }

    /**
     * Выводит диалог с сообщением.
     *
     * @param message   сообщение
     * @param icon      поток для загрузки инконки
     * @param title     текст для заголовка окна
     * @param alertType тип диалога
     */
    public static void showMessageDialog(final String message,
                                         final InputStream icon,
                                         final String title,
                                         final AlertType alertType) {
        final Alert alert = new Alert(alertType, message);
        if (icon != null) {
            ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(icon));
        }
        alert.setTitle      (title);
        alert.setHeaderText (null );
        alert.getButtonTypes().clear();
        alert.getButtonTypes().add(OK);
        alert.showAndWait   ();
    }

    /**
     * Формирует диалог с возможностью выбора варианта ответа.
     *
     * @param message           сообщение
     * @param icon              поток для загрузки инконки
     * @param title             текст для заголовка окна
     * @param alertType         тип диалога
     * @param defaultButtonType тип кнопки по умолчанию
     * @param buttonTypes       типы конопок
     * @return выбранный тип кнопки
     */
    public static ButtonType showOptionDialog(final String message,
                                              final InputStream icon,
                                              final String title,
                                              final AlertType alertType,
                                              final ButtonType defaultButtonType,
                                              final ButtonType... buttonTypes) {
        final Alert alert = new Alert(alertType, message);
        if (icon != null) {
            ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(new Image(icon));
        }
        alert.setTitle      (title);
        alert.setHeaderText (null );
        alert.getButtonTypes().clear ();
        alert.getButtonTypes().addAll(buttonTypes);
        // Тут во время прохода нельзя устанавливать кнопку по умолчанию (из-за добавления).
        if (Arrays.stream(buttonTypes).noneMatch(buttonType -> buttonType.getButtonData().isCancelButton())) {
            alert.getButtonTypes().add(new ButtonType("Отмена", ButtonData.CANCEL_CLOSE));
        }
        // Устанавливаем кнопку по умолчанию
        Arrays.stream(buttonTypes).forEach(buttonType ->
                ((Button) alert.getDialogPane()
                        .lookupButton     (buttonType))
                        .setDefaultButton (buttonType.equals(defaultButtonType)));

        return alert.showAndWait().orElse(CANCEL);
    }
}
