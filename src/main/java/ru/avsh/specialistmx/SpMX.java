package ru.avsh.specialistmx;

import ru.avsh.lib.cFileFinder;
import org.ini4j.Wini;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.io.*;
import java.util.List;
import java.util.Properties;

/**
 * Класс "Компьютер 'Специалист MX'".
 * @author -=AVSh=-
 */
final class SpMX {
    private final String fProductName;

    private final I8080 fCPU;
    private final cMD_SpMX_RAM fRAM;
    private final cMD_SpMX_FDC fFDC;
    private final cClockGenerator fGen;
    private final cMD_SpMX_Screen fScr;
    private final cMD_SpMX_KeyPort fKey;
    private final cMemoryDevicesManager fMemDevMng;
    /* Пока не используем!
    private final cMemoryDevicesManager fInOutDevMng; */

    private Wini     fIni;
    private cSpeaker fSpc;
    private boolean  fDebugRun;
    private JFrame   fMainFrame;
    private String   fCurMonName;
    private File     fCurRomFile;

    /**
     * Конструктор.
     */
    SpMX() {
        // Создаем объект для работы с ini-файлом настроек
        fIni = new Wini();
        fIni.setFile(new File(ConsStat.INI_FILE));
        try {
            if (fIni.getFile().exists()) {
                fIni.load (); // читаем настроки
            } else {
                fIni.store(); // создаем ini-файл
            }
        } catch (IOException e) {
            fIni = null;
        }

        // Читаем номер версии эмулятора из внутренних настроек
        fProductName = readProductName();

        // Создаем тактовый генератор
        fGen = new cClockGenerator();
        // Создаем диспетчер устройств памяти
        fMemDevMng = new cMemoryDevicesManager();
        // Создаем CPU
        fCPU = new I8080(this, fMemDevMng, null); // fInOutDevMng - пока не используем!
        // Создаем Speaker
        try {
            fSpc = new cSpeaker(fGen);
        } catch (LineUnavailableException e) {
            fSpc = null;
        }
        // Создаем устройства памяти
        fScr = new cMD_SpMX_Screen (    );
        fRAM = new cMD_SpMX_RAM    (ConsStat.NUMBER_PAGES_RAMDISK + 1, fScr); // RAM + RAM-диск (8 страниц) + ROM-диск
        fKey = new cMD_SpMX_KeyPort(fSpc);
        fFDC = new cMD_SpMX_FDC    (fGen, fCPU);
        cMD_SimpleRAM      excRAM  = new cMD_SimpleRAM     (0x20 );
        cMD_SpMX_Timer     timer   = new cMD_SpMX_Timer    (fSpc );
        cMD_SpMX_PrgPort   prgPort = new cMD_SpMX_PrgPort  (timer);
        cMD_SpMX_FDC_Port  fdcPort = new cMD_SpMX_FDC_Port (fFDC );
        cMD_SpMX_ColorPort colPort = new cMD_SpMX_ColorPort(fScr );
        cMD_SpMX_RAM_Port  ramPort = new cMD_SpMX_RAM_Port (fRAM );

        // Добавляем тактируемые устройства в тактововый генератор
        fGen.addClockedDevice(fCPU );
        fGen.addClockedDevice(timer);

        // Добавляем устройства памяти в диспетчер устройств памяти
        fMemDevMng.addMemoryDevice(0x0000, fRAM   );
        fMemDevMng.addMemoryDevice(0x9000, fScr   );
        fMemDevMng.addMemoryDevice(0xFFC0, excRAM );
        fMemDevMng.addMemoryDevice(0xFFE0, fKey   );
        fMemDevMng.addMemoryDevice(0xFFE4, prgPort);
        fMemDevMng.addMemoryDevice(0xFFE8, fFDC   );
        fMemDevMng.addMemoryDevice(0xFFEC, timer  );
        fMemDevMng.addMemoryDevice(0xFFF0, fdcPort);
        fMemDevMng.addMemoryDevice(0xFFF8, colPort);
        fMemDevMng.addMemoryDevice(0xFFFC, ramPort);

        // Инициализируем переменную под имя текущего MON-файла
        fCurMonName = "";

        // Русифицируем JFileChooser
        UIManager.put("FileChooser.openButtonText"         , "Открыть"  );
        UIManager.put("FileChooser.saveButtonText"         , "Сохранить");
        UIManager.put("FileChooser.cancelButtonText"       , "Отменить" );
        UIManager.put("FileChooser.directoryOpenButtonText", "Открыть"  );

        UIManager.put("FileChooser.lookInLabelText"     , "Смотреть в:" );
        UIManager.put("FileChooser.saveInLabelText"     , "Сохранить в:");
        UIManager.put("FileChooser.fileNameLabelText"   , "Имя файла:"  );
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип файла:"  );

        UIManager.put("FileChooser.openButtonToolTipText"  , "Открыть выделенный файл"     );
        UIManager.put("FileChooser.saveButtonToolTipText"  , "Сохранить выбранный файл"    );
        UIManager.put("FileChooser.cancelButtonToolTipText", "Прервать диалог выбора файла");

        UIManager.put("FileChooser.listViewButtonToolTipText"     , "Список"       );
        UIManager.put("FileChooser.detailsViewButtonToolTipText"  , "Детально"     );
        UIManager.put("FileChooser.directoryOpenButtonToolTipText", "Открыть папку");

        UIManager.put("FileChooser.upFolderToolTipText"  , "На уровень выше");
        UIManager.put("FileChooser.homeFolderToolTipText", "В начало"       );
        UIManager.put("FileChooser.newFolderToolTipText" , "Создать папку"  );

        UIManager.put("FileChooser.fileNameHeaderText", "Имя"     );
        UIManager.put("FileChooser.fileSizeHeaderText", "Размер"  );
        UIManager.put("FileChooser.fileTypeHeaderText", "Тип"     );
        UIManager.put("FileChooser.fileDateHeaderText", "Изменен" );
        UIManager.put("FileChooser.fileAttrHeaderText", "Атрибуты");

        UIManager.put("FileChooser.acceptAllFileFilterText", "Все файлы");

        // Русифицируем JOptionPane
        UIManager.put("OptionPane.yesButtonText"   , "Да"      );
        UIManager.put("OptionPane.noButtonText"    , "Нет"     );
        UIManager.put("OptionPane.cancelButtonText", "Отменить");

        // Запускаем тактовый генератор
        new Thread(fGen).start();
        // Запускаем эмулятор
        restart(false, true);
    }

    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
    // Методы для связи между объектами
    /**
     * Запоминает ссылку на главное окно приложения.
     * @param frame ссылка на главное окно
     */
    void setMainFrame(JFrame frame) {
        fMainFrame = frame;
    }

