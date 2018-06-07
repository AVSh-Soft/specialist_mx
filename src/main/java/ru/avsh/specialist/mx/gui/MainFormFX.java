package ru.avsh.specialist.mx.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.gui.lib.PixelatedImageView;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.memory.sub.ScreenFx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import static javafx.geometry.Pos.CENTER;
import static javafx.scene.control.Alert.AlertType.*;
import static javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST;
import static ru.avsh.specialist.mx.gui.lib.AlertUtil.showMessageDialog;
import static ru.avsh.specialist.mx.gui.lib.AlertUtil.showOptionDialog;
import static ru.avsh.specialist.mx.helpers.Constants.*;

/**
 * Класс для формирования главного окна приложения (меню, кнопоки обработчики событий).
 *
 * @author -=AVSh=-
 */
public class MainFormFX extends Application {
    private static final double IMAGE_WIDTH         = ScreenFx.SCREEN_WIDTH ;
    private static final double IMAGE_HEIGHT        = ScreenFx.SCREEN_HEIGHT;
    private static final double IMAGE_DOUBLE_WIDTH  = ScreenFx.SCREEN_WIDTH  << 1;
    private static final double IMAGE_DOUBLE_HEIGHT = ScreenFx.SCREEN_HEIGHT << 1;

    private static final String INI_OPTION_FORM_WIDTH  = "MainFormWidth" ;
    private static final String INI_OPTION_FORM_HEIGHT = "MainFormHeight";
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
        primaryStage.getIcons().add(new Image(getResourceAsStream(SPMX_ICON_FILE)));

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

        final   ToggleGroup       group = new ToggleGroup();
        modeMXItem.setToggleGroup(group);
        modeSTItem.setToggleGroup(group);
        modeMXItem.setSelected(!fSpMX.isKeyboardMode());
        modeSTItem.setSelected( fSpMX.isKeyboardMode());

        final MenuItem aboutItem = new MenuItem("О программе...");
        final Menu     aboutMenu = new Menu    ("О программе", null, aboutItem);

        final MenuBar menuBar = new MenuBar(fileMenu, viewMenu, aboutMenu);

        final ImageView imageView = new PixelatedImageView(fSpMX.getScreen());
        imageView.setPreserveRatio(true );
        imageView.setSmooth       (false);
        imageView.setCache        (true );

        final Button   openBtn = new Button("Открыть"  );
        final Button   saveBtn = new Button("Сохранить");
        final Button  resetBtn = new Button("Сбросить" );
        final Button  debugBtn = new Button("Отладчик" );
        final HBox   buttonBox = new HBox();
        buttonBox.setSpacing(5.0);
        buttonBox.setAlignment(CENTER);
        buttonBox.setPadding(new Insets(5.0, 5.0, 5.0, 5.0));
        buttonBox.getChildren().addAll(openBtn, saveBtn, resetBtn, debugBtn);

         openBtn.setFocusTraversable(false);
         saveBtn.setFocusTraversable(false);
        resetBtn.setFocusTraversable(false);
        debugBtn.setFocusTraversable(false);

         openBtn.setTooltip(new Tooltip("Открывает (загружает и запускает) файлы *.rom, *.mon, *.cpu(i80), *.rks, *.odi"));
         saveBtn.setTooltip(new Tooltip("Сохраняет блок данных в файлы *.cpu(i80) и *.rks"));
        resetBtn.setTooltip(new Tooltip("Сбрасывает эмулятор в исходное состояние"));
        debugBtn.setTooltip(new Tooltip("Запускает отладчик"));

        final VBox  root  = new VBox(menuBar, imageView, buttonBox);
        final Scene scene = new Scene(root, -1, -1);
        primaryStage.setScene(scene);
        primaryStage.show();
        //--------------------------------------------------------------------------------------------------------------

        primaryStage.setMinWidth (primaryStage.getWidth ());
        primaryStage.setMinHeight(primaryStage.getHeight());

        final double deltaW = primaryStage.getWidth () - IMAGE_WIDTH ;
        final double deltaH = primaryStage.getHeight() - IMAGE_HEIGHT;

        imageView.fitWidthProperty().addListener((obs, oldValue, newValue) -> {
            size11Item.setSelected((double) newValue == IMAGE_WIDTH       );
            size21Item.setSelected((double) newValue == IMAGE_DOUBLE_WIDTH);
        });

        imageView.setFitWidth (IMAGE_WIDTH );
        imageView.setFitHeight(IMAGE_HEIGHT);

        primaryStage.widthProperty ().addListener((obs, oldValue, newValue) -> imageView.setFitWidth ((Double) newValue - deltaW));
        primaryStage.heightProperty().addListener((obs, oldValue, newValue) -> imageView.setFitHeight((Double) newValue - deltaH));

        // -= Открытие файла =-
        openBtn.setOnAction(event -> {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(STR_OPEN_FILE);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Файлы: *.rom, *.mon, *.cpu, *.rks, *.odi",
                    "*.rom", "*.mon", "*.cpu", "*.rks", "*.odi"));
            chooser.setInitialDirectory(new File(getCurPath()));

            final File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                final String fileName = file.getName().toLowerCase();

                setCurPath(file.getParent());

                boolean result = true;
                if (fileName.endsWith("rom")) {
                    result = fSpMX.loadFileROM(file);
                    if (result) {
                        fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, fSpMX.getShortPath(file));
                        setRomItem(romItem);
                    }
                } else if (fileName.endsWith("mon")) {
                    result = fSpMX.loadFileMON(file);
                } else if (fileName.endsWith("cpu")) {
                    result = fSpMX.loadFileCPU(file);
                } else if (fileName.endsWith("rks")) {
                    result = fSpMX.loadFileRKS(file);
                } else if (fileName.endsWith("odi")) {
                    final ButtonType     btnA = new ButtonType("[A:]");
                    final ButtonType     btnB = new ButtonType("[B:]");
                    final ButtonType selected = showOptionDialog("В какой дисковод вставить диск?",
                            getResourceAsStream(SPMX_ICON_FILE), "Выбор дисковода", CONFIRMATION, btnA, btnA, btnB);
                    if (!selected.getButtonData().isCancelButton()) {
                        final boolean   fdd = btnB.equals(selected);
                        diskInsertEject(fdd,  file, fdd ? diskBItem : diskAItem, primaryStage);
                    }
                }
                setTitle(primaryStage, result ? "" : " (Ошибка загрузки!)");
            }
        });

        // -= Сброс компьютера =-
        resetBtn.setOnAction(event -> {
            fSpMX.restart(true, false);
            setTitle(primaryStage, "");
            // Режим клавиатуры будем сбрасывать здесь.
            fSpMX.setKeyboardMode (false);
            modeMXItem.setSelected(true );
            modeSTItem.setSelected(false);
        });

        // -= Изменение размеров окна программы =-
        final EventHandler<ActionEvent> sizeEventHandler = event -> {
            final CheckMenuItem checkMenuItem = (CheckMenuItem) event.getSource();
            if (size11Item.equals(checkMenuItem)) {
                primaryStage.setWidth (deltaW + IMAGE_WIDTH );
                primaryStage.setHeight(deltaH + IMAGE_HEIGHT);
            } else {
                primaryStage.setWidth (deltaW + IMAGE_DOUBLE_WIDTH );
                primaryStage.setHeight(deltaH + IMAGE_DOUBLE_HEIGHT);
            }
            primaryStage.centerOnScreen();
        };
        size11Item.setOnAction(sizeEventHandler);
        size21Item.setOnAction(sizeEventHandler);

        exitItem.setOnAction(event -> primaryStage.fireEvent(new WindowEvent(primaryStage, WINDOW_CLOSE_REQUEST)));

        // -= Режим работы клавиатуры =-
        modeMXItem.setOnAction(event -> fSpMX.setKeyboardMode(false));
        modeSTItem.setOnAction(event -> fSpMX.setKeyboardMode(true ));

        // -= О программе =-
        aboutItem.setOnAction(event -> {
            String name      = "Эмулятор \"".concat(SPMX_NAME).concat("\"");
            String version   = "x.x.x.x";
            String copyright = "Copyright © 2018 \"AVSh Software\" (Александр Шевцов)";

            final InputStream is = getResourceAsStream(SPMX_PROP_FILE);
            if (is != null) {
                try (InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {
                    final Properties property = new Properties();
                    property.load(isr);
                    name      = property.getProperty("productName"  , name     );
                    version   = property.getProperty("versionNumber", version  );
                    copyright = property.getProperty("copyright"    , copyright);
                } catch (IOException ex) {
                    //
                }
            }
            showMessageDialog(String.format("%s v%s%n%n%s", name, version, copyright),
                    getResourceAsStream(SPMX_ICON_FILE), "Информация", INFORMATION);
        });

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
            // Запоминаем размеры формы в ini-файле
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FORM_WIDTH , primaryStage.getWidth ());
            fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FORM_HEIGHT, primaryStage.getHeight());
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

    private void setTitle(final Stage stage, final String title) {
        stage.setTitle(String.format("[%s]%s%s", fSpMX.getCurMonName(), fSpMX.getProductName(), title));
    }

    /**
     * Вставляет или извлекает диск в/из дисковод(а).
     *
     * @param fdd            false = "A" / true = "B"
     * @param file           файл с образом диска, если null, то запускается диалог выбора файла
     * @param targetMenuItem целевой пункт меню, отображающий состояние диска
     * @param stage          родительское окно
     */
    private void diskInsertEject(boolean fdd, File file,
                                 @NotNull final CheckMenuItem targetMenuItem, @NotNull Stage stage) {
        boolean insert = true; // По умолчанию вставка диска

        if (file == null) {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(STR_OPEN_FILE);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы: *.odi", "*.odi"));
            chooser.setInitialDirectory(new File(getCurPath()));

            insert = (file = chooser.showOpenDialog(stage)) != null;
            if (insert) {
                setCurPath(file.getParent());
            }
        }

        final String diskName = fdd ? DISK_B : DISK_A;
        if (insert) {
            final String fileName = "\"".concat(file.getName()).concat("\"");
            try {
                fSpMX.insertDisk(fdd, file);
                targetMenuItem.setSelected(true );
                targetMenuItem.setText(diskName.concat(fileName));
            } catch (Exception e) {
                fSpMX.ejectDisk(fdd);
                targetMenuItem.setSelected(false);
                targetMenuItem.setText(diskName.concat(NO_DISK));

                showMessageDialog(String.format("Ошибка вставки образа диска: %s%n%s", fileName, e.toString()),
                        getResourceAsStream(SPMX_ICON_FILE), "Ошибка", ERROR);
            }
        } else {
            fSpMX.ejectDisk(fdd);
            targetMenuItem.setSelected(false);
            targetMenuItem.setText(diskName.concat(NO_DISK));
        }
    }

    /**
     * Устанавливает параметры пункта меню для отображения/управления ROM-файл(а/ом) эмулятора.
     */
    private void setRomItem(@NotNull final CheckMenuItem romItem) {
        final File romFile = fSpMX.getCurRomFile();
        if ( romFile == null) {
            romItem.setSelected(false);
            //romItem.setToolTipText("");
            romItem.setText(ROM_PREF.concat("встроенный"));
        } else {
            romItem.setSelected(true);
            //romItem.setToolTipText(romFile.getPath());
            romItem.setText(ROM_PREF.concat("\"").concat(romFile.getName()).concat("\""));
        }
    }
}
