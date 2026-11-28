package ru.avsh.specialist.mx.utils;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Статические переменные и константы для проекта.
 *
 * @author -=AVSh=-
 */
public final class Constants {
    // Каталог размещения эмулятора "Специалист MX"
    private static final String APP_DIR  = System.getProperty("user.dir");
    //    Путь размещения эмулятора "Специалист MX"
    public  static final Path   APP_PATH = Paths.get(APP_DIR);

    // Название радиолюбительского компьютера
    public static final String SPMX_NAME = "Специалист MX";

    // Настроечный ini-файл эмулятора "Специалист MX"
    public static final String INI_FILE  = APP_DIR.concat(File.separator).concat("specialist_mx.ini");
    // Секция настроек в ini-файле
    public static final String INI_SECTION_CONFIG  = "Config" ;
    // Опция в ini-файле для ROM-файла эмулятора "Специалист MX"
    public static final String INI_OPTION_ROM_FILE = "RomFile";
    // Опция в ini-файле для выбора процессора эмулятора "Специалист MX"
    public static final String INI_OPTION_CPU      = "Cpu";

    // Значения опции выбора процессора
    public static final String CPU_I8080 = "I8080";
    public static final String CPU_Z80   = "Z80"  ;

    // Путь к каталогу с MON-файлами эмулятора "Специалист MX"
    public static final String PATH_MON_FILES = APP_DIR.concat(File.separator).concat("mon");

    // Папка с ресурсами эмулятора
    private static final String RESOURCES_CSS  = "css/" ;
    private static final String RESOURCES_DATA = "data/";
    // Главный properties-файл эмулятора
    public static final String SPMX_PROP_FILE  = "specialist_mx.properties";
    // Встроенный ROM-файл эмулятора "Специалист MX"
    public static final String SPMX_ROM_FILE   = "spmx.rom";
    // Иконка эмулятора "Специалист MX"
    public static final String SPMX_ICON_FILE  = "specialist_mx.png";

    // Строковые константы
    public static final String TITLE_ERROR = "Ошибка"    ;
    public static final String TITLE_INFO  = "Информация";

    public static final String STR_OPEN_FILE  = "Открыть файл"  ;
    public static final String STR_SAVE_FILE  = "Сохранить файл";

    // Количество страниц RAM-диска у ПК "Специалист MX" (от 1 до 8)
    // (здесь только количество страницы RAM-диска - без основной памяти и ROM-диска)
    public static final int NUMBER_PAGES_RAMDISK = 8;

    // Текущий путь к файлам эмулятора "Специалист MX"
    private static String fCurPath = APP_DIR;

    /**
     * Конструктор.
     */
    private Constants() {
        //
    }

    /**
     * Возвращает текущий путь к файлам эмулятора.
     *
     * @return - текущий путь
     */
    public static String getCurPath() {
        return fCurPath;
    }

    /**
     * Устанавливает текущий путь к файлам эмулятора.
     *
     * @param fCurPath - текущий путь
     */
    public static void setCurPath(final String fCurPath) {
        Constants.fCurPath = fCurPath;
    }

    /**
     * Ищет scc-ресурс и возвращает URL-объект для чтения ресурса.
     *
     * @param name имя css-ресурса
     * @return URL-объект для чтения ресурса (завернут в Optional)
     */
    public static Optional<URL> getCssUrl(final String name) {
        return Optional.ofNullable(Constants.class.getClassLoader())
                .map(classLoader -> classLoader.getResource(RESOURCES_CSS.concat(name)));
    }

    /**
     * Ищет ресурс и возвращает URL-объект для чтения ресурса.
     *
     * @param name имя ресурса
     * @return URL-объект для чтения ресурса (завернут в Optional)
     */
    public static Optional<URL> getURL(final String name) {
        return Optional.ofNullable(Constants.class.getClassLoader())
                .map(classLoader -> classLoader.getResource(RESOURCES_DATA.concat(name)));
    }

    /**
     * Возвращает InputStream для чтения ресурса.
     *
     * @param name имя ресурса
     * @return InputStream для чтения ресурса (завернут в Optional)
     */
    public static Optional<InputStream> getResourceAsStream(final String name) {
        return Optional.ofNullable(Constants.class.getClassLoader())
                .map(classLoader -> classLoader.getResourceAsStream(RESOURCES_DATA.concat(name)));
    }
}