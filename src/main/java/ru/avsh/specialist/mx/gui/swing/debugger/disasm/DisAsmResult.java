package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Класс "Результат дизассемблирования одной команды CPU".
 * Содержит длину команды, её текст и признаки для навигации по коду.
 */
public final class DisAsmResult {
    // Код байта данных, который не читался (в команде меньше байт)
    public static final int EMPTY = -1;
    // Код байта данных, который попал в команду из-за коллизии с PC
    public static final int WRONG = -2;

    private final int     fLength;
    private final String  fText  ;
    private final boolean fIsJmp ;
    private final boolean fIsCall;

    /**
     * Конструктор.
     *
     * @param length длина команды в байтах (1..4)
     * @param text   текст команды
     * @param isJmp  true = команда передачи управления (JMP/Jxx/JP/JR/DJNZ)
     * @param isCall true = команда вызова подпрограммы (CALL/Cxx/RST)
     */
    public DisAsmResult(final int length,
                        @NotNull final String text,
                        final boolean isJmp,
                        final boolean isCall) {
        fLength = length;
        fText   = text;
        fIsJmp  = isJmp;
        fIsCall = isCall;
    }

    /**
     * Возвращает длину команды.
     *
     * @return длина команды в байтах (1..4)
     */
    public int getLength() {
        return fLength;
    }

    /**
     * Возвращает текст команды (мнемоника + операнды).
     *
     * @return текст команды
     */
    @NotNull
    public String getText() {
        return fText;
    }

    /**
     * Показывает, является ли команда командой передачи управления.
     *
     * @return true = команда передачи управления
     */
    public boolean isJmpCmd() {
        return fIsJmp;
    }

    /**
     * Показывает, является ли команда командой вызова подпрограммы.
     *
     * @return true = команда вызова подпрограммы
     */
    public boolean isCallCmd() {
        return fIsCall;
    }

    /**
     * Возвращает 16-битное значение из байтов команды или отрицательное значение,
     * если в команде нет 16-битных данных (учитываются признаки EMPTY и WRONG).
     *
     * @param byte1 второй байт команды
     * @param byte2 третий байт команды
     * @return 16-битное значение или отрицательное значение
     */
    public static int getValue16(final int byte1, final int byte2) {
        return ((byte1 >= 0) && (byte2 >= 0)) ? (byte1 | (byte2 << 8)) : EMPTY;
    }

    /**
     * Возвращает 16-битное значение из байтов команды со старшим байтом первым.
     * Байты идут в порядке, в котором они записаны в команде Z80.
     *
     * @param high первый по порядку байт (старший)
     * @param low  второй по порядку байт (младший)
     * @return 16-битное значение или отрицательное значение, если байты не прочитаны
     */
    public static int getValue16BigEndian(final int high, final int low) {
        return ((high >= 0) && (low >= 0)) ? ((high << 8) | low) : EMPTY;
    }

    /**
     * Возвращает 16-битное значение (адрес), записанное в команде по адресу в памяти.
     * Байты передаются в порядке, в котором они лежат в памяти: у i8080 адрес записан
     * младшим байтом вперед, у Z80 - старшим.
     *
     * @param isZ80 true = процессор Z80 (адрес старшим байтом вперед), false = i8080
     * @param byte1 первый по порядку байт команды (второй байт команды)
     * @param byte2 второй по порядку байт команды (третий байт команды)
     * @return 16-битное значение или {@link #EMPTY}, если байты не прочитаны
     */
    public static int getCmdAddress(final boolean isZ80, final int byte1, final int byte2) {
        return isZ80 ? getValue16BigEndian(byte2, byte1) : getValue16(byte1, byte2);
    }

    /**
     * Возвращает байт данных команды в виде hex-строки.
     *
     * @param value байт данных (может быть EMPTY/WRONG)
     * @return два hex-символа или пустая строка
     */
    @NotNull
    public static String toHex(@Nullable final Integer value) {
        return ((value == null) || (value < 0)) ? "" : String.format("%02X", value);
    }
}
