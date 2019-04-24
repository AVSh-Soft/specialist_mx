package ru.avsh.specialist.mx.gui.debugger.lib;

import ru.avsh.specialist.mx.units.CPUi8080;

import java.awt.*;

public final class PreviousStaticData {
    private PreviousStaticData() {
        //
    }

    // Статические структуры для сохранения положения окна отладчика и значений регистровых пар
    private static final Point PREV_LOCATION  = new Point();
    private static final int[] PREV_REG_PAIRS = new int[CPUi8080.DebugRegPair.values().length];
    // Статические переменные для сохранения страницы и адреса памяти таблицы данных памяти
    private static int fPrevDataPage;
    private static int fPrevDataAddress;
    // Статическая переменная для сохранения строки поиска (строка из байт)
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
    public static synchronized int getPrevRegPairs(final CPUi8080.DebugRegPair regPair) {
        return (regPair == null) ? 0 : PREV_REG_PAIRS[regPair.ordinal()];
    }

    /**
     * Сохраняет значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @param value   значение
     */
    public static synchronized void setPrevRegPairs(final CPUi8080.DebugRegPair regPair, final int value) {
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
     * Возвращает предыдущий адрес страницы памяти для просмотра данных.
     *
     * @return предыдущий адрес страницы памяти
     */
    public static synchronized int getPrevDataAddress() {
        return fPrevDataAddress;
    }

    /**
     * Сохраняет текущий адрес страницы памяти для просмотра данных.
     *
     * @param prevDataAddress адрес
     */
    public static synchronized void setPrevDataAddress(final int prevDataAddress) {
        fPrevDataAddress = prevDataAddress;
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
