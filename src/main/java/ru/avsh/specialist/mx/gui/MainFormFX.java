package ru.avsh.specialist.mx.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.SpecialistMX;

public class MainFormFX extends Application {
    private final SpecialistMX fSpMX;

    /**
     * Конструктор.
     *
     * @param spMX ссылка на объект класса SpecialistMX - "Компьютер 'Специалист MX'"
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


        scene.setOnKeyPressed(key -> {
            System.out.println(">>" + key.getCode());
/*
            if (imageView.isManaged()) {
                System.out.println(key.getCode());
                //fSpMX.keyCodeReceiver(true, key.getCode().ordinal());
            }
*/
        });

        scene.setOnKeyReleased(key -> {
            System.out.println("<<" + key.getCode());
/*
            if (imageView.isFocused()) {
                //fSpMX.keyCodeReceiver(false, key.getCode().ordinal());
            }
*/
        });
        primaryStage.setScene(scene);
        //primaryStage.setMinWidth(width);
        //primaryStage.setMinHeight(height);
        primaryStage.show();

    }
}