    /**
     * Возвращает ссылку на главное окно приложения.
     * @return ссылка на главное окно
     */
    JFrame getMainFrame() {
        return fMainFrame;
    }

    /**
     * Возвращает ссылку на тактовый генератор.
     * @return ссылка на тактовый генератор
     */
    cClockGenerator getGen() {
        return fGen;
    }

    /**
     * Возвращает ссылку на диспетчер устройств памяти.
     * @return ссылка на диспетчер устройств памяти
     */
    cMemoryDevicesManager getMemDevMng() {
        return fMemDevMng;
    }

    /**
     * Возвращает ссылку на CPU.
     * @return ссылка на CPU
     */
    I8080 getCPU() {
        return fCPU;
    }

    /**
     * Возвращает ссылку на память.
     * @return ссылка на память
     */
    cMD_SpMX_RAM getRAM() {
        return fRAM;
    }

    /**
     * Возвращает ссылку на экран.
     * @return ссылка на экран
     */
    cMD_SpMX_Screen getScreen() {
        return fScr;
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
    // Делегированные методы
    /**
     * Показывает приостановлен тактовый генератор или нет.
     * @return - true = CPU приостановлен
     */
    boolean isPaused() {
        return fGen.isPaused();
    }

    /**
     * Переводит тактовый генератор в режим "Пауза".
     * @param mode true/false = установить/снять режим "Пауза"
     * @param dev true = устанавливать/снимать режим "Пауза" и для устройств памяти
     */
    void pause(boolean mode, boolean dev) {
        fGen.pause(mode, dev);
    }

    /**
     * Возвращает номер текущей страницы памяти.
     * @return номер страницы
     */
    int getPage() {
        return fRAM.getPage();
    }

    /**
     * Устанавливает заданную страницу памяти.
     * @param pageNumber от 0-8 - страницы RAM, 9 или больше - страница ROM
     */
    void setPage(int pageNumber) {
        fRAM.setPage(pageNumber);
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    int readByte(int address) {
        return fMemDevMng.readByte(address);
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * (Метод для вызова из отладчика - минимизирует влияние отладчика на работу устройств памяти)
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    int debugReadByte(int address) {
        return fMemDevMng.debugReadByte(address);
    }

    /**
     * Записывает байт по заданному адресу в устройство/устройства памяти.
     * @param address заданный адрес
     * @param value записываемый байт (байт представлен как int)
     */
    void writeByte(int address, int value) {
        fMemDevMng.writeByte(address, value);
    }

    /**
     * Вставляет диск в заданный дисковод.
     * @param fdd false = "A" / true = "B"
     * @param file файл с образом диска
     * @throws Exception исключение, возникающее при вставке диска (SecurityException, IllegalArgumentException, FileNotFoundException)
     */
    void insertDisk(boolean fdd, File file) throws Exception {
        fFDC.insertDisk(fdd, file);
    }

    /**
     * Извлекает диск из заданного дисковода.
     * @param fdd false = "A" / true = "B"
     */
    void ejectDisk(boolean fdd) {
        fFDC.ejectDisk(fdd);
    }

    /**
     * Возвращает режим работы клавиатуры.
     * @return false = "Специалист MX" / true = стандартный "Специалист"
     */
    boolean isKeyboardMode() {
        return fKey.isKeyboardMode();
    }

    /**
     * Устанавливает режим работы клавиатуры.
     * @param keyboardMode false = "Специалист MX" / true = стандартный "Специалист"
     */
    void setKeyboardMode(boolean keyboardMode) {
        fKey.setKeyboardMode(keyboardMode);
    }

    /**
     * Очищает клавиатурный буфер.
     */
    void clearKeyBuffer() {
        fKey.clearKeyBuffer();
    }

    /**
     * Принимает коды клавиш.
     * @param flagKeyPressed true = клавиша нажата, false = клавиша отпущена
     * @param keyCode код клавиши
     */
    void keyCodeReceiver(boolean flagKeyPressed, int keyCode) {
        fKey.keyCodeReceiver(flagKeyPressed, keyCode);
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

    /**
     * Читает значение из ini-файла.
     * @param sectionName имя секции
     * @param optionName имя опции
     * @param clazz класс
     * @param <T> тип
     * @return полученное значение
     */
    <T> T getIni(Object sectionName, Object optionName, Class<T> clazz) {
        if (fIni != null) {
            return fIni.get(sectionName, optionName, clazz);
        }
        return null;
    }

    /**
     * Записывает значение в ini-файл.
     * @param sectionName имя секции
     * @param optionName имя опции
     * @param value значение
     */
    void putIni(String sectionName, String optionName, Object value) {
        if (fIni != null) {
            fIni.put(sectionName, optionName, value);
        }
    }

    /**
     * Сохраняет ini-файл на диск.
     */
    void storeIni() {
        try {
            fIni.store();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Возвращает наиболее короткий путь к файлу (полный или относительный).
     * @param file файл
     * @return путь
     */
    String getShortPath(File file) {
        String p0 = file.getPath();
        String p1 = ConsStat.APP_PATH.relativize(file.toPath()).toString();
        return (p0.length() < p1.length()) ? p0 : p1;
    }

    /**
     * Читает название эмулятора и номер его версии из properties-файла.
     * @return название эмулятора и номер его версии
     */
    private String readProductName() {
        String ver  = "x.x.x.x";
        try (InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream(ConsStat.RESOURCES.concat(ConsStat.SPMX_PROP_FILE)),"UTF-8")) {
            Properties property    = new Properties();
            property.load(isr);
            return String.format(" - \"%s\" v%s", property.getProperty("appName", ConsStat.SPMX_NAME), property.getProperty("versionNumber", ver));
        } catch (IOException e) {
            return String.format(" - \"%s\" v%s", ConsStat.SPMX_NAME, ver);
        }
    }

    /**
     * Выполняет запуск с заданного адреса.
     * @param address адрес запуска
     */
    private void run(int address) {
        pause(true, true);
        fCPU.run(address);
        // Проверям ловушки (стартовые ловушки не отслеживаются в классе I8080)
        if (fCPU.debugIsTrap(getPage(), address)) {
            startDebugger();
        } else {
            pause(false, true);
        }
    }

    /**
     * Выполняет сброс компьютера.
     * @param address адрес запуска
     * @param resetMemoryDevices true = выполняет сброс устройств памяти
     */
    private void reset(int address, boolean resetMemoryDevices) {
        pause(true, true);
        fCPU.reset(address, resetMemoryDevices);
        // Проверям ловушки (стартовые ловушки не отслеживаются в классе I8080)
        if (fCPU.debugIsTrap(getPage(), address)) {
            startDebugger();
        } else {
            pause(false, true);
        }
    }

    /**
     * Вычисляет контрольную сумму по алгоритму RAMFOS (подпрограмма 0xC82A).
     * @param buf буфер
     * @param start начало блока данных в буфере
     * @param length длина блока данных
     * @return контрольная сумма
     */
    private int getChecksum(byte[] buf, int start, int length) {
        length += start - 1; // вычисляем конец блока в буфере
        if (length < buf.length) {
            int lo = 0, hi = 0, cur;
            do {
                cur = buf[start++] & 0xFF;
                lo += cur;
                if (start <= length) {
                    hi += cur;
                    if (lo  > 0xFF) {
                        lo &= 0xFF;
                        hi++;
                    }
                }
            } while (start <= length);
            return (hi & 0xFF) << 8 | (lo & 0xFF);
        }
        return 0;
    }

    /**
     * Загружает в память OS/BIOS (ROM-файл из ресурсов) эмулятора "Специалист MX".
     * @throws IOException исключение
     */
    private void loadROM() throws IOException {
        // Если ROM-файл прописан в ini-файле и он имеется на диске, то загружаем внешний ROM-файл
        String romPath   = getIni(ConsStat.INI_SECTION_CONFIG, ConsStat.INI_OPTION_ROM_FILE, String.class);
        if ((  romPath  != null) && !romPath.equals("")) {
            File romFile =  new File(romPath);
            if ( romFile.exists() && romFile.isFile()) {
                try {
                    loadFile(romFile, 0x0000, 0, 0, -1);
                    // Запоминаем ROM-файл
                    fCurRomFile = romFile;
                    return;
                } catch (IOException e) {
                    // Если были ошибки загрузки внешнего ROM-файла - загружаем встроенный ROM-файл
                }
            }
        }
        // Иначе загружаем встроенный ROM-файл
        fCurRomFile = null;
        try (BufferedInputStream bis = new BufferedInputStream(getClass().getResourceAsStream(ConsStat.RESOURCES.concat(ConsStat.SPMX_ROM_FILE)))) {
            int length = bis.available();
            if (length < 0x10000) {
                byte[] buf = new byte[length];
                if (bis.read(buf, 0, length) < length) {
                    throw new IOException("Не удалось полностью прочитать ROM-файл эмулятора из ресурсов!");
                }
                // Перемещаем данные из буфера в память через менеджер устройств памяти
                if (isPaused()) {
                    int address = 0x0000;
                    for (byte data : buf) {
                        writeByte(address++, data); // Вызываем синхронизированный метод
                    }
                } else {
                    throw new IOException("Процессор эмулятора не находится в состоянии \"Пауза\" - невозможно загрузить в память ROM-файл эмулятора!");
                }
            } else {
                throw new IOException("Невозможно загрузить в память ROM-файл эмулятора из ресурсов!");
            }
        }
    }

    /**
     * Загружает данные с заданным смещением и длиной из файла в память по заданному адресу.
     * @param file файл
     * @param address адрес
     * @param offset смещение в файле (игнорируется если <= 0)
     * @param length необходимая длина (игнорируется если <= 0)
     * @param checksum контрольная сумма для проверки (проверка игнорируется, если контрольная сумма < 0)
     * @throws IOException исключение
     */
    private void loadFile(File file, int address, int offset, int length, int checksum) throws IOException {
        if ((address >= 0) && (address <= 0xFFFF)) {
            try (FileInputStream fis = new FileInputStream(file)) {
                String fileName = "\"".concat(file.getName()).concat("\"");
                if (offset > 0 && fis.skip(offset) != offset) {
                    throw new IOException(String.format("Не удалось выполнить смещение на %d байт(а/ов) в файле: %s", offset, fileName));
                }
                if (length > 0 && fis.available() < length) {
                    throw new IOException(String.format("Невозможно прочитать %d байт(а/ов) из файла: %s", length, fileName));
                }
                if (length <= 0) {
                    length = fis.available();
                }
                if (length + address <= 0x10000) {
                    byte[] buf = new byte[length];
                    if (fis.read(buf, 0, length) < length) {
                        throw new IOException("Не удалось полностью прочитать файл: ".concat(fileName));
                    }
                    // Проверим контрольную сумму данных
                    if (checksum >= 0) {
                        int  curChecksum  = getChecksum(buf, 0, length);
                        if ((curChecksum != checksum) &&
                                (JOptionPane.showConfirmDialog(fMainFrame,
                                        String.format("В файле: %s\n" +
                                                      "Рассчитанная контрольная сумма  данных: [%04X]\n"    +
                                                      "не равна проверочной контрольной сумме: [%04X]\n\n" +
                                                      "Загружать файл?", fileName, curChecksum, checksum),
                                        "Загружать?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)) {
                            throw new IOException("Не прошла проверка контрольной суммы в файле: ".concat(fileName));
                        }
                    }
                    // Перемещаем данные из буфера в память через менеджер устройств памяти
                    if (isPaused()) {
                        for (byte data : buf) {
                            writeByte(address++, data); // Вызываем синхронизированный метод
                        }
                    } else {
                        throw new IOException("Процессор эмулятора не находится в состоянии \"Пауза\" - невозможно загрузить в память файл: ".concat(fileName));
                    }
                } else {
                    throw new IOException("Невозможно загрузить в память файл: ".concat(fileName));
                }
            }
        } else {
            throw new IOException(String.format("Заданный адрес загрузки [%H] находится вне диапазаона возможных адресов [0..0xFFFF] памяти!", address));
        }
    }

    /**
     * Перезапускает компьютер "Специалист MX".
     * @param clearDialog true = выводится диалог очистки памяти
     * @param clear true = выполняется очистка памяти (перекрывается параметром clearDialog)
     * @return false = перезапуск не удался
     */
    boolean restart(boolean clearDialog, boolean clear) {
        try {
            if (clearDialog) {
                Object[] options = {"Да", "Нет"};
                int selected = JOptionPane.showOptionDialog(fMainFrame, "Очистить память?", "Очистить?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
                // Если диалог закрыт крестом - отменяем перезапуск
                if (selected == JOptionPane.CLOSED_OPTION) {
                    return true;
                } else {
                    clear = selected == JOptionPane.YES_OPTION;
                }
            }
            // Приостанавливаем компьютер
            pause(true, true);
            // Сбрасываем устройства памяти (с полной очисткой или нет)
            fMemDevMng.resetMemoryDevices(clear);
            // Сбрасываем устройства ввода/вывода (с полной очисткой или нет)
            /* Пока не используем!
            if (fInOutDevMng != null) {
                fInOutDevMng.resetMemoryDevices(clear);
            }*/
            // Очищаем все ловушки, если выбрана полная очистка
            if (clear) {
                fCPU.debugClearTraps();
            }
            // Включаем ROM-диск
            setPage(cMD_SpMX_RAM.ROM_DISK);
            // Загружаем BIOS "Специалиста_MX"
            loadROM();
            // Запоминаем имя ROM-файла
            fCurMonName = ConsStat.SPMX_ROM_FILE.toLowerCase();
            // Сбрасываем CPU с адреса 0x0000 (сброс устройств памяти устанавливает страницу памяти 0, что здесь не подходит)
            reset(0x0000, false);
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Загружает и запускает ROM-файл.
     * @param file ROM-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileROM(File file) {
        // Получаем имя ROM-файла
        String fileName = file.getName();
        try {
            // Приостанавливаем компьютер
            pause(true, true);
            // Сбрасываем устройства памяти (без полной очистки)
            fMemDevMng.resetMemoryDevices(false);
            // Включаем ROM-диск
            setPage(cMD_SpMX_RAM.ROM_DISK);
            try {
                // Загружаем ROM-файл в страницу ROM-диска
                loadFile(file, 0x0000, 0, 0, -1);
                // Запоминаем ROM-файл
                fCurRomFile = file;
            } catch (IOException e) {
                // В случае ошибки выполняем сброс
                restart(false, false);
                throw e;
            }
            // Сбрасываем CPU с адреса 0x0000 (сброс устройств памяти устанавливает страницу памяти 0, что здесь не подходит)
            reset(0x0000, false);
            return true;
        } catch (NumberFormatException | IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка загрузки ROM-файла: \"%s\"\n%s", fileName, e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Загружает и запускает MON-файл.
     * Формат MON-файла: "nm_addr.MON" - nm = имя, addr = адрес.
     * @param file MON-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileMON(File file) {
        // Получаем имя MON-файла
        String fileName = file.getName();
        // Проверяем длину имени MON-файла
        if (fileName.length() == 11) {
            try {
                // Получаем адрес из имени MON-файла
                int address = Integer.parseInt(fileName.substring(3, 7), 16);
                // Приостанавливаем компьютер
                pause(true, true);
                // Запоминаем текущую страницу памяти
                int curPage = getPage();
                // Включаем основную страницу памяти
                setPage(0);
                try {
                    // Загружаем MON-файл в основную страницу памяти
                    loadFile(file, address, 0, 0, -1);
                } catch (IOException e){
                    // В случае ошибки продолжим выполнять предыдущий код
                    setPage(curPage);
                    pause(false, true);
                    throw e;
                }
                // Запоминаем имя MON-файла
                fCurMonName = fileName.toLowerCase();
                // Сбрасываем CPU с заданного адреса
                reset(address, true);
                return true;
            } catch (NumberFormatException | IOException e) {
                JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка загрузки MON-файла: \"%s\"\n%s", fileName, e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        return false;
    }

    /**
     * Загружает и запускает CPU(I80)-файл.
     * @param file CPU-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileCPU(File file) {
        try {
            int load_adr  = 0;
            int start_adr = 0;
            int selected  = 0;
            // Читаем CPU-файл
            try (BufferedReader cpuFile = new BufferedReader(new FileReader(file))) {
                int index = 0;
                for (String line; (index <= 2) && ((line = cpuFile.readLine()) != null); index++) {
                    line = line.trim().toLowerCase();
                    switch (index) {
                        case 0: // Читаем начальный адрес
                            load_adr  = Integer.parseInt(line, 16);
                            break;
                        case 1: // Читаем стартовый адрес
                            start_adr = Integer.parseInt(line, 16);
                            // Выводим диалог загрузки
                            Object[] options = {"Загрузить и запустить", "Только загрузить"};
                            selected = JOptionPane.showOptionDialog(fMainFrame,
                                    String.format("Файл: \"%s\"\n" +
                                                  "Адрес  начала: [%04X]\n" +
                                                  "Адрес запуска: [%04X]\n" , file.getName(), load_adr, start_adr),
                                    "Что делать?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                            // Если диалог закрыт крестом - отменяем загрузку
                            if (selected == JOptionPane.CLOSED_OPTION) {
                                return false;
                            }
                            break;
                        case 2: // Проверяем соответствие монитора
                            if ((selected == JOptionPane.YES_OPTION) && (line.length() > 0) && !fCurMonName.equals(line)) {
                                boolean result;
                                if (ConsStat.SPMX_ROM_FILE.toLowerCase().endsWith(line)) {
                                    // Запускаем стандартный BIOS
                                    result = restart(false, false);
                                } else {
                                    // Ищем, загружаем и запускаем необходимый монитор
                                    cFileFinder fileFinder = new cFileFinder();
                                    List<File> listFiles = fileFinder.findFiles(file.getParent(), line);
                                    if (!listFiles.isEmpty()) {
                                        result = loadFileMON(listFiles.get(0));
                                    } else {
                                        listFiles = fileFinder.findFiles(ConsStat.PATH_MON_FILES, line);
                                        if (!listFiles.isEmpty()) {
                                            result = loadFileMON(listFiles.get(0));
                                        } else {
                                            throw new IOException(String.format("Необходимый для запуска файла: \"%s\" MON-файл: \"%s\" не найден!", file.getName(), line));
                                        }
                                    }
                                }
                                if (result) {
                                    // Увеличиваем тактовую частоту в 4 раза
                                    fGen.setClockSpeed(fGen.getClockSpeed() << 2);
                                    // Приостановим GUI на 0.5 секунды для инициализации BIOSа/монитора
                                    try {
                                        Thread.sleep(500L);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    // Устанавливаем тктовую частоту по умолчанию
                                    fGen.setClockSpeed(cClockGenerator.CLOCK_SPEED);
                                } else {
                                    // Были ошибки при загрузке BIOSа/монитора
                                    return false;
                                }
                            }
                            break;
                    }
                }
                // Проверяем корректность CPU-файла
                if (index < 2) {
                    throw new IOException("В CPU-файле отсутствует информация об адресе загрузки или старта!");
                }
            }
            // Приостанавливаем компьютер
            pause(true, true);
            // Запоминаем текущую страницу памяти
            int curPage = getPage();
            // Устанавливам основную страницу памяти
            setPage(0);
            try {
                // Определяем I80-файл
                file = new File(file.getPath().substring(0, file.getPath().length() - 3).concat("i80"));
                // Загружаем I80-файл в основную станицу памяти
                loadFile(file, load_adr, 0, 0, -1);
            } catch (IOException e){
                // В случае ошибки продолжим выполнять предыдущий код
                setPage(curPage);
                pause(false, true);
                throw e;
            }
            if (selected == JOptionPane.YES_OPTION) {
                // Если выбрана загрузка с запуском, то устанавливаем цвет по умолчанию
                fScr.setColor(cMD_SpMX_Screen.DEFAULT_COLOR);
                // Устанавливаем режим порта клавиатуры по умолчанию
                fKey.setDefaultMode();
                // Сбрасываем Speaker
                if (fSpc != null) {
                    fSpc.reset();
                }
                // Запускаем CPU с заданного адреса
                run(start_adr);
            } else {
                // Если выбрана только загрузка, то продолжим выполнять предыдущий код
                setPage(curPage);
                pause(false, true);
            }
            return true;
        } catch (NumberFormatException | IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка загрузки файла: \"%s\"\n%s", file.getName(), e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Сохраняет CPU-файл.
     * @param file CPU-файл
     * @param beginAddress начальный адрес
     * @param endAddress конечный адрес
     * @param startAddress стартовый адрес
     * @return false = сохранение не удалось
     */
    boolean saveFileCPU(File file, int beginAddress, int endAddress, int startAddress) {
        // Проверяем корректность переданных параметров
        if (    (file   ==   null) ||
                (beginAddress < 0) || (beginAddress > 0xFFFF) ||
                (endAddress   < 0) || (endAddress   > 0xFFFF) ||
                (startAddress < 0) || (startAddress > 0xFFFF)   ) {
            JOptionPane.showMessageDialog(fMainFrame, "Некоторые параметры переданы неверно - сохранение невозможно!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        try {
            // Сохранеяем текстовый CPU-файл
            try (BufferedWriter cpuFile = new BufferedWriter(new FileWriter(file))) {
                cpuFile.write(Integer.toHexString(beginAddress));
                cpuFile.newLine();
                cpuFile.write(Integer.toHexString(startAddress));
                cpuFile.newLine();
                cpuFile.write(fCurMonName);
                cpuFile.newLine();
            }
            // Определяем I80-файл
            file = new File(file.getPath().substring(0, file.getPath().length() - 3).concat("i80"));
            // Вычисляем размер данных и формируем буфер
            int length = endAddress - beginAddress + 1;
            byte[] buf = new byte[length];
            // Перемещаем данные из памяти в буфер через менеджер устройств памяти
            pause(true , true);
            for (int i = 0; i < length; i++) {
                buf[i] = (byte) debugReadByte(beginAddress++); // Вызываем синхронизированный метод
            }
            pause(false, true);
            // Сохраняем I80-файл
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(buf);
            }
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка сохранения файла: \"%s\"\n%s", file.getName(), e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Загружает и запускает RKS-файл.
     * @param file RKS-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileRKS(File file) {
        String fileName = "\"".concat(file.getName()).concat("\"");
        try {
            int beg_adr, end_adr, length, checksum;
            // Выделяем буфер под служебные поля RKS-файла
            byte[] buf = new byte[6];
            // Открываем файл на чтение
            try (FileInputStream fis = new FileInputStream(file)) {
                // Читаем заголовок RKS-файла в буфер
                if (fis.read(buf, 0, 4) < 4) {
                    throw new IOException("Не удалось прочитать заголовок RKS-файла: ".concat(fileName));
                }
                // Получаем адреса начала и конца из заголовка файла, вычисляем длину данных
                beg_adr = (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8);
                end_adr = (buf[2] & 0xFF) | ((buf[3] & 0xFF) << 8);
                if (beg_adr > end_adr) {
                    throw new IOException(String.format("Адрес начала: [%04X] > адреса конца: [%04X] в заголовке файла: %s", beg_adr, end_adr, fileName));
                }
                length  = end_adr - beg_adr + 1;
                // Пропускаем блок с данными
                if (fis.skip(length) != length) {
                    throw new IOException(String.format("Не удалось выполнить смещение на %d байт(а/ов) для чтения контрольной суммы в файле: %s", length, fileName));
                }
                // Читаем контрольную сумму из RKS-файла в буфер
                if (fis.read(buf, 4, 2) < 2) {
                    throw new IOException("Не удалось прочитать контрольную сумму из RKS-файла: ".concat(fileName));
                }
                // Получаем контрольную сумму из хвоста файла
                checksum = (buf[4] & 0xFF) | ((buf[5] & 0xFF) << 8);
            }
            // Выводим диалог загрузки
            Object[] options = {"Загрузить и запустить", "Только загрузить"};
            int selected = JOptionPane.showOptionDialog(fMainFrame,
                    String.format("Файл: %s\n" +
                                  "Адреса загрузки: [%04X..%04X]\n", fileName, beg_adr, end_adr),
                    "Что делать?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            // Если диалог закрыт крестом - отменяем загрузку
            if (selected == JOptionPane.CLOSED_OPTION) {
                return false;
            }
            // Приостанавливаем компьютер
            pause(true, true);
            // Запоминаем текущую страницу памяти
            int curPage = getPage();
            // Устанавливам основную страницу памяти
            setPage(0);
            try {
                // Загружаем RKS-файл в основную страницу памяти
                loadFile(file, beg_adr, 4, length, checksum);
            } catch (IOException e){
                // В случае ошибки продолжим выполнять предыдущий код
                setPage(curPage);
                pause(false, true);
                throw e;
            }
            if (selected == JOptionPane.YES_OPTION) {
                // Если выбрана загрузка с запуском, то устанавливаем цвет по умолчанию
                fScr.setColor(cMD_SpMX_Screen.DEFAULT_COLOR);
                // Устанавливаем режим порта клавиатуры по умолчанию
                fKey.setDefaultMode();
                // Сбрасываем Speaker
                if (fSpc != null) {
                    fSpc.reset();
                }
                // Запускаем CPU с заданного адреса
                run(beg_adr);
            } else {
                // Если выбрана только загрузка, то продолжим выполнять предыдущий код
                setPage(curPage);
                pause(false, true);
            }
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка загрузки файла: %s\n%s", fileName, e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Сохраняет RKS-файл.
     * @param file RKS-файл
     * @param beginAddress начальный адрес
     * @param endAddress конечный адрес
     * @return false = сохранение не удалось
     */
    boolean saveFileRKS(File file, int beginAddress, int endAddress) {
        // Проверяем корректность переданных параметров
        if (    (file   ==   null) ||
                (beginAddress < 0) || (beginAddress > 0xFFFF) ||
                (endAddress   < 0) || (endAddress   > 0xFFFF)   ) {
            JOptionPane.showMessageDialog(fMainFrame, "Некоторые параметры переданы неверно - сохранение невозможно!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        try {
            // Вычисляем размер данных
            int length = endAddress - beginAddress + 1;
            // Формируем буфер размером на 6 байт больше размера данных (дополнительные байты выделены под данные RKS-файла)
            byte[] buf = new byte[length + 6];
            // Записываем стартовый и конечный адреса данных
            buf[0] = (byte) ( beginAddress       & 0xFF);
            buf[1] = (byte) ((beginAddress >> 8) & 0xFF);
            buf[2] = (byte) ( endAddress         & 0xFF);
            buf[3] = (byte) ((endAddress   >> 8) & 0xFF);
            // Перемещаем данные из памяти в буфер через менеджер устройств памяти
            pause(true , true);
            for (int i = 4, j = length + i; i < j; i++) {
                buf[i] = (byte) debugReadByte(beginAddress++); // Вызываем синхронизированный метод
            }
            pause(false, true);
            // Рассчитаем контрольную сумму данных
            int checkSum = getChecksum(buf, 4, length);
            buf[length + 4] = (byte) ( checkSum       & 0xFF);
            buf[length + 5] = (byte) ((checkSum >> 8) & 0xFF);
            // Сохраняем RKS-файл
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(buf);
            }
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(fMainFrame, String.format("Ошибка сохранения файла: \"%s\"\n%s", file.getName(), e.toString()), "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Запускает отладчик.
     */
    void startDebugger() {
        if (!fDebugRun) {
            // Блокируем возможность одновременного запуска нескольких копий отладчика
             fDebugRun = true;

            try {
                // Выполняем мгновенный останов всех устройств с остановкой тактового генератора
                pause(true, true);
                try {
                    // Отменяем режим "Пауза" только для CPU
                    fCPU.hold(false);
                    // Выводим окно отладчика
                    DebuggerI8080 debug = new DebuggerI8080(this);
                    // После окончания работы - убиваем отладчик
                    debug.getContentPane().removeAll();
                    debug.dispose();
                } finally {
                    // Запускаем тактовый генератор и устройства памяти
                    pause(false, true);

                }
            } finally {
                fDebugRun = false;
            }
        }
    }

    /**
     * Возвращает название эмулятора и номер его версии.
     * @return название эмулятора и номер его версии
     */
    String getProductName() {
        return fProductName;
    }

    /**
     * Возвращает имя текущего MON-файла.
     * @return имя текущего MON-файла
     */
    String getCurMonName() {
        return fCurMonName;
    }

    /**
     * Возвращает текущий ROM-файл.
     * @return текущий ROM-файл (если = null, то используется встроенный ROM-файл).
     */
    File getCurRomFile() {
        return fCurRomFile;
    }
}