package ru.avsh.specialist.mx.gui.swing.debugger.helpers;

import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.utils.Constants;

import java.awt.*;
import java.util.Arrays;

public final class PrevStaticData {

    private PrevStaticData() {
        //
    }

    // Статические структуры для сохранения положения окна отладчика и значений регистровых пар
    private static final Point PREV_LOCATION        = new Point();
    private static final int[] PREV_REG_PAIRS       = new int[DebugRegPair.values().length];
    // Размер массива под выделенные диапазоны адресов: по паре (начало/конец) на каждую страницу памяти
    private static final int   DATA_SELECTIONS_SIZE = 2 * (Constants.NUMBER_PAGES_RAMDISK + 2);
    // Статическая структура для сохранения выделенных диапазонов адресов по страницам памяти:
    // пара (2 * страница, 2 * страница + 1) = (адрес начала, адрес конца), оба 0 = выделения нет
    private static final int[] PREV_DATA_SELECTIONS = new int[DATA_SELECTIONS_SIZE];
    static {
        Arrays.fill(PREV_DATA_SELECTIONS, 0);
    }
    // Статическая переменная для сохранения страницы таблицы данных памяти
    private static int    fPrevDataPage;
    // Статическая переменная для сохранения строки поиска (строка байт)
    private static String fPrevStringBytes = "";

    /**
     * Возвращает предыдущее положение окна отладчика.
     *
     * @return предыдущее положение окна
     */
    public static synchronized Point getPrevLocation() {
        return PREV_LOCATION.getLocation();
    }

    /**
     * Сохраняет положение окна отладчика.
     *
     * @param prevLocation положение окна
     */
    public static synchronized void setPrevLocation(final Point prevLocation) {
        if (prevLocation != null) {
            PREV_LOCATION.setLocation(prevLocation);
        }
    }

    /**
     * Возвращает предыдущее значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @return значение
     */
    public static synchronized int getPrevRegPairs(final DebugRegPair regPair) {
        return (regPair == null) ? 0 : PREV_REG_PAIRS[regPair.ordinal()];
    }

    /**
     * Сохраняет значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @param value   значение
     */
    public static synchronized void setPrevRegPairs(final DebugRegPair regPair, final int value) {
        if (regPair != null) {
            PREV_REG_PAIRS[regPair.ordinal()] = value;
        }
    }

    /**
     * Возвращает предыдущий номер страницы памяти для просмотра данных.
     *
     * @return предыдущий номер страницы памяти
     */
    public static synchronized int getPrevDataPage() {
        return fPrevDataPage;
    }

    /**
     * Сохраняет номер страницы памяти для просмотра данных.
     *
     * @param prevDataPage номер страницы памяти
     */
    public static synchronized void setPrevDataPage(final int prevDataPage) {
        fPrevDataPage = prevDataPage;
    }

    /**
     * Возвращает предыдущие выделенные диапазоны адресов по страницам памяти.
     * Пара (2 * страница, 2 * страница + 1) содержит адрес начало и конца диапазона,
     * если оба 0 = диапазон не выделен.
     *
     * @return копия массива выделенных диапазонов адресов
     */
    public static synchronized int[] getPrevDataSelections() {
        return PREV_DATA_SELECTIONS.clone();
    }

    /**
     * Сохраняет выделенные диапазоны адресов по страницам памяти.
     *
     * @param selections массив выделенных диапазонов адресов (пары "начало/конец" по номерам страниц)
     */
    public static synchronized void setPrevDataSelections(final int[] selections) {
        for (int i = 0; (selections != null) && (i < selections.length) && (i < DATA_SELECTIONS_SIZE); i++) {
            PREV_DATA_SELECTIONS[i] = selections[i];
        }
    }

    /**
     * Возвращает предыдущую строку поиска.
     *
     * @return предыдущая строка поиск
     */
    public static synchronized String getPrevStringBytes() {
        return fPrevStringBytes;
    }

    /**
     * Сохраняет строку поиска.
     *
     * @param prevStringBytes строка поиска
     */
    public static synchronized void setPrevStringBytes(final String prevStringBytes) {
        fPrevStringBytes = prevStringBytes;
    }
}
