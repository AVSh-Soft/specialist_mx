package ru.avsh.specialist.mx.helpers;

import javafx.scene.image.Image;
import javafx.scene.text.Font;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    // Путь к каталогу с MON-файлами эмулятора "Специалист MX"
    public static final String PATH_MON_FILES = APP_DIR.concat(File.separator).concat("mon");

    // Папка с ресурсами эмулятора
    private static final String RESOURCES     = "data/";
    // Главный properties-файл эмулятора
    public static final String SPMX_PROP_FILE = "specialist_mx.properties";
    // Встроенный ROM-файл эмулятора "Специалист MX"
    public static final String SPMX_ROM_FILE  = "spmx.rom";
    // Иконка эмулятора "Специалист MX"
    public static final String SPMX_ICON_FILE = "specialist_mx.png";

    // Строковые константы
    public static final String STR_ERROR     = "Ошибка";
    public static final String STR_OPEN_FILE = "Открыть файл";

    // Количество страниц RAM-диска у ПК "Специалист MX" (от 1 до 8)
    // (здесь только количество страницы RAM-диска - без основной памяти и ROM-диска)
    public static final int NUMBER_PAGES_RAMDISK = 8;

    // Инстанс иконки эмулятора "Специалист MX"
    public static final Image ICON            = new Image(getResourceAsStream(SPMX_ICON_FILE));
    // Инстанс шрифта семейства "Monospaced"
    public static final Font  FONT_MONOSPACED = Font.font("Monospaced");

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
    public static void setCurPath(String fCurPath) {
        Constants.fCurPath = fCurPath;
    }

    /**
     * Ищет ресурс и возвращает URL-объект для чтения ресурса.
     *
     * @param name имя ресурса
     * @return URL-объект для чтения ресурса
     */
    public static URL getURL(final String name) {
        return Constants.class.getClassLoader().getResource(RESOURCES.concat(name));
    }

    /**
     * Возвращает InputStream для чтения ресурса.
     *
     * @param name имя ресурса
     * @return InputStream для чтения ресурса
     */
    public static InputStream getResourceAsStream(final String name) {
        return Constants.class.getClassLoader().getResourceAsStream(RESOURCES.concat(name));
    }
}