package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import org.jetbrains.annotations.NotNull;

/**
 * Интерфейс "Дизассемблер процессора".
 * Используется отладчиком для отображения кода в таблице дизассемблера.
 */
public interface DisAsm {
    /**
     * Возвращает максимальную длину команды в байтах.
     *
     * @return максимальная длина команды (1..4)
     */
    int getMaxCmdLength();

    /**
     * Возвращает самый длинный текст команды (мнемоника с операндами),
     * который может выдать дизассемблер.
     * Используется для расчёта ширины колонки с текстом команды.
     *
     * @return самый длинный текст команды
     */
    @NotNull
    String getMaxCmdText();

    /**
     * Дизассемблирует команду по заданному адресу.
     *
     * @param address адрес команды
     * @param bytes   байты команды, начиная с кода операции
     * @return результат дизассемблирования
     */
    @NotNull
    DisAsmResult disAsm(final int address, final int[] bytes);
}
