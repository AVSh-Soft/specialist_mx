package ru.avsh.specialist.mx.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.gui.lib.PixelatedImageView;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.memory.sub.Screen;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static javafx.geometry.Pos.CENTER;
import static javafx.scene.control.Alert.AlertType.*;
import static javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST;
import static ru.avsh.specialist.mx.gui.lib.AlertUtil.Option.YES_NO_OPTION;
import static ru.avsh.specialist.mx.gui.lib.AlertUtil.*;
import static ru.avsh.specialist.mx.helpers.Constants.*;

/**
 * Класс для формирования главного окна приложения (меню, кнопоки обработчики событий).
 *
 * @author -=AVSh=-
 */
public class SpMxApplication extends Application {
    private static final double IMAGE_WIDTH         = Screen.SCREEN_WIDTH ;
    private static final double IMAGE_HEIGHT        = Screen.SCREEN_HEIGHT;
    private static final double IMAGE_DOUBLE_WIDTH  = Screen.SCREEN_WIDTH  << 1;
    private static final double IMAGE_DOUBLE_HEIGHT = Screen.SCREEN_HEIGHT << 1;

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
    public SpMxApplication() {
        this.fSpMX = new SpecialistMX();
    }

    @Override
    public void start(  Stage primaryStage) throws Exception {
        fSpMX.setPrimaryStage(primaryStage);

        primaryStage.getIcons().add(ICON);
        setTitle(primaryStage, "");

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

        setRomItem(romItem);

        final VBox  root  = new VBox(menuBar, imageView, buttonBox);
        final Scene scene = new Scene(root, -1, -1);
        primaryStage.setScene(scene);
        primaryStage.show();
        //--------------------------------------------------------------------------------------------------------------

        imageView.setFitWidth (IMAGE_WIDTH );
        imageView.setFitHeight(IMAGE_HEIGHT);

        primaryStage.setMinWidth (primaryStage.getWidth ());
        primaryStage.setMinHeight(primaryStage.getHeight());

        final double deltaW = primaryStage.getWidth () - IMAGE_WIDTH ;
        final double deltaH = primaryStage.getHeight() - IMAGE_HEIGHT;

        // Украшаем окна Swing
         JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        size11Item.setSelected(true );
        size21Item.setSelected(false);

        // -= Обработчик отображения галки на пунктах меню: "Размер 1:1"/"Размер 2:1" =-
        imageView.fitWidthProperty().addListener((obs, oldValue, newValue) -> {
            size11Item.setSelected(Math.round((double) newValue) == IMAGE_WIDTH       );
            size21Item.setSelected(Math.round((double) newValue) == IMAGE_DOUBLE_WIDTH);
        });

        // -= Обработчик изменения размеров главного окна =-
        primaryStage.widthProperty ().addListener((obs, oldValue, newValue) -> imageView.setFitWidth ((Double) newValue - deltaW));
        primaryStage.heightProperty().addListener((obs, oldValue, newValue) -> imageView.setFitHeight((Double) newValue - deltaH));

        // Восстанавливаем размеры главного окна из ini-файла
        final Double iniWidth  = fSpMX.getIni(INI_SECTION_CONFIG, INI_OPTION_FORM_WIDTH , Double.class);
        final Double iniHeight = fSpMX.getIni(INI_SECTION_CONFIG, INI_OPTION_FORM_HEIGHT, Double.class);
        if ((iniWidth != null) && (iniHeight != null)) {
            primaryStage.setWidth (iniWidth );
            primaryStage.setHeight(iniHeight);
        }
        primaryStage.centerOnScreen();

        // -= Открытие файла =-
        final EventHandler<ActionEvent> openEventHandler = event -> {
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
                    final ButtonType selected = showOptionDialog(ICON, "Выбор дисковода", null,
                            "В какой дисковод вставить диск?", CONFIRMATION, btnA, btnA, btnB);
                    if (!selected.getButtonData().isCancelButton()) {
                        final boolean   fdd = btnB.equals(selected);
                        diskInsertEject(fdd,  file, fdd ? diskBItem : diskAItem, primaryStage);
                    }
                }
                setTitle(primaryStage, result ? "" : "(Ошибка загрузки!)");
            }
        };
         openBtn.setOnAction(openEventHandler);
        openItem.setOnAction(openEventHandler);

        // -= Управление ROM-файлами =-
        romItem.setOnAction(event -> {
            final File curRomFile = fSpMX.getCurRomFile();
            if ((curRomFile != null) && !showConfirmDialog(ICON, "Что делать?", null,
                    "Заменить текущий ROM-файл на встроенный?", YES_NO_OPTION).isCancelButton()) {
                fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, "");
                fSpMX.reset (false, false);
            } else {
                final FileChooser chooser = new FileChooser();
                chooser.setTitle(STR_OPEN_FILE);
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файлы: *.rom", "*.rom"));
                chooser.setInitialDirectory(new File((curRomFile != null) ? curRomFile.getParent() : getCurPath()));

                final File file = chooser.showOpenDialog(primaryStage);
                if (file != null) {
                    setCurPath(file.getParent());
                    if (fSpMX.loadFileROM(file)) {
                        fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, fSpMX.getShortPath(file));
                    }
                }
            }
            setRomItem((CheckMenuItem) event.getSource());
        });

        // -= Вставка или извлечение диска "A" =-
        diskAItem.setOnAction(event -> diskInsertEject(false, null, (CheckMenuItem) event.getSource(), primaryStage));

        // -= Вставка или извлечение диска "B" =-
        diskBItem.setOnAction(event -> diskInsertEject(true , null, (CheckMenuItem) event.getSource(), primaryStage));

        // -= Сохранение файла =-
        final EventHandler<ActionEvent> saveEventHandler = event -> {
            // Блокируем главное окно
            fSpMX.setPrimaryStagePeerEnabled(false);
            // Диалог сохранения реализован в Swing
            SwingUtilities.invokeLater(() -> {
                final AtomicBoolean result = new AtomicBoolean(true);
                try {
                    final BlockSaveDialog blockSaveDialog = new BlockSaveDialog(JOptionPane.getRootFrame());
                    if (blockSaveDialog.getResult()) {
                        final   File file     = blockSaveDialog.getFile   ();
                        final String fileName = file.getName().toLowerCase();
                               if (fileName.endsWith("cpu")) {
                            result.getAndSet(fSpMX.saveFileCPU(file,
                                    blockSaveDialog.getBeginAddress(),
                                    blockSaveDialog.getEndAddress  (),
                                    blockSaveDialog.getStartAddress()));
                        } else if (fileName.endsWith("rks")) {
                            result.getAndSet(fSpMX.saveFileRKS(file,
                                    blockSaveDialog.getBeginAddress(),
                                    blockSaveDialog.getEndAddress  ()));
                        }
                    }
                    blockSaveDialog.getContentPane().removeAll();
                    blockSaveDialog.dispose();
                } finally {
                    Platform.runLater(() -> {
                        // Отменяем блокировку главного окна
                        fSpMX.setPrimaryStagePeerEnabled(true);

                        if (!result.get()) {
                            setTitle(primaryStage, "(Ошибка сохранения!)");
                        }
                    });
                }
            });
        };
         saveBtn.setOnAction(saveEventHandler);
        saveItem.setOnAction(saveEventHandler);

        // -= Сброс компьютера =-
        final EventHandler<ActionEvent> resetEventHandler = event -> {
            fSpMX.reset(true, false);
            setTitle(primaryStage, "");
            // Режим клавиатуры будем сбрасывать здесь.
            fSpMX.setKeyboardMode (false);
            modeMXItem.setSelected(true );
            modeSTItem.setSelected(false);
        };
         resetBtn.setOnAction(resetEventHandler);
        resetItem.setOnAction(resetEventHandler);

        // -= Информация =-
        infoItem.setOnAction(event -> {
            // Выполняем мгновенный останов всех устройств
            fSpMX.pause(true , true);
            // Выводим информацию
            showMessageDialog(ICON, "Информация", FONT_MONOSPACED, String.format("%s%n%s%n%n%s",
                    fSpMX.getGen().toString(), fSpMX.getCPU().toString(), fSpMX.getRAM().toString()), INFORMATION);
            // Запускаем все устройства
            fSpMX.pause(false, true);
        });

        // -= Запуск отладчика =-
         debugBtn.setOnAction(event -> fSpMX.startDebugger());
        debugItem.setOnAction(event -> fSpMX.startDebugger());

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
                try (InputStreamReader isr    = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    final Properties property = new Properties();
                    property.load(isr);
                    name      = property.getProperty("productName"  , name     );
                    version   = property.getProperty("versionNumber", version  );
                    copyright = property.getProperty("copyright"    , copyright);
                } catch (IOException ex) {
                    //
                }
            }
            showMessageDialog(ICON, "Информация", null,
                    String.format("%s v%s%n%n%s", name, version, copyright), INFORMATION);
        });

        // -= Обработка событий клавиатуры "нажатие" (перехватваем события до всех обработчиков) =-
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isConsumed() && fSpMX.keyCodeReceiver(true , event.getCode())) {
                 event.consume   ();
            }
        });

        // -= Обработка событий клавиатуры "отпускание" (перехватваем события до всех обработчиков) =-
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (!event.isConsumed() && fSpMX.keyCodeReceiver(false, event.getCode())) {
                 event.consume   ();
            }
        });

        // -= Обработка событий клавиатуры "нажатие" =-
        scene.onKeyPressedProperty().setValue(event -> {
            if (event.isConsumed()) {
                return;
            }

            switch (event.getCode()) {
                // Нажатие на клавишу "Pause" приостанавливает компьютер
                case PAUSE:
                    if (fSpMX.isPaused()) {
                        fSpMX.pause(false, true);
                        setTitle(primaryStage, "");
                    } else {
                        fSpMX.pause(true , true);
                        setTitle(primaryStage, "(пауза)");
                    }
                    event.consume();
                    break;
                // Нажатие на клавишу "Scroll Lock" запускает отладчик
                case SCROLL_LOCK:
                    fSpMX.startDebugger();
                    event.consume      ();
                    break;
                default:
            }
        });

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

    /**
     * Формирует заголовок главного окна.
     *
     * @param stage главная сцена
     * @param title заголовок
     */
    private void setTitle(@NotNull final Stage stage, @NotNull final String title) {
        stage.setTitle(String.format("[%s] - %s %s", fSpMX.getCurMonName(), fSpMX.getProductName(), title));
    }

    /**
     * Вставляет или извлекает диск в/из дисковод(а).
     *
     * @param fdd            false = "A" / true = "B"
     * @param file           файл с образом диска, если null, то запускается диалог выбора файла
     * @param targetMenuItem целевой пункт меню, отображающий состояние диска
     * @param stage          главная сцена
     */
    private void diskInsertEject(boolean fdd, File file, @NotNull final CheckMenuItem targetMenuItem, @NotNull Stage stage) {
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

                showMessageDialog(ICON, "Ошибка", null,
                        String.format("Ошибка вставки образа диска: %s%n%s", fileName, e.toString()), ERROR);
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
        if (romFile == null) {
            romItem.setSelected(false);
            //romItem.setToolTipText("") - нет этого в FX
            romItem.setText(ROM_PREF.concat("встроенный"));
        } else {
            romItem.setSelected(true);
            //romItem.setToolTipText(romFile.getPath()) - нет этого в FX
            romItem.setText(ROM_PREF.concat("\"").concat(romFile.getName()).concat("\""));
        }
    }
}
