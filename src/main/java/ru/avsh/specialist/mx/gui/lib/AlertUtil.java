package ru.avsh.specialist.mx.gui.lib;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static javafx.scene.control.Alert.AlertType.CONFIRMATION;
import static javafx.scene.control.ButtonBar.ButtonData;
import static javafx.scene.control.ButtonType.CANCEL;
import static javafx.scene.control.ButtonType.OK;

public final class AlertUtil {
    private static final String TEXT_YES    = "Да";
    private static final String TEXT_NO     = "Нет";
    private static final String TEXT_CANCEL = "Отмена";

    private AlertUtil() {
        //
    }

    /**
     * Выводит диалог с сообщением.
     *
     * @param icon      иконка
     * @param title     текст для заголовка окна
     * @param message   текст сообщения
     * @param alertType тип диалога
     */
    public static void showMessageDialog(final     Image icon   ,
                                         final    String title  ,
                                         final    String message,
                                         final AlertType alertType) {
        final Alert alert = new Alert(alertType, message);
        // Добавляем иконку
        if (icon != null) {
            ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(icon);
        }
        alert.setTitle      (title);
        alert.setHeaderText (null );
        alert.getButtonTypes().clear();
        alert.getButtonTypes().add(OK);

           autoWidth(alert);
        alert.showAndWait();
    }

    /**
     * Формирует диалог с возможностью выбора варианта ответа.
     *
     * @param icon          иконка
     * @param title         текст для заголовка окна
     * @param message       текст сообщения
     * @param alertType     тип диалога
     * @param defaultButton кнопка по умолчанию
     * @param buttons       конопки
     * @return выбранный тип кнопки
     */
    public static ButtonType showOptionDialog(final      Image    icon     ,
                                              final     String    title    ,
                                              final     String    message  ,
                                              final  AlertType    alertType,
                                              final ButtonType    defaultButton,
                                              final ButtonType... buttons) {
        final Alert alert = new Alert(alertType, message, buttons);
        // Добавляем иконку
        if (icon != null) {
            ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(icon);
        }
        alert.setTitle      (title);
        alert.setHeaderText (null );
        // Добавляем кнопку "Отмена", если нет отменяющих кнопок в диалоге.
        if (   (buttons !=    null)
            && (buttons.length > 0)
            && Arrays.stream(buttons).noneMatch(buttonType -> (buttonType != null) && buttonType.getButtonData().isCancelButton())) {
            alert.getButtonTypes().add(new ButtonType(TEXT_CANCEL, ButtonData.CANCEL_CLOSE));
        }
        // Устанавливаем кнопку по умолчанию
        if ((defaultButton != null) && (buttons != null) && (buttons.length > 0)) {
            Arrays.stream(buttons).forEach(buttonType ->
                    ((Button) alert.getDialogPane().lookupButton(buttonType)).setDefaultButton(defaultButton.equals(buttonType)));
        }

        autoWidth(alert);
        return alert.showAndWait().orElse(CANCEL);
    }

    /**
     * Формирует подтверждающий диалог.
     *
     * @param icon    иконка
     * @param title   текст для заголовка окна
     * @param message текст сообщения
     * @param option  варианты кнопок для диалога
     * @return аннотация выбранной кнопки
     */
    public static ButtonData showConfirmDialog(final  Image icon   ,
                                               final String title  ,
                                               final String message,
                                               final Option option) {
        return showOptionDialog(icon, title, message, CONFIRMATION,
                null, (option != null) ? option.getButtonTypes() : null).getButtonData();
    }

    /**
     * Формирует подтверждающий диалог.
     *
     * @param icon      иконка
     * @param title     текст для заголовка окна
     * @param message   текст сообщения
     * @param alertType тип диалога
     * @param option    варианты кнопок для диалога
     * @return аннотация выбранной кнопки
     */
    public static ButtonData showConfirmDialog(final     Image icon     ,
                                               final    String title    ,
                                               final    String message  ,
                                               final AlertType alertType,
                                               final    Option option)  {
        return showOptionDialog(icon, title, message, alertType,
                null, (option != null) ? option.getButtonTypes() : null).getButtonData();
    }

    /**
     * Устанавливает автоматический подбор ширины диалога (по contentText).
     *
     * @param alert диалог
     */
    private static void autoWidth(@NotNull final Alert alert) {
        alert.getDialogPane().getChildren()
                .stream    ()
                .filter    (node -> node instanceof Label)
                .findFirst ()
                .ifPresent (node -> ((Label) node).setPrefWidth(Region.USE_COMPUTED_SIZE));
    }

    /**
     * Варианты кнопок для showOptionDialog и showConfirmDialog.
     */
    public enum Option {
        YES_NO_OPTION(new ButtonType[] {
                new ButtonType(TEXT_YES   , ButtonData.YES),
                new ButtonType(TEXT_NO    , ButtonData.NO )
        }),
        YES_NO_CANCEL_OPTION(new ButtonType[] {
                new ButtonType(TEXT_YES   , ButtonData.YES),
                new ButtonType(TEXT_NO    , ButtonData.NO ),
                new ButtonType(TEXT_CANCEL, ButtonData.CANCEL_CLOSE)
        }),
        OK_CANCEL_OPTION(new ButtonType[] {
                OK,
                new ButtonType(TEXT_CANCEL, ButtonData.CANCEL_CLOSE)
        });

        private final ButtonType[] buttonTypes;

        Option(ButtonType[] buttonTypes) {
            this.buttonTypes = buttonTypes;
        }

        public ButtonType[] getButtonTypes() {
            return buttonTypes;
        }
    }
}
