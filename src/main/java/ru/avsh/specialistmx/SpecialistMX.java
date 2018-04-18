package ru.avsh.specialistmx;

import org.ini4j.Wini;
import ru.avsh.lib.FileFinder;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.io.*;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static javax.swing.JOptionPane.*;
import static ru.avsh.specialistmx.ConsStat.*;

/**
 * Класс "Компьютер 'Специалист MX'".
 * @author -=AVSh=-
 */
final class SpecialistMX {
    private final String fProductName;

    private final MemDevScreen fScr;
    private final ProcessorI8080 fCPU;
    private final ClockGenerator fGen;
    private final MemDevMainMemory fRAM;
    private final MemDevKeyboardPort fKey;
    private final MemoryDevicesManager fMemDevMng;
    private final MemDevFloppyDiskController fFDC;
    /* Пока не используем!
    private final MemoryDevicesManager fInOutDevMng; */

    private Wini    fIni;
    private Speaker fSpc;
    private boolean fDebugRun;
    private JFrame  fMainFrame;
    private String  fCurMonName;
    private File    fCurRomFile;

    /**
     * Конструктор.
     */
    SpecialistMX() {
        // Создаем объект для работы с ini-файлом настроек
        fIni = new Wini();
        fIni.setFile(new File(INI_FILE));
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
        fGen = new ClockGenerator();
        // Создаем диспетчер устройств памяти
        fMemDevMng = new MemoryDevicesManager();
        // Создаем CPU
        fCPU = new ProcessorI8080(this, fMemDevMng, null); // fInOutDevMng - пока не используем!
        // Создаем Speaker
        try {
            fSpc = new Speaker(fGen);
        } catch (LineUnavailableException e) {
            fSpc = null;
        }
        // Создаем устройства памяти
        fScr = new MemDevScreen();
        fRAM = new MemDevMainMemory(NUMBER_PAGES_RAMDISK + 1, fScr); // RAM + RAM-диск (8 страниц) + ROM-диск
        fKey = new MemDevKeyboardPort(fSpc);
        fFDC = new MemDevFloppyDiskController(fGen, fCPU);

        final MemDevTimer                    timer   = new MemDevTimer                   (fSpc );
        final MemDevSimpleMemory             excRAM  = new MemDevSimpleMemory            (0x20 );
        final MemDevProgrammerPort           prgPort = new MemDevProgrammerPort          (timer);
        final MemDevMainMemoryPort           ramPort = new MemDevMainMemoryPort          (fRAM );
        final MemDevScreenColorPort          colPort = new MemDevScreenColorPort         (fScr );
        final MemDevFloppyDiskControllerPort fdcPort = new MemDevFloppyDiskControllerPort(fFDC );

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
     * Запоминает ссылку на главный фрейм приложения.
     *
     * @param frame ссылка на главный фрейм
     */
    void setMainFrame(final JFrame frame) {
        fMainFrame = frame;
    }

    /**
     * Возвращает ссылку на главный фрейм приложения.
     *
     * @return ссылка на главный фрейм
     */
    JFrame getMainFrame() {
        return fMainFrame;
    }

    /**
     * Возвращает ссылку на тактовый генератор.
     *
     * @return ссылка на тактовый генератор
     */
    ClockGenerator getGen() {
        return fGen;
    }

    /**
     * Возвращает ссылку на диспетчер устройств памяти.
     *
     * @return ссылка на диспетчер устройств памяти
     */
    MemoryDevicesManager getMemDevMng() {
        return fMemDevMng;
    }

    /**
     * Возвращает ссылку на CPU.
     *
     * @return ссылка на CPU
     */
    ProcessorI8080 getCPU() {
        return fCPU;
    }

    /**
     * Возвращает ссылку на память.
     *
     * @return ссылка на память
     */
    MemDevMainMemory getRAM() {
        return fRAM;
    }

    /**
     * Возвращает ссылку на экран.
     *
     * @return ссылка на экран
     */
    MemDevScreen getScreen() {
        return fScr;
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
    // Делегированные методы
    /**
     * Показывает приостановлен тактовый генератор или нет.
     *
     * @return - true = CPU приостановлен
     */
    boolean isPaused() {
        return fGen.isPaused();
    }

    /**
     * Переводит тактовый генератор в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     * @param dev  true = устанавливать/снимать режим "Пауза" и для устройств памяти
     */
    void pause(final boolean mode, final boolean dev) {
        fGen.pause(mode, dev);
    }

    /**
     * Возвращает номер текущей страницы памяти.
     *
     * @return номер страницы
     */
    int getPage() {
        return fRAM.getPage();
    }

    /**
     * Устанавливает заданную страницу памяти.
     *
     * @param pageNumber от 0-8 - страницы RAM, 9 или больше - страница ROM
     */
    void setPage(final int pageNumber) {
        fRAM.setPage(pageNumber);
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     *
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    int readByte(final int address) {
        return fMemDevMng.readByte(address);
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * (Метод для вызова из отладчика - минимизирует влияние отладчика на работу устройств памяти)
     *
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    int debugReadByte(final int address) {
        return fMemDevMng.debugReadByte(address);
    }

    /**
     * Записывает байт по заданному адресу в устройство/устройства памяти.
     *
     * @param address заданный адрес
     * @param value   записываемый байт (байт представлен как int)
     */
    void writeByte(final int address, final int value) {
        fMemDevMng.writeByte(address, value);
    }

    /**
     * Вставляет диск в заданный дисковод.
     *
     * @param fdd  false = "A" / true = "B"
     * @param file файл с образом диска
     * @throws IOException исключение, возникающее при вставке диска
     */
    void insertDisk(final boolean fdd, final File file) throws IOException {
        fFDC.insertDisk(fdd, file);
    }

    /**
     * Извлекает диск из заданного дисковода.
     *
     * @param fdd false = "A" / true = "B"
     */
    void ejectDisk(final boolean fdd) {
        fFDC.ejectDisk(fdd);
    }

    /**
     * Возвращает режим работы клавиатуры.
     *
     * @return false = "Специалист MX" / true = стандартный "Специалист"
     */
    boolean isKeyboardMode() {
        return fKey.isKeyboardMode();
    }

    /**
     * Устанавливает режим работы клавиатуры.
     *
     * @param keyboardMode false = "Специалист MX" / true = стандартный "Специалист"
     */
    void setKeyboardMode(final boolean keyboardMode) {
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
     *
     * @param flagKeyPressed true = клавиша нажата, false = клавиша отпущена
     * @param keyCode        код клавиши
     */
    void keyCodeReceiver(final boolean flagKeyPressed, final int keyCode) {
        fKey.keyCodeReceiver(flagKeyPressed, keyCode);
    }
    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

    /**
     * Читает значение из ini-файла.
     *
     * @param sectionName имя секции
     * @param optionName  имя опции
     * @param clazz       класс
     * @param <T>         тип
     * @return полученное значение
     */
    <T> T getIni(final Object sectionName, final Object optionName, final Class<T> clazz) {
        if (fIni != null) {
            return fIni.get(sectionName, optionName, clazz);
        }
        return null;
    }

    /**
     * Записывает значение в ini-файл.
     *
     * @param sectionName имя секции
     * @param optionName  имя опции
     * @param value       значение
     */
    void putIni(final String sectionName, final String optionName, final Object value) {
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
            showMessageDialog(fMainFrame, e.toString(), STR_ERROR, ERROR_MESSAGE);
        }
    }

    /**
     * Возвращает наиболее короткий путь к файлу (полный или относительный).
     *
     * @param file файл
     * @return путь
     */
    String getShortPath(final File file) {
        final String p0 = file.getPath();
        final String p1 = APP_PATH.relativize(file.toPath()).toString();
        return (p0.length() < p1.length()) ? p0 : p1;
    }

    /**
     * Читает название эмулятора и номер его версии из properties-файла.
     *
     * @return название эмулятора и номер его версии
     */
    private String readProductName() {
        String appName = SPMX_NAME;
        String version = "x.x.x.x";

        final InputStream is = getResourceAsStream(SPMX_PROP_FILE);
        if (is != null) {
            try (InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {
                final Properties property = new Properties();
                property.load(isr);

                appName = property.getProperty("appName"      , appName);
                version = property.getProperty("versionNumber", version);
            } catch (IOException e) {
                //
            }
        }
        return String.format(" - \"%s\" v%s", appName, version);
    }

    /**
     * Выполняет запуск с заданного адреса.
     *
     * @param address адрес запуска
     */
    private void run(final int address) {
        pause(true, true);
        fCPU.run(address);
        // Проверям ловушки (стартовые ловушки не отслеживаются в классе ProcessorI8080)
        if (fCPU.debugIsTrap(getPage(), address)) {
            startDebugger();
        } else {
            pause(false, true);
        }
    }

    /**
     * Выполняет сброс компьютера.
     *
     * @param address            адрес запуска
     * @param resetMemoryDevices true = выполняет сброс устройств памяти
     */
    private void reset(final int address, final boolean resetMemoryDevices) {
        pause(true, true);
        fCPU.reset(address, resetMemoryDevices);
        // Проверям ловушки (стартовые ловушки не отслеживаются в классе ProcessorI8080)
        if (fCPU.debugIsTrap(getPage(), address)) {
            startDebugger();
        } else {
            pause(false, true);
        }
    }

    /**
     * Вычисляет контрольную сумму по алгоритму RAMFOS (подпрограмма 0xC82A).
     *
     * @param buf    буфер
     * @param start  начало блока данных в буфере
     * @param length длина блока данных
     * @return контрольная сумма
     */
    private int getChecksum(final byte[] buf, int start, int length) {
        length += start - 1; // вычисляем конец блока в буфере
        if (length < buf.length) {
            int cur;
            int lo = 0;
            int hi = 0;
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
     *
     * @throws IOException исключение
     */
    private void loadROM() throws IOException {
        // Если ROM-файл прописан в ini-файле и он имеется на диске, то загружаем внешний ROM-файл
        final String romPath = getIni(INI_SECTION_CONFIG, INI_OPTION_ROM_FILE, String.class);
        if ((romPath != null) && (romPath.length() > 0)) {
            final File romFile = new File(romPath);
            if (romFile.exists() && romFile.isFile()) {
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
        final InputStream is = getResourceAsStream(SPMX_ROM_FILE);
        if (is == null) {
            throw new IOException("ROM-файл эмулятора не найден в ресурсах программы!");
        }
        try (BufferedInputStream bis = new BufferedInputStream(is)) {
            final int length = bis.available();
            if (length < 0x10000) {
                final byte[] buf = new byte[length];
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
     *
     * @param file     файл
     * @param address  адрес
     * @param offset   смещение в файле (игнорируется если <= 0)
     * @param length   необходимая длина (игнорируется если <= 0)
     * @param checksum контрольная сумма для проверки (проверка игнорируется, если контрольная сумма < 0)
     * @throws IOException исключение
     */
    private void loadFile(final File file, int address, int offset, int length, int checksum) throws IOException {
        if ((address >= 0) && (address <= 0xFFFF)) {
            try (FileInputStream fis = new FileInputStream(file)) {
                final String fileName = "\"".concat(file.getName()).concat("\"");
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
                    final byte[] buf = new byte[length];
                    if (fis.read(buf, 0, length) < length) {
                        throw new IOException("Не удалось полностью прочитать файл: ".concat(fileName));
                    }
                    // Проверим контрольную сумму данных
                    if (checksum >= 0) {
                        final int curChecksum = getChecksum(buf, 0, length);
                        if ((curChecksum != checksum) &&
                                (showConfirmDialog(fMainFrame,
                                        String.format("В файле: %s%n" +
                                                      "Рассчитанная контрольная сумма  данных: [%04X]%n"   +
                                                      "не равна проверочной контрольной сумме: [%04X]%n%n" +
                                                      "Загружать файл?", fileName, curChecksum, checksum),
                                        "Загружать?", YES_NO_OPTION) != YES_OPTION)) {
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

    private void loadFile(final File file,
                          final int address,
                          final int offset,
                          final int length,
                          final int checksum,
                          final Consumer<IOException> exceptionAction) throws IOException {
        try {
            loadFile(file, address, offset, length, checksum);
        } catch (IOException e) {
            exceptionAction.accept(e);
            throw e;
        }
    }

    /**
     * Перезапускает компьютер "Специалист MX".
     *
     * @param clearDialog true = выводится диалог очистки памяти
     * @param clear       true = выполняется очистка памяти (перекрывается параметром clearDialog)
     * @return false = перезапуск не удался
     */
    boolean restart(boolean clearDialog, boolean clear) {
        try {
            if (clearDialog) {
                final Object[] options = {"Да", "Нет"};
                int selected = showOptionDialog(fMainFrame, "Очистить память?", "Очистить?", YES_NO_OPTION, QUESTION_MESSAGE, null, options, options[1]);
                // Если диалог закрыт крестом - отменяем перезапуск
                if (selected == CLOSED_OPTION) {
                    return true;
                } else {
                    clear = selected == YES_OPTION;
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
            setPage(MemDevMainMemory.ROM_DISK);
            // Загружаем BIOS "Специалиста_MX"
            loadROM();
            // Запоминаем имя ROM-файла
            fCurMonName = SPMX_ROM_FILE.toLowerCase();
            // Сбрасываем CPU с адреса 0x0000 (сброс устройств памяти устанавливает страницу памяти 0, что здесь не подходит)
            reset(0x0000, false);
            return true;
        } catch (IOException e) {
            showMessageDialog(fMainFrame, e.toString(), STR_ERROR, ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Загружает и запускает ROM-файл.
     *
     * @param file ROM-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileROM(File file) {
        // Получаем имя ROM-файла
        final String fileName = file.getName();
        try {
            // Приостанавливаем компьютер
            pause(true, true);
            // Сбрасываем устройства памяти (без полной очистки)
            fMemDevMng.resetMemoryDevices(false);
            // Включаем ROM-диск
            setPage(MemDevMainMemory.ROM_DISK);
            // Загружаем ROM-файл в страницу ROM-диска (в случае ошибки загрузки выполняем сброс)
            loadFile(file, 0x0000, 0, 0, -1, e -> restart(false, false));
            // Запоминаем ROM-файл
            fCurRomFile = file;
            // Сбрасываем CPU с адреса 0x0000 (сброс устройств памяти устанавливает страницу памяти 0, что здесь не подходит)
            reset(0x0000, false);
            return true;
        } catch (NumberFormatException | IOException e) {
            showMessageDialog(fMainFrame, String.format("Ошибка загрузки ROM-файла: \"%s\"%n%s", fileName, e.toString()), STR_ERROR, ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Загружает и запускает MON-файл.
     * Формат MON-файла: "nm_addr.MON" - nm = имя, addr = адрес.
     *
     * @param file MON-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileMON(final File file) {
        // Получаем имя MON-файла
        final String fileName = file.getName();
        // Проверяем длину имени MON-файла
        if (fileName.length() == 11) {
            try {
                // Получаем адрес из имени MON-файла
                final int address = Integer.parseInt(fileName.substring(3, 7), 16);
                // Приостанавливаем компьютер
                pause(true, true);
                // Запоминаем текущую страницу памяти
                final int curPage = getPage();
                // Включаем основную страницу памяти
                setPage(0);
                // Загружаем MON-файл в основную страницу памяти
                loadFile(file, address, 0, 0, -1, e -> {
                    // В случае ошибки продолжим выполнять предыдущий код
                    setPage(curPage);
                    pause(false, true);
                });
                // Запоминаем имя MON-файла
                fCurMonName = fileName.toLowerCase();
                // Сбрасываем CPU с заданного адреса
                reset(address, true);
                return true;
            } catch (NumberFormatException | IOException e) {
                showMessageDialog(fMainFrame, String.format("Ошибка загрузки MON-файла: \"%s\"%n%s", fileName, e.toString()), STR_ERROR, ERROR_MESSAGE);
            }
        }
        return false;
    }

    /**
     * Загружает и запускает (если runFlag = true) файл.
     *
     * @param file     файл
     * @param loadAdr  адрес загрузки
     * @param startAdr адрес запуска
     * @param offset   смещение в файле (игнорируется если <= 0)
     * @param length   необходимая длина (игнорируется если <= 0)
     * @param checksum контрольная сумма для проверки (проверка игнорируется, если контрольная сумма < 0)
     * @param runFlag  true = выполнить запуск после загрузки
     * @throws IOException исключение
     */
    private void loadHelper(File file, int loadAdr, int startAdr, int offset, int length, int checksum, boolean runFlag) throws IOException {
        // Приостанавливаем компьютер
        pause(true, true);
        // Запоминаем текущую страницу памяти
        final int curPage = getPage();
        // Устанавливам основную страницу памяти
        setPage(0);
        // Загружаем файл в основную страницу памяти
        loadFile(file, loadAdr, offset, length, checksum, e -> {
            // В случае ошибки продолжим выполнять предыдущий код
            setPage(curPage);
            pause(false, true);
        });
        if (runFlag) {
            // Если выбрана загрузка с запуском, то устанавливаем цвет по умолчанию
            fScr.setColor(MemDevScreen.DEFAULT_COLOR);
            // Устанавливаем режим порта клавиатуры по умолчанию
            fKey.setDefaultMode();
            // Сбрасываем Speaker
            if (fSpc != null) {
                fSpc.reset();
            }
            // Запускаем CPU с заданного адреса
            run(startAdr);
        } else {
            // Если выбрана только загрузка, то продолжим выполнять предыдущий код
            setPage(curPage);
            pause(false, true);
        }
    }

    /**
     * Загружает и запускает CPU(I80)-файл.
     * @param file CPU-файл
     * @return false = загрузка не удалась
     */
    boolean loadFileCPU(File file) {
        try {
            int  loadAdr = 0;
            int startAdr = 0;
            int selected = 0;
            // Читаем CPU-файл
            try (BufferedReader cpuFile = new BufferedReader(new FileReader(file))) {
                int index = 0;
                for (String line; (index <= 2) && ((line = cpuFile.readLine()) != null); index++) {
                    line  = line.trim().toLowerCase();
                    switch (index) {
                        case 0: // Читаем начальный адрес
                             loadAdr = Integer.parseInt(line, 16);
                            break;
                        case 1: // Читаем стартовый адрес
                            startAdr = Integer.parseInt(line, 16);
                            // Выводим диалог загрузки
                            Object[] options = {"Загрузить и запустить", "Только загрузить"};
                            selected = showOptionDialog(fMainFrame,
                                    String.format("Файл: \"%s\"%n" +
                                                  "Адрес  начала: [%04X]%n" +
                                                  "Адрес запуска: [%04X]%n" , file.getName(), loadAdr, startAdr),
                                    "Что делать?", YES_NO_OPTION, QUESTION_MESSAGE, null, options, options[0]);
                            // Если диалог закрыт крестом - отменяем загрузку
                            if (selected == CLOSED_OPTION) {
                                return false;
                            }
                            break;
                        case 2: // Проверяем соответствие монитора
                            if ((selected == YES_OPTION) && (line.length() > 0) && !fCurMonName.equals(line)) {
                                boolean result;
                                if (SPMX_ROM_FILE.toLowerCase().endsWith(line)) {
                                    // Запускаем стандартный BIOS
                                    result = restart(false, false);
                                } else {
                                    // Ищем, загружаем и запускаем необходимый монитор
                                    FileFinder fileFinder = new FileFinder();
                                    List<File> listFiles = fileFinder.findFiles(file.getParent(), line);
                                    if (!listFiles.isEmpty()) {
                                        result = loadFileMON(listFiles.get(0));
                                    } else {
                                        listFiles = fileFinder.findFiles(PATH_MON_FILES, line);
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
                                    fGen.setClockSpeed(ClockGenerator.CLOCK_SPEED);
                                } else {
                                    // Были ошибки при загрузке BIOSа/монитора
                                    return false;
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
                // Проверяем корректность CPU-файла
                if (index < 2) {
                    throw new IOException("В CPU-файле отсутствует информация об адресе загрузки или старта!");
                }
            }
            // Выполняем загрузку I80-файла
            loadHelper(new File(file.getPath().substring(0, file.getPath().length() - 3).concat("i80")), loadAdr, startAdr, 0, 0, -1, selected == YES_OPTION);
            return true;
        } catch (NumberFormatException | IOException e) {
            showMessageDialog(fMainFrame, String.format("Ошибка загрузки файла: \"%s\"%n%s", file.getName(), e.toString()), STR_ERROR, ERROR_MESSAGE);
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
            int begAdr;
            int endAdr;
            int length;
            int checksum;
            // Выделяем буфер под служебные поля RKS-файла
            byte[] buf = new byte[6];
            // Открываем файл на чтение
            try (FileInputStream fis = new FileInputStream(file)) {
                // Читаем заголовок RKS-файла в буфер
                if (fis.read(buf, 0, 4) < 4) {
                    throw new IOException("Не удалось прочитать заголовок RKS-файла: ".concat(fileName));
                }
                // Получаем адреса начала и конца из заголовка файла, вычисляем длину данных
                begAdr = (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8);
                endAdr = (buf[2] & 0xFF) | ((buf[3] & 0xFF) << 8);
                if (begAdr > endAdr) {
                    throw new IOException(String.format("Адрес начала: [%04X] > адреса конца: [%04X] в заголовке файла: %s", begAdr, endAdr, fileName));
                }
                length = endAdr - begAdr + 1;
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
            int selected = showOptionDialog(fMainFrame,
                    String.format("Файл: %s%n" +
                                  "Адреса загрузки: [%04X..%04X]%n", fileName, begAdr, endAdr),
                    "Что делать?", YES_NO_OPTION, QUESTION_MESSAGE, null, options, options[0]);
            // Если диалог закрыт крестом - отменяем загрузку
            if (selected == CLOSED_OPTION) {
                return false;
            }
            // Выполняем загрузку RKS-файла
            loadHelper(file, begAdr, begAdr, 4, length, checksum, selected == YES_OPTION);
            return true;
        } catch (IOException e) {
            showMessageDialog(fMainFrame, String.format("Ошибка загрузки файла: %s%n%s", fileName, e.toString()), STR_ERROR, ERROR_MESSAGE);
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
            showMessageDialog(fMainFrame, "Некоторые параметры переданы неверно - сохранение невозможно!", STR_ERROR, ERROR_MESSAGE);
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
            showMessageDialog(fMainFrame, String.format("Ошибка сохранения файла: \"%s\"%n%s", file.getName(), e.toString()), STR_ERROR, ERROR_MESSAGE);
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
            showMessageDialog(fMainFrame, "Некоторые параметры переданы неверно - сохранение невозможно!", STR_ERROR, ERROR_MESSAGE);
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
            showMessageDialog(fMainFrame, String.format("Ошибка сохранения файла: \"%s\"%n%s", file.getName(), e.toString()), STR_ERROR, ERROR_MESSAGE);
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