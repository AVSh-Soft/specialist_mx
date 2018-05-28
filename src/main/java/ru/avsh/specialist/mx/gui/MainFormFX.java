package ru.avsh.specialist.mx.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import ru.avsh.specialist.mx.root.SpecialistMX;

import static javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST;
import static ru.avsh.specialist.mx.helpers.Constants.INI_SECTION_CONFIG;
import static ru.avsh.specialist.mx.helpers.Constants.SPMX_NAME;

/**
 * Класс для формирования главного окна приложения (меню, кнопоки обработчики событий).
 *
 * @author -=AVSh=-
 */
public class MainFormFX extends Application {
    private static final String INI_OPTION_FRAME_WIDTH = "MainFrameWidth";
    private static final String INI_OPTION_FRAME_HEIGHT = "MainFrameHeight";
    private static final String DISK_A   =   "[A:] - ";
    private static final String DISK_B   =   "[B:] - ";
    private static final String NO_DISK  = "нет диска";
    private static final String ROM_PREF =  "[ROM] - ";

    private final SpecialistMX fSpMX;

    /**
     * Конструктор.
     */
    public MainFormFX() {
        fSpMX = new SpecialistMX();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        final      MenuItem  openItem = new      MenuItem("Открыть…");
        final CheckMenuItem   romItem = new CheckMenuItem(ROM_PREF);
        final CheckMenuItem diskAItem = new CheckMenuItem(DISK_A.concat(NO_DISK));
        final CheckMenuItem diskBItem = new CheckMenuItem(DISK_B.concat(NO_DISK));
        final      MenuItem  saveItem = new      MenuItem("Сохранить блок...");
        final      MenuItem resetItem = new      MenuItem("Сбросить");
        final      MenuItem  exitItem = new      MenuItem("Выход");
        final      Menu      fileMenu = new      Menu    ("Файл", null,
                                          openItem,
                new SeparatorMenuItem(),   romItem,
                new SeparatorMenuItem(), diskAItem, diskBItem,
                new SeparatorMenuItem(),  saveItem,
                new SeparatorMenuItem(), resetItem,
                new SeparatorMenuItem(),  exitItem);

        final      MenuItem   infoItem = new      MenuItem("Состояние CPU/RAM");
        final      MenuItem  debugItem = new      MenuItem("Запуск отладчика");
        final CheckMenuItem size11Item = new CheckMenuItem("Размер 1:1");
        final CheckMenuItem size21Item = new CheckMenuItem("Размер 2:1");
        final RadioMenuItem modeMXItem = new RadioMenuItem("Клавиатура \"".concat(SPMX_NAME).concat("\""));
        final RadioMenuItem modeSTItem = new RadioMenuItem("Клавиатура \"Специалист\"");
        final      Menu       viewMenu = new      Menu    ("Просмотр", null,
                                           infoItem,  debugItem,
                new SeparatorMenuItem(), size11Item, size21Item,
                new SeparatorMenuItem(), modeMXItem, modeSTItem);

        final MenuItem aboutItem = new MenuItem("О программе...");
        final Menu     aboutMenu = new Menu    ("О программе", null, aboutItem);

        final MenuBar menuBar = new MenuBar(fileMenu, viewMenu, aboutMenu);

        final ImageView imageView = new ImageView(fSpMX.getScreen());
        imageView.setPreserveRatio(true);
        imageView.setSmooth(false);
        imageView.setCache(true);

        final VBox root = new VBox(menuBar, imageView);
        final Scene scene = new Scene(root, root.getPrefWidth(), root.getPrefHeight());
        primaryStage.setScene(scene);
        //primaryStage.setMinWidth(width);
        //primaryStage.setMinHeight(height);
        primaryStage.show();

        exitItem.setOnAction(event -> primaryStage.fireEvent(new WindowEvent(primaryStage, WINDOW_CLOSE_REQUEST)));

        // -= Обработка событий клавиатуры (нажатие) =-
        scene.setOnKeyPressed(event -> {
            final KeyCode keyCode = event.getCode();
            switch (keyCode) {
                // Нажатие на клавишу "Pause" приостанавливает компьютер
                case PAUSE:
                    if (fSpMX.isPaused()) {
                        fSpMX.pause(false, true);
                        primaryStage.setTitle("");
                    } else {
                        fSpMX.pause(true, true);
                        primaryStage.setTitle(" (пауза)");
                    }
                    break;
                // Нажатие на клавишу "Scroll Lock" запускает отладчик
                case SCROLL_LOCK:
                    fSpMX.startDebugger();
                    break;
                default:
                    fSpMX.keyCodeReceiver(true, keyCode);
            }
        });



        // -= Обработка событий клавиатуры (отпускание) =-
        scene.setOnKeyReleased(event -> fSpMX.keyCodeReceiver(false, event.getCode()));

        // Обработчик вызывается, когда окно закрывается - для завершения работы программы
        primaryStage.setOnCloseRequest(event -> {
            // Запоминаем размеры фрейма в ini-файл
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , primaryStage.getWidth ());
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, primaryStage.getHeight());
            // Сохраняем ini-файл
            fSpMX.storeIni();
            // Завершаем приложение JavaFX
            Platform.exit();
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // Останавливаем компьютер
        fSpMX.pause(true, true);
        // Закрываем открытые ресурсы запоминающих устройств
        fSpMX.getMemoryUnitManager().close();
        // Завершаем приложение
        System.exit(0);
    }
}
