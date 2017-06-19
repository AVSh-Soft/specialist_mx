package avsh.specialist_mx;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Статические переменные и константы для проекта.
 * @author -=AVSh=-
 */
final class cConsStat {
    // Каталог размещения эмулятора "Специалист MX"
    private static final String APP_DIR  = System.getProperty("user.dir");
    //    Путь размещения эмулятора "Специалист MX"
            static final Path   APP_PATH = Paths.get(APP_DIR);

    // Название радиолюбительского компьютера
    static final String SPMX_NAME = "Специалист MX";

    // Настроечный ini-файл эмулятора "Специалист MX"
    static final String INI_FILE  = APP_DIR.concat(File.separator).concat("Specialist_MX.ini");
    // Секция настроек в ini-файле
    static final String INI_SECTION_CONFIG  = "Config" ;
    // Опция в ini-файле для ROM-файла эмулятора "Специалист MX"
    static final String INI_OPTION_ROM_FILE = "RomFile";

    // Путь к каталогу с MON-файлами эмулятора "Специалист MX"
    static final String PATH_MON_FILES = APP_DIR.concat(File.separator).concat("mon");

    // Папка с ресурсами (отностительно классов)
    static final String RESOURCES      = "resources/";
    // Главный properties-файл эмулятора
    static final String SPMX_PROP_FILE = "Specialist_MX.properties";
    // Встроенный ROM-файл эмулятора "Специалист MX"
    static final String SPMX_ROM_FILE  = "spmx.rom";
    // Иконка эмулятора "Специалист MX"
    static final String SPMX_ICON_FILE = "Specialist_MX.png";

    // Количество страниц RAM-диска у ПК "Специалист MX" (от 1 до 8)
    // (здесь только количество страницы RAM-диска - без основной памяти и ROM-диска)
    static final int NUMBER_PAGES_RAMDISK = 8;

    // Текущий путь к файлам эмулятора "Специалист MX"
    private static String fCurPath = APP_DIR;

    private cConsStat() {
    }

    /**
     * Возвращает текущий путь к файлам эмулятора.
     * @return - текущий путь
     */
    static String getCurPath() {
        return fCurPath;
    }

    /**
     * Устанавливает текущий путь к файлам эмулятора.
     * @param fCurPath - текущий путь
     */
    static void setCurPath(String fCurPath) {
        cConsStat.fCurPath = fCurPath;
    }
}