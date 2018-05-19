package ru.avsh.specialist.mx.gui;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.SpecialistMX;
import ru.avsh.specialist.mx.units.memory.devices.MemDevScreen;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

import static ru.avsh.specialist.mx.helpers.Constants.*;

/**
 * Класс для формирования главного окна приложения (меню, кнопоки обработчики событий).
 *
 * @author -=AVSh=-
 */
public final class MainFrame extends JFrame {
    private static final long serialVersionUID = -7222591210056082993L;

    private static final String INI_OPTION_FRAME_WIDTH  = "MainFrameWidth" ;
    private static final String INI_OPTION_FRAME_HEIGHT = "MainFrameHeight";
    private static final String DISK_A   =   "[A:] - ";
    private static final String DISK_B   =   "[B:] - ";
    private static final String NO_DISK  = "нет диска";
    private static final String ROM_PREF =  "[ROM] - ";

    private final transient SpecialistMX fSpMX;

    /**
     * Конструктор.
     *
     * @param spMX ссылка на объект класса SpecialistMX - "Компьютер 'Специалист MX'"
     */
    public MainFrame(@NotNull SpecialistMX spMX) {
        fSpMX = spMX;

        final URL iconURL = getURL(SPMX_ICON_FILE);
        if (iconURL != null) {
            setIconImage(new ImageIcon(iconURL).getImage());
        }
        setTitle("");

        final JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        final JMenu fileMenu      = new JMenu("Файл");
        menuBar.add(fileMenu  );
        final JMenuItem openItem  = new JMenuItem("Открыть...");
        fileMenu.add(openItem );
        fileMenu.addSeparator();
        final JCheckBoxMenuItem   romItem = new JCheckBoxMenuItem(ROM_PREF);
        fileMenu.add(romItem  );
        fileMenu.addSeparator();
        final JCheckBoxMenuItem diskAItem = new JCheckBoxMenuItem(DISK_A.concat(NO_DISK));
        fileMenu.add(diskAItem);
        final JCheckBoxMenuItem diskBItem = new JCheckBoxMenuItem(DISK_B.concat(NO_DISK));
        fileMenu.add(diskBItem);
        fileMenu.addSeparator();
        final JMenuItem saveItem  = new JMenuItem("Сохранить блок...");
        fileMenu.add(saveItem );
        fileMenu.addSeparator();
        final JMenuItem resetItem = new JMenuItem("Сбросить");
        fileMenu.add(resetItem);
        fileMenu.addSeparator();
        final JMenuItem exitItem  = new JMenuItem("Выход");
        fileMenu.add(exitItem );

        final JMenu viewMenu      = new JMenu("Просмотр");
        menuBar.add(viewMenu   );
        final JMenuItem infoItem  = new JMenuItem("Состояние CPU/RAM");
        viewMenu.add(infoItem  );
        final JMenuItem debugItem = new JMenuItem("Запуск отладчика" );
        viewMenu.add(debugItem );
        viewMenu.addSeparator( );
        final JCheckBoxMenuItem size11Item = new JCheckBoxMenuItem("Размер 1:1");
        viewMenu.add(size11Item);
        final JCheckBoxMenuItem size21Item = new JCheckBoxMenuItem("Размер 2:1");
        viewMenu.add(size21Item);
        viewMenu.addSeparator( );
        final JRadioButtonMenuItem modeMXItem = new JRadioButtonMenuItem("Клавиатура \"".concat(SPMX_NAME).concat("\""));
        modeMXItem.setSelected(!fSpMX.isKeyboardMode());
        final JRadioButtonMenuItem modeSTItem = new JRadioButtonMenuItem("Клавиатура \"Специалист\"");
        modeSTItem.setSelected( fSpMX.isKeyboardMode());
        viewMenu.add(modeMXItem);
        viewMenu.add(modeSTItem);

        final JMenu aboutMenu     = new JMenu("О программе");
        menuBar.add(aboutMenu  );
        final JMenuItem aboutItem = new JMenuItem("О программе...");
        aboutMenu.add(aboutItem);

        final JPanel controlPanel = new JPanel();

        final JButton  openBtn = new JButton( "Открыть" );
        final JButton  saveBtn = new JButton("Сохранить");
        final JButton resetBtn = new JButton("Сбросить" );
        final JButton debugBtn = new JButton("Отладчик" );

         openBtn.setToolTipText("Открывает (загружает и запускает) файлы *.rom, *.mon, *.cpu(i80), *.rks, *.odi");
         saveBtn.setToolTipText("Сохраняет блок данных в файлы *.cpu(i80) и *.rks");
        resetBtn.setToolTipText("Сбрасывает эмулятор в исходное состояние");
        debugBtn.setToolTipText("Запускает отладчик");

        setRomItem(romItem);

        controlPanel.add(openBtn );
        controlPanel.add(saveBtn );
        controlPanel.add(resetBtn);
        controlPanel.add(debugBtn );

        add(fSpMX.getScreen(), BorderLayout.CENTER);
        add(controlPanel     , BorderLayout.SOUTH );

        // -=-=-=-=- Обработчики событий -=-=-=-=-
        // -= Обработка событий клавиатуры =-
        final KeyEventDispatcher keyEventDispatcher = e -> {
            final int id      = e.getID();
            final int keyCode = e.getKeyCode();
            if (this.isActive() && ((id == KeyEvent.KEY_PRESSED) || (id == KeyEvent.KEY_RELEASED))) {
                if (id == KeyEvent.KEY_PRESSED) {
                    switch (keyCode) {
                        // Нажатие на клавишу "Pause" приостанавливает компьютер
                        case KeyEvent.VK_PAUSE:
                            if (fSpMX.isPaused()) {
                                fSpMX.pause(false, true);
                                setTitle("");
                            } else {
                                fSpMX.pause(true , true);
                                setTitle(" (пауза)");
                            }
                            break;
                        // Нажатие на клавишу "Scroll Lock" запускает отладчик
                        case KeyEvent.VK_SCROLL_LOCK:
                            fSpMX.startDebugger();
                            break;
                        default:
                            fSpMX.keyCodeReceiver(true, keyCode);
                    }
                } else {
                    fSpMX.keyCodeReceiver(false, keyCode);
                }
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);

        // -= Открытие файла =-
        openBtn.addActionListener(e -> {
            final JFileChooser chooser = new JFileChooser(getCurPath());
            chooser.setDialogTitle(STR_OPEN_FILE);
            chooser.setFileFilter(new FileNameExtensionFilter("Файлы: *.rom, *.mon, *.cpu, *.rks, *.odi", "rom", "mon", "cpu", "rks", "odi"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                final File   file     =    chooser.getSelectedFile();
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
                    Object[] options = {"[A:]", "[B:]"};
                    final int selected  = JOptionPane.showOptionDialog(this, "В какой дисковод вставить диск?",
                            "Выбор дисковода", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                    if (selected != JOptionPane.CLOSED_OPTION) {
                        final boolean fdd = selected == JOptionPane.NO_OPTION;
                        diskInsertEject(fdd, file, fdd ? diskBItem : diskAItem);
                    }
                }
                setTitle(result ? "" : " (Ошибка загрузки!)");
            }
        });

        // -= Управление ROM-файлами =-
        romItem.addActionListener(e -> {
            final File curRomFile = fSpMX.getCurRomFile();
            if ((curRomFile != null) && (JOptionPane.showConfirmDialog(this,
                    "Заменить текущий ROM-файл на встроенный?", "Что делать?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)) {
                fSpMX.putIni (INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, "");
                fSpMX.restart(false, false);
            } else {
                final JFileChooser chooser = new JFileChooser((curRomFile != null) ? curRomFile.getParent() : getCurPath());
                chooser.setDialogTitle(STR_OPEN_FILE);
                chooser.setFileFilter (new FileNameExtensionFilter("Файлы: *.rom", "rom"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    final File file = chooser.getSelectedFile();
                    setCurPath(file.getParent());
                    if (fSpMX.loadFileROM(file)) {
                        fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, fSpMX.getShortPath(file));
                    }
                }
            }
            setRomItem((JCheckBoxMenuItem) e.getSource());
        });

        // -= Вставка или извлечение диска "A" =-
        diskAItem.addActionListener(e -> diskInsertEject(false, null, (JMenuItem) e.getSource()));

        // -= Вставка или извлечение диска "B" =-
        diskBItem.addActionListener(e -> diskInsertEject(true , null, (JMenuItem) e.getSource()));

        // -= Сохранение файла =-
        saveBtn.addActionListener(e -> {
            final BlockSaveDialog blockSaveDialog = new BlockSaveDialog(this);

            boolean result = true;
            if (blockSaveDialog.getResult()) {
                final File   file     = blockSaveDialog.getFile   ();
                final String fileName = file.getName().toLowerCase();
                if        (fileName.endsWith("cpu")) {
                    result = fSpMX.saveFileCPU(file, blockSaveDialog.getBeginAddress(), blockSaveDialog.getEndAddress(), blockSaveDialog.getStartAddress());
                } else if (fileName.endsWith("rks")) {
                    result = fSpMX.saveFileRKS(file, blockSaveDialog.getBeginAddress(), blockSaveDialog.getEndAddress());
                }
            }
            blockSaveDialog.getContentPane().removeAll();
            blockSaveDialog.dispose();

            if (!result) {
                setTitle(" (Ошибка сохранения!)");
            }
        });

        // -= Сброс компьютера =-
        resetBtn.addActionListener(e -> {
            fSpMX.restart(true, false);
            setTitle("");
            // Режим клавиатуры будем сбрасывать здесь.
            fSpMX.setKeyboardMode (false);
            modeMXItem.setSelected(true );
            modeSTItem.setSelected(false);
        });

        // -= Информация =-
        infoItem.addActionListener(e -> {
            // Выполняем мгновенный останов всех устройств
            fSpMX.pause(true , true);
            // Выводим информацию
            JOptionPane.showMessageDialog(this,
                    String.format("%s%n%s%n%n%s", fSpMX.getGen().toString(), fSpMX.getCPU().toString(), fSpMX.getRAM().toString()),
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            // Запускаем все устройства
            fSpMX.pause(false, true);
        });

        // -= Запуск отладчика =-
        debugBtn.addActionListener(e -> fSpMX.startDebugger());

        // -= Изменение размеров окна программы =-
        size11Item.addActionListener(e -> {
            ((JCheckBoxMenuItem)e.getSource()).setSelected(true);
            setSize(getMinimumSize());
            setLocationRelativeTo(null);
        });
        size21Item.addActionListener(e -> {
            ((JCheckBoxMenuItem)e.getSource()).setSelected(true);
            final Dimension dim = getMinimumSize();
            setSize(dim.width + MemDevScreen.SCREEN_WIDTH, dim.height + MemDevScreen.SCREEN_HEIGHT);
            setLocationRelativeTo(null);
        });

        // -= Режим работы клавиатуры =-
        modeMXItem.addActionListener(e -> {
            fSpMX.setKeyboardMode (false);
            modeSTItem.setSelected(false);
        });
        modeSTItem.addActionListener(e -> {
            fSpMX.setKeyboardMode (true );
            modeMXItem.setSelected(false);
        });

        // -= О программе =-
        aboutItem.addActionListener(e -> {
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
            JOptionPane.showMessageDialog(this, String.format("%s v%s%n%n%s", name, version, copyright), "Информация", JOptionPane.INFORMATION_MESSAGE);
        });

         openItem.addActionListener( openBtn.getActionListeners()[0]);
         saveItem.addActionListener( saveBtn.getActionListeners()[0]);
        resetItem.addActionListener(resetBtn.getActionListeners()[0]);
         exitItem.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        debugItem.addActionListener(debugBtn.getActionListeners()[0]);

        // -= Обработчики событий компонента =-
        addComponentListener(new ComponentAdapter() {
            // Обработчик вызывается, при изменении размеров фрейма
            @Override
            public void componentResized(ComponentEvent e) {
                final Dimension cur = getSize();
                final Dimension dim = getMinimumSize();
                size11Item.setSelected(cur.equals(dim));
                dim.setSize(dim.width + MemDevScreen.SCREEN_WIDTH, dim.height + MemDevScreen.SCREEN_HEIGHT);
                size21Item.setSelected(getSize().equals(dim));
            }
        });

        // -= Обработчики событий окна приложения =-
        addWindowListener(new WindowAdapter() {
            // Обработчик вызывается, когда окно деактивировано - для очистки буфера клавиатуры
            @Override
            public void windowDeactivated(WindowEvent e) {
                fSpMX.clearKeyBuffer();
            }

            // Обработчик вызывается, когда окно закрывается - для завершения работы программы
            @Override
            public void windowClosing(WindowEvent event) {
                // Останавливаем компьютер
                fSpMX.pause(true, true);
                // Закрываем открытые ресурсы устройств памяти
                fSpMX.getMemDevMng().closeMemoryDevices();
                // Запоминаем размеры фрейма в ini-файл
                fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , getWidth ());
                fSpMX.putIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, getHeight());
                // Сохраняем ini-файл
                fSpMX.storeIni();
                // Освобождаем ресурсы главного фрейма (окна)
                event.getWindow().dispose();
                // Завершаем приложение
                System.exit(0);
            }
        });

        // -=-=-=-=- Завершение формирования окна эмулятора -=-=-=-=-
        pack();
        // Здесь после pack() для закрепления полученых размеров окна
        setMinimumSize(new Dimension(getWidth(), getHeight()));

        // Восстанавливаем размеры фрейма из ini-файла
        {
            final Integer width  = fSpMX.getIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , Integer.class);
            final Integer height = fSpMX.getIni(INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, Integer.class);
            if ((width != null) && (height != null)) {
                setSize(width, height);
            }
        }
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(String.format("[%s]%s%s", fSpMX.getCurMonName(), fSpMX.getProductName(), title));
    }

    /**
     * Вставляет или извлекает диск в/из дисковод(а).
     *
     * @param fdd            false = "A" / true = "B"
     * @param file           файл с образом диска, если null, то запускается диалог выбора файла
     * @param targetMenuItem целевой пункт меню, отображающий состояние диска
     */
    private void diskInsertEject(boolean fdd, File file, @NotNull final JMenuItem targetMenuItem) {
        boolean insert = true; // По умолчанию вставка диска

        if (file == null) {
            final JFileChooser chooser = new JFileChooser(getCurPath());
            chooser.setDialogTitle(STR_OPEN_FILE);
            chooser.setFileFilter (new FileNameExtensionFilter("Файлы: *.odi", "odi"));
            insert = chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION;
            if (insert) {
                file = chooser.getSelectedFile() ;
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
                JOptionPane.showMessageDialog(this, String.format("Ошибка вставки образа диска: %s%n%s", fileName, e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
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
    private void setRomItem(@NotNull final JCheckBoxMenuItem romItem) {
        final File romFile = fSpMX.getCurRomFile();
        if ( romFile == null) {
             romItem.setSelected(false);
             romItem.setToolTipText("");
             romItem.setText(ROM_PREF.concat("встроенный"));
        } else {
             romItem.setSelected(true);
             romItem.setToolTipText(romFile.getPath());
             romItem.setText(ROM_PREF.concat("\"").concat(romFile.getName()).concat("\""));
        }
    }
}