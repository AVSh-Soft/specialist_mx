package ru.avsh.specialist.mx.gui.swing.debugger.types;

/**
 * Регистровые пары, отображаемые в таблице отладчика.
 * <p>
 * {@code ordinal()} используется как индекс при обращении к процессору: он совпадает
 * с индексом пары в контракте {@link ru.avsh.specialist.mx.units.types.CpuDebugCore}
 * (0 - AF, 1 - BC, 2 - DE, 3 - HL, 4 - IX, 5 - IY, 6 - SP, 7 - PC,
 * 8 - AF', 9 - BC', 10 - DE', 11 - HL'). Пары альтернативного регистрового файла Z80
 * дописаны в конец перечисления, чтобы не сдвигать индексы основных пар.
 * <p>
 * Порядок строк в таблице задаёт {@link #getByRowIndex(int, boolean, boolean)}:
 * пары альтернативного файла идут сразу под своими основными парами.
 * <p>
 * Пары, которых нет у процессора (IX, IY и альтернативный файл у I8080), остаются
 * в перечислении, но не показываются в таблице - см. {@link #isSupported(boolean, boolean)}.
 */
public enum DebugRegPair {
    AF,
    BC,
    DE,
    HL,
    IX,
    IY,
    SP,
    PC,
    AF_ALT,
    BC_ALT,
    DE_ALT,
    HL_ALT;

    /**
     * Показывает, отображается ли пара в таблице регистров процессора.
     *
     * @param isZ80                true = активен процессор Z80
     * @param isShowIndexRegisters true = показывать пары IX и IY
     * @return true = пара отображается
     */
    public boolean isSupported(final boolean isZ80, final boolean isShowIndexRegisters) {
        if (isAlternative()) {
            return isZ80;
        }
        if ((this == IX) || (this == IY)) {
            return isZ80 && isShowIndexRegisters;
        }
        return true;
    }

    /**
     * Показывает, относится ли пара к альтернативному регистровому файлу Z80.
     *
     * @return true = пара альтернативного файла
     */
    public boolean isAlternative() {
        return ordinal() >= AF_ALT.ordinal();
    }

    /**
     * Возвращает имя пары для вывода в таблице регистров.
     * Пары альтернативного файла выводятся со штрихом, как принято у Z80: AF', BC', DE', HL'.
     *
     * @return имя регистровой пары
     */
    public String getName() {
        return isAlternative() ? name().substring(0, name().indexOf('_')) + "'" : name();
    }

    /**
     * Возвращает количество пар, отображаемых для процессора.
     *
     * @param isZ80                true = активен процессор Z80
     * @param isShowIndexRegisters true = показывать пары IX и IY
     * @return количество отображаемых пар
     */
    public static int getSupportedCount(final boolean isZ80, final boolean isShowIndexRegisters) {
        int count = 0;
        for (final DebugRegPair regPair : values()) {
            if (regPair.isSupported(isZ80, isShowIndexRegisters)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Возвращает пару по индексу строки таблицы регистров.
     *
     * @param rowIndex             индекс строки таблицы
     * @param isZ80                true = активен процессор Z80
     * @param isShowIndexRegisters true = показывать пары IX и IY
     * @return регистровая пара или null, если индекс вне диапазона
     */
    public static DebugRegPair getByRowIndex(final int rowIndex,
                                             final boolean isZ80,
                                             final boolean isShowIndexRegisters) {
        int row = 0;
        for (final DebugRegPair regPair : rowsOrder(isZ80, isShowIndexRegisters)) {
            if (row++ == rowIndex) {
                return regPair;
            }
        }
        return null;
    }

    /**
     * Возвращает порядок пар в таблице регистров.
     * Пары альтернативного файла идут под своими основными парами: AF, AF', BC, BC', ...
     *
     * @param isZ80                true = активен процессор Z80
     * @param isShowIndexRegisters true = показывать пары IX и IY
     * @return отображаемые пары в порядке строк таблицы
     */
    private static DebugRegPair[] rowsOrder(final boolean isZ80, final boolean isShowIndexRegisters) {
        if (!isZ80) {
            return new DebugRegPair[] { AF, BC, DE, HL, SP, PC };
        }
        if (!isShowIndexRegisters) {
            return new DebugRegPair[] { AF, AF_ALT, BC, BC_ALT, DE, DE_ALT, HL, HL_ALT, SP, PC };
        }
        return new DebugRegPair[] { AF, AF_ALT, BC, BC_ALT, DE, DE_ALT, HL, HL_ALT, IX, IY, SP, PC };
    }
}
