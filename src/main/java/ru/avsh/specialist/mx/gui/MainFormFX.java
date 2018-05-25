package ru.avsh.specialist.mx.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import ru.avsh.specialist.mx.root.SpecialistMX;

import static ru.avsh.specialist.mx.helpers.Constants.INI_SECTION_CONFIG;

public class MainFormFX extends Application {
    private static final String INI_OPTION_FRAME_WIDTH  = "MainFrameWidth" ;
    private static final String INI_OPTION_FRAME_HEIGHT = "MainFrameHeight";

    private final SpecialistMX fSpMX;

    /**
     * Конструктор.
     */
    public MainFormFX() {
        fSpMX = new SpecialistMX();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        ImageView imageView = new ImageView(fSpMX.getScreen());
        imageView.setPreserveRatio(true);
        imageView.setSmooth(false);
        imageView.setCache(true);

        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        //primaryStage.setMinWidth(width);
        //primaryStage.setMinHeight(height);
        primaryStage.show();



        scene.setOnKeyPressed(event -> {


            System.out.println(">>" + event.getCode());
/*
            if (imageView.isManaged()) {
                System.out.println(event.getCode());
                //fSpMX.keyCodeReceiver(true, event.getCode().ordinal());
            }
*/
        });

        scene.setOnKeyReleased(event -> {
            System.out.println("<<" + event.getCode());
/*
            if (imageView.isFocused()) {
                //fSpMX.keyCodeReceiver(false, event.getCode().ordinal());
            }
*/
        });



        // Обработчик вызывается, когда окно закрывается - для завершения работы программы
        primaryStage.setOnCloseRequest(event -> {
            // Останавливаем компьютер
            fSpMX.pause(true, true);
            // Закрываем открытые запоминающих ресурсы устройств
            fSpMX.getMemoryUnitManager().close();
            // Запоминаем размеры фрейма в ini-файл
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , primaryStage.getWidth ());
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, primaryStage.getHeight());
            // Сохраняем ini-файл
            fSpMX.storeIni();
            // Завершаем приложение JavaFX
            Platform.exit();
            // Завершаем приложение
            System.exit(0);
        });
    }
}
