package ru.avsh.specialist.mx.gui.utils;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static javafx.scene.control.Alert.AlertType.CONFIRMATION;
import static javafx.scene.control.ButtonBar.ButtonData;
import static javafx.scene.control.ButtonType.CANCEL;
import static javafx.scene.control.ButtonType.OK;

/**
 * Утилиты для формирования различных диалогов (JavaFX).
 *
 * @author -=AVSh=-
 */
public final class AlertUtil {
    private static final String TEXT_YES    = "Да"    ;
    private static final String TEXT_NO     = "Нет"   ;
    private static final String TEXT_CANCEL = "Отмена";

    private AlertUtil() {
        //
    }

    /**
     * Выводит диалог с сообщением.
     *
     * @param icon      иконка
     * @param title     текст для заголовка окна
     * @param font      шрифт сообщения
     * @param message   текст сообщения
     * @param alertType тип диалога
     */
    public static void showMessageDialog(final     Image icon   ,
                                         final    String title  ,
                                         final      Font font   ,
                                         final    String message,
                                         final AlertType alertType) {
        final Alert alert = new Alert(alertType, message);
        setBasicParams(alert, icon, title, font);
        alert.getButtonTypes().clear();
        alert.getButtonTypes().add(OK);
        alert.showAndWait   ();
    }

    /**
     * Формирует диалог с возможностью выбора варианта ответа.
     *
     * @param icon          иконка
     * @param title         текст для заголовка окна
     * @param font          шрифт сообщения
     * @param message       текст сообщения
     * @param alertType     тип диалога
     * @param defaultButton кнопка по умолчанию
     * @param buttons       конопки
     * @return выбранный тип кнопки
     */
    public static ButtonType showOptionDialog(final      Image    icon   ,
                                              final     String    title  ,
                                              final       Font    font   ,
                                              final     String    message,
                                              final  AlertType    alertType  ,
                                              final ButtonType    defaultButton,
                                              final ButtonType... buttons) {
        final Alert alert = new Alert(alertType, message, buttons);
        setBasicParams(alert, icon, title, font);
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
        return alert.showAndWait().orElse(CANCEL);
    }

    /**
     * Формирует подтверждающий диалог.
     *
     * @param icon    иконка
     * @param title   текст для заголовка окна
     * @param font    шрифт сообщения
     * @param message текст сообщения
     * @param option  варианты кнопок для диалога
     * @return аннотация выбранной кнопки
     */
    public static ButtonData showConfirmDialog(final  Image icon   ,
                                               final String title  ,
                                               final   Font font   ,
                                               final String message,
                                               final Option option) {
        return showOptionDialog(icon, title, font, message, CONFIRMATION,
                null, (option != null) ? option.getButtonTypes() : null).getButtonData();
    }

    /**
     * Формирует подтверждающий диалог.
     *
     * @param icon      иконка
     * @param title     текст для заголовка окна
     * @param font      шрифт сообщения
     * @param message   текст сообщения
     * @param alertType тип диалога
     * @param option    варианты кнопок для диалога
     * @return аннотация выбранной кнопки
     */
    public static ButtonData showConfirmDialog(final     Image icon     ,
                                               final    String title    ,
                                               final      Font font     ,
                                               final    String message  ,
                                               final AlertType alertType,
                                               final    Option option)  {
        return showOptionDialog(icon, title, font, message, alertType,
                null, (option != null) ? option.getButtonTypes() : null).getButtonData();
    }

    /**
     * Устанавливает базовые параметры диалога.
     *
     * @param alert диалог
     * @param icon  иконка
     * @param title текст для заголовка окна
     * @param font  шрифт сообщения
     */
    private static void setBasicParams(@NotNull final  Alert alert,
                                                final  Image icon ,
                                                final String title,
                                                final   Font font ) {
        // Добавляем иконку
        if (icon != null) {
            Optional.ofNullable(alert.getDialogPane(    ))
                    .map       ( Node::getScene  )
                    .map       (Scene::getWindow )
                    .filter    (window -> window   instanceof   Stage)
                    .map       (window -> ((Stage) window).getIcons())
                    .ifPresent (icons  -> icons.add(icon));
        }

        // Устанавливаем автоматический подбор ширины диалога (по contentText) и шрифт
        getAlertLabel(alert).ifPresent(label -> {
            label.setPrefWidth(Region.USE_COMPUTED_SIZE);

            if (font != null) {
                label.setFont(font);
            }
        });
        alert.setTitle     (title);
        alert.setHeaderText(null );
    }

    /**
     * Извлекает Label из диалога.
     *
     * @param alert диалог
     * @return объект Label в Optional
     */
    private static Optional<Label> getAlertLabel(@NotNull final Alert alert) {
        return Optional.ofNullable(alert.getDialogPane())
                .map    (DialogPane::getChildren)
                .flatMap(nodes -> nodes.stream( )
                        .filter(Objects::nonNull)
                        .filter(node -> node instanceof Label)
                        .map   (node ->          (Label) node)
                        .findFirst());
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
            return buttonTypes.clone();
        }
    }
}
