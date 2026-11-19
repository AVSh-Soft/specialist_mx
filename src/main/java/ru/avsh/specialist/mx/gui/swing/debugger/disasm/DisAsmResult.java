package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPU;

/**
 * Класс "Результат дизассемблирования одной команды CPU".
 * Содержит длину команды, её текст и признаки для навигации по коду.
 */
public final class DisAsmResult {
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
    public DisAsmResult(final int length, final @NotNull String text, final boolean isJmp, final boolean isCall) {
        fLength = length;
        fText   = text  ;
        fIsJmp  = isJmp ;
        fIsCall = isCall;
    }

    /**
     * Возвращает байт команды по индексу.
     *
     * @param bytes байты команды
     * @param index индекс байта
     * @return байт или {@link DebuggerCPU#EMPTY_VALUE}
     */
    public static int byteAt(final int[] bytes, final int index) {
        return (index < bytes.length) ? bytes[index] : DebuggerCPU.EMPTY_VALUE;
    }

    /**
     * Формирует строку из одного байта.
     *
     * @param value байт
     * @return два hex-символа или пустая строка
     */
    @NotNull
    public static String hex8(final int value) {
        return (value >= 0) ? String.format("%02X", value) : "";
    }

    /**
     * Формирует строку из двух байтов.
     *
     * @param high старший байт
     * @param low  младший байт
     * @return четыре hex-символа или пустая строка
     */
    @NotNull
    public static String hex16(final int high, final int low) {
        return ((high >= 0) && (low >= 0)) ? String.format("%02X%02X", high, low) : "";
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
}
