package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import static ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPU.EMPTY_VALUE;
import static ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPU.WRONG_VALUE;

/**
 * Офлайн-проверка дизассемблеров: длина команды, которую отдаёт дизассемблер,
 * совпадает с длиной по первым байтам, укладывается в размер записи отладчика
 * и не приводит к выходу за границы буфера при заполнении таблицы.
 * Запускается вручную: java -cp <classes> ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmSelfTest
 */
public final class DisAsmSelfTest {
    // Размер буфера таблицы дизассемблера отладчика (DebuggerCPU.BUF_SIZE)
    private static final int BUF_SIZE = 64;
    // Коды условий общие для обоих процессоров
    private static final String[] CONDS = { "NZ", "Z", "NC", "C", "PO", "PE", "P", "M" };

    private static int fPassed;
    private static int fFailed;

    private DisAsmSelfTest() {
        //
    }

    private static void fail(final String message, final Object... args) {
        fFailed++;
        System.out.printf("FAIL " + message + "%n", args);
    }

    /**
     * Проверяет, что длина по первым двум байтам совпадает с длиной разобранной команды.
     */
    private static void checkLength(final String name, final DisAsm disAsm,
                                    final int b0, final int b1, final int b2, final int b3) {
        final int len = disAsm.disAsm(0x0000, new int[] { b0, b1, b2, b3 }).getLength();
        if ((len < 1) || (len > disAsm.getMaxCmdLength())) {
            fail("%s: длина %d вне 1..%d для %02X %02X", name, len, disAsm.getMaxCmdLength(), b0, b1);
        } else {
            fPassed++;
        }
    }

    /**
     * Проверяет, что команда Z80 имеет ожидаемый текст и что по ней прочитаны
     * все байты, которые в ней есть. Длину, которую отдаёт {@link DisAsmZ80#getCmdLength},
     * отладчик использует, чтобы решить, сколько байтов команды показать в таблице:
     * если она меньше реальной, в таблице остаётся один код операции без операндов.
     */
    private static void checkZ80Length(final int expectedLen, final String expectedText, final int... bytes) {
        final int byBytes = DisAsmZ80.getCmdLength(bytes);
        final DisAsmResult result = new DisAsmZ80().disAsm(0x1000, bytes);
        if (byBytes != expectedLen) {
            fail("Z80 %s: getCmdLength=%d, ожидалось %d", expectedText, byBytes, expectedLen);
        } else if (result.getLength() != expectedLen) {
            fail("Z80 %s: длина дизассемблера %d, ожидалось %d",
                    expectedText, result.getLength(), expectedLen);
        } else if (!expectedText.equals(result.getText())) {
            fail("Z80: текст \"%s\", ожидалось \"%s\"", result.getText(), expectedText);
        } else {
            fPassed++;
        }
    }

    /**
     * Проверяет, что команда i8080 определённой длины имеет ожидаемый текст.
     */
    private static void checkI8080(final int expectedLen, final String expectedText, final int... bytes) {
        final DisAsmResult result = new DisAsmI8080().disAsm(0x1000, bytes);
        if (result.getLength() != expectedLen) {
            fail("i8080 %s: длина %d, ожидалось %d", expectedText, result.getLength(), expectedLen);
        } else if (!expectedText.equals(result.getText())) {
            fail("i8080: текст \"%s\", ожидалось \"%s\"", result.getText(), expectedText);
        } else {
            fPassed++;
        }
    }

    /**
     * Проверяет, что команда Z80 определённой длины имеет ожидаемый текст.
     */
    private static void checkZ80(final int expectedLen, final String expectedText, final int... bytes) {
        final DisAsmResult result = new DisAsmZ80().disAsm(0x1000, bytes);
        if (result.getLength() != expectedLen) {
            fail("Z80 %s: длина %d, ожидалось %d", expectedText, result.getLength(), expectedLen);
        } else if (!expectedText.equals(result.getText())) {
            fail("Z80: текст \"%s\", ожидалось \"%s\"", result.getText(), expectedText);
        } else {
            fPassed++;
        }
    }

    /**
     * Проверяет, какой адрес дизассемблер Z80 извлекает из команды: от этого зависит
     * подкраска связанного адреса и переход по двойному клику в отладчике.
     */
    private static void checkZ80Addr(final int expected, final int address, final int... bytes) {
        final int got = DisAsmZ80.getCmdAddress(address, bytes);
        if (got != expected) {
            fail("Z80 адрес %s: получено %04X, ожидалось %04X",
                    new DisAsmZ80().disAsm(address, bytes).getText(), got, expected);
        } else {
            fPassed++;
        }
    }

    /**
     * Заполняет буфер таблицы дизассемблера так же, как это делает отладчик,
     * и проверяет, что ни одна запись не выходит за границы массива.
     */
    private static void checkFillBuffer(final String name, @org.jetbrains.annotations.NotNull final DisAsm disAsm,
                                        final int[] mem) {
        // Размеры записи и цикла взяты из DebuggerCPU.DisAsmTableModel:
        // IND_CMD = 1, fRecSize = IND_CMD + fMaxCmdLength, fNumDataBytes = fMaxCmdLength - 1
        final int indCmd  = 1;
        final int recSize = indCmd + disAsm.getMaxCmdLength();
        final int numData = disAsm.getMaxCmdLength() - 1;
        for (int pc = 0; pc < 0x10000; pc += 13) {
            final int[][] buf = new int[BUF_SIZE][];
            for (int i = 0; i < BUF_SIZE; i++) {
                buf[i] = new int[recSize];
            }
            int address = 0;
            for (int i = 0; i < BUF_SIZE; i++) {
                final int[] record = buf[i];
                record[0] = address;
                final int cmdLen = disAsm.disAsm(address, new int[] {
                        mem[address],
                        mem[(address + 1) & 0xFFFF],
                        mem[(address + 2) & 0xFFFF],
                        mem[(address + 3) & 0xFFFF]
                }).getLength();
                if ((cmdLen < 1) || (cmdLen > disAsm.getMaxCmdLength())) {
                    fail("%s: длина %d вне 1..%d по адресу %04X",
                            name, cmdLen, disAsm.getMaxCmdLength(), address);
                    return;
                }
                final boolean collide = (pc > address) && (pc < address + cmdLen);
                record[indCmd] = mem[address];
                for (int k = 1; k <= numData; k++) {
                    record[indCmd + k] = collide ? ((pc == address + k) ? WRONG_VALUE : EMPTY_VALUE)
                                                 : ((k < cmdLen) ? mem[(address + k) & 0xFFFF] : EMPTY_VALUE);
                }
                address = (address + (collide ? pc - address : cmdLen)) & 0xFFFF;
            }
        }
        fPassed++;
    }

    public static void main(final String[] args) {
        // Длины команд обоих процессоров: перебираются все коды операций
        final DisAsmI8080 disAsmI8080 = new DisAsmI8080();
        final DisAsmZ80   disAsmZ80   = new DisAsmZ80  ();
        for (int code = 0x00; code <= 0xFF; code++) {
            checkLength("i8080", disAsmI8080, code, 0x34, 0x12, 0x01);
        }
        for (int b0 = 0x00; b0 <= 0xFF; b0++) {
            for (int b1 = 0x00; b1 <= 0xFF; b1++) {
                checkLength("Z80", disAsmZ80, b0, b1, 0x02, 0x03);
            }
        }

        // Команды с префиксами: длина обязана совпадать с числом байтов в мнемонике
        checkZ80(3, "LD   A,(IX+05)",       0xDD, 0x7E, 0x05);
        checkZ80(4, "LD   (IX+02),77",      0xDD, 0x36, 0x02, 0x77);
        checkZ80(3, "INC  (IX+02)",         0xDD, 0x34, 0x02);
        checkZ80(4, "LD   IX,1234",         0xDD, 0x21, 0x34, 0x12);
        checkZ80(2, "LD   IXH,A",           0xDD, 0x67);
        checkZ80(4, "LD   BC,(1234)",       0xED, 0x4B, 0x34, 0x12);
        checkZ80(2, "SBC  HL,BC",           0xED, 0x42);
        checkZ80(2, "BIT  7,(HL)",          0xCB, 0x7E);
        checkZ80(4, "SET  0,(IY+00)",       0xFD, 0xCB, 0x00, 0xC6);

        // DD CB / FD CB: у сдвигов и RES/SET результат пишется и в память,
        // и в регистр r (r != 6). У BIT результата нет — поле r не используется,
        // поэтому регистр в мнемонике не выводится ни при каком r
        checkZ80(4, "RLC  (IX+02),B",       0xDD, 0xCB, 0x02, 0x00);
        checkZ80(4, "RLC  (IX+02)",         0xDD, 0xCB, 0x02, 0x06);
        checkZ80(4, "BIT  0,(IX+02)",       0xDD, 0xCB, 0x02, 0x46);
        checkZ80(4, "BIT  0,(IX+02)",       0xDD, 0xCB, 0x02, 0x40);
        checkZ80(4, "SET  0,(IY+01),B",     0xFD, 0xCB, 0x01, 0xC0);
        checkZ80(4, "SET  0,(IY+01)",       0xFD, 0xCB, 0x01, 0xC6);
        checkZ80(4, "RES  7,(IY+01),A",     0xFD, 0xCB, 0x01, 0xBF);

        // ED: недокументированные IN F,(C) и OUT (C),0
        checkZ80(2, "IN   F,(C)",           0xED, 0x70);
        checkZ80(2, "OUT  (C),0",           0xED, 0x71);
        checkZ80(2, "IN   A,(C)",           0xED, 0x78);
        checkZ80(2, "OUT  (C),A",           0xED, 0x79);

        // ED: все варианты NEG, ADC HL,ss и SBC HL,ss
        for (final int b : new int[] { 0x44, 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C, 0x77, 0x7F }) {
            checkZ80(2, "NEG",              0xED, b);
        }
        final String[] pairs = { "BC", "DE", "HL", "SP" };
        for (int k = 0; k < pairs.length; k++) {
            checkZ80(2, "ADC  HL," + pairs[k], 0xED, 0x4A + (k << 4));
            checkZ80(2, "SBC  HL," + pairs[k], 0xED, 0x42 + (k << 4));
        }

        // ED: возвраты и режимы прерываний
        for (final int b : new int[] { 0x45, 0x4D, 0x55, 0x5D }) {
            checkZ80(2, "RETN",             0xED, b);
        }
        for (final int b : new int[] { 0x65, 0x6D, 0x75, 0x7D }) {
            checkZ80(2, "RETI",             0xED, b);
        }
        checkZ80(2, "IM   0",               0xED, 0x46);
        checkZ80(2, "IM   0",               0xED, 0x66);
        checkZ80(2, "IM   0/1",             0xED, 0x4E);
        checkZ80(2, "IM   0/1",             0xED, 0x6E);
        checkZ80(2, "IM   1",               0xED, 0x56);
        checkZ80(2, "IM   1",               0xED, 0x76);
        checkZ80(2, "IM   2",               0xED, 0x5E);
        checkZ80(2, "IM   2",               0xED, 0x7E);

        // ED: прочие коды Z80 выполняет как NOP длиной два байта
        checkZ80(2, "NOP",                  0xED, 0x00);
        checkZ80(2, "NOP",                  0xED, 0xFF);

        // DD/FD: LD IXH/IXL/IYH/IYL,n — длина 3, байт данных обязателен
        checkZ80Length(3, "LD   IXH,12",    0xDD, 0x26, 0x12);
        checkZ80Length(3, "LD   IXL,34",    0xDD, 0x2E, 0x34);
        checkZ80Length(3, "LD   IYH,56",    0xFD, 0x26, 0x56);

        // DD/FD: HALT и команды, на которые префикс не влияет
        checkZ80(2, "HALT",                 0xDD, 0x76);
        checkZ80(2, "NOP",                  0xDD, 0x00);
        checkZ80(2, "LD   A,12",            0xDD, 0x3E, 0x12);
        checkZ80(2, "JP   1234",            0xDD, 0xC3, 0x34, 0x12);
        checkZ80(2, "DI",                   0xDD, 0xF3);
        checkZ80(2, "OUT  (12),A",          0xDD, 0xD3, 0x12);
        checkZ80(2, "ED",                   0xDD, 0xED);
        checkZ80(2, "NOP",                  0xDD, 0xDD);
        checkZ80(2, "NOP",                  0xDD, 0xFD);

        // DD/FD: LD (IX+d),H/L пишет обычные H/L, а не IXH/IXL;
        // LD H/L,(IX+d) читает в обычные H/L — так же работает и эмулятор
        checkZ80(3, "LD   (IX+02),H",       0xDD, 0x74, 0x02);
        checkZ80(3, "LD   (IX+02),L",       0xDD, 0x75, 0x02);
        checkZ80(3, "LD   H,(IX+02)",       0xDD, 0x66, 0x02);
        checkZ80(3, "LD   L,(IX+02)",       0xDD, 0x6E, 0x02);
        checkZ80(2, "LD   IXH,A",           0xDD, 0x67);
        checkZ80(2, "LD   IXH,IXL",         0xDD, 0x65);

        // PUSH/POP: четвёртая пара — AF, а не SP (код F1 — это POP AF, F9 — LD SP,HL)
        checkZ80(1, "PUSH BC",              0xC5);
        checkZ80(1, "PUSH DE",              0xD5);
        checkZ80(1, "PUSH HL",              0xE5);
        checkZ80(1, "PUSH AF",              0xF5);
        checkZ80(1, "POP  BC",              0xC1);
        checkZ80(1, "POP  DE",              0xD1);
        checkZ80(1, "POP  HL",              0xE1);
        checkZ80(1, "POP  AF",              0xF1);
        checkZ80(1, "LD   SP,HL",           0xF9);

        // Полный перебор: дизассемблер не должен возвращать «сырой» код операции
        // ни для одного кода после префиксов ED, DD, FD
        for (int b1 = 0x00; b1 <= 0xFF; b1++) {
            for (final int pfx : new int[] { 0xED, 0xDD, 0xFD }) {
                final String stub = (pfx == 0xED) ? "ED" : ((pfx == 0xDD) ? "DD" : "FD");
                final String got  = disAsmZ80.disAsm(0x1000, new int[] { pfx, b1, 0x02, 0x03 }).getText();
                if (stub.equals(got)) {
                    fail("Z80 %02X %02X: осталась заглушка \"%s\"", pfx, b1, got);
                } else {
                    fPassed++;
                }
            }
        }

        // Длины команд по первым байтам совпадают с длиной разобранной команды:
        // иначе отладчик показывает команду без байтов операндов
        checkZ80Length(3, "CALL NZ,1234", 0xC4, 0x34, 0x12);
        checkZ80Length(3, "CALL Z,1234",  0xCC, 0x34, 0x12);
        checkZ80Length(3, "CALL NC,1234", 0xD4, 0x34, 0x12);
        checkZ80Length(3, "CALL C,1234",  0xDC, 0x34, 0x12);
        checkZ80Length(3, "CALL PO,1234", 0xE4, 0x34, 0x12);
        checkZ80Length(3, "CALL PE,1234", 0xEC, 0x34, 0x12);
        checkZ80Length(3, "CALL P,1234",  0xF4, 0x34, 0x12);
        checkZ80Length(3, "CALL M,1234",  0xFC, 0x34, 0x12);
        checkZ80Length(3, "CALL 1234",    0xCD, 0x34, 0x12);
        checkZ80Length(3, "JP   NZ,1234", 0xC2, 0x34, 0x12);
        checkZ80Length(3, "JP   Z,1234",  0xCA, 0x34, 0x12);
        checkZ80Length(3, "JP   M,1234",  0xFA, 0x34, 0x12);
        checkZ80Length(3, "JP   1234",    0xC3, 0x34, 0x12);
        checkZ80Length(2, "JR   Z,12",    0x28, 0x12);
        checkZ80Length(3, "LD   (1234),A", 0x32, 0x34, 0x12);
        checkZ80Length(2, "ADD  A,12",    0xC6, 0x12);
        checkZ80Length(1, "RST  08H",     0xCF);
        checkZ80Length(1, "RET  Z",       0xC8);
        checkZ80Length(1, "RET",          0xC9);

        // RET и его варианты: безусловный и по всем восьми условиям
        checkI8080(1, "RET",               0xC9);
        for (int cond = 0; cond < 8; cond++) {
            checkI8080(1, "R" + CONDS[cond], 0xC0 | (cond << 3));
        }
        checkZ80(1, "RET",                 0xC9);
        for (int cond = 0; cond < 8; cond++) {
            checkZ80(1, "RET  " + CONDS[cond], 0xC0 | (cond << 3));
        }

        // Недокументированные команды i8080 показываются в нижнем регистре,
        // в отличие от документированных с тем же действием
        checkI8080(3, "CALL 1234",         0xCD, 0x34, 0x12);
        checkI8080(3, "call 1234",         0xDD, 0x34, 0x12);
        checkI8080(3, "call 1234",         0xED, 0x34, 0x12);
        checkI8080(3, "call 1234",         0xFD, 0x34, 0x12);
        checkI8080(3, "JMP  1234",         0xC3, 0x34, 0x12);
        checkI8080(3, "jmp  1234",         0xCB, 0x34, 0x12);
        checkI8080(1, "RET",               0xC9);
        checkI8080(1, "ret",               0xD9);

        // Недокументированные NOP i8080 тоже показываются в нижнем регистре,
        // документированный NOP (0x00) - в верхнем
        checkI8080(1, "NOP",               0x00);
        for (final int code : new int[] { 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38 }) {
            checkI8080(1, "nop",           code);
        }

        // Адрес из команды Z80: он берётся из байтов, которые дизассемблер показывает как адрес,
        // а не всегда из байтов 1-2 (у i8080 так можно было, у Z80 - нет)
        checkZ80Addr(0x1234, 0x1000, 0xC3, 0x34, 0x12);        // JP   nn
        checkZ80Addr(0x1234, 0x1000, 0xCD, 0x34, 0x12);        // CALL nn
        checkZ80Addr(0x1234, 0x1000, 0xC4, 0x34, 0x12);        // CALL NZ,nn
        checkZ80Addr(0x1234, 0x1000, 0xDA, 0x34, 0x12);        // JP   C,nn
        checkZ80Addr(0x1234, 0x1000, 0xED, 0x4B, 0x34, 0x12);  // LD   BC,(nn)
        checkZ80Addr(0x1234, 0x1000, 0xED, 0x73, 0x34, 0x12);  // LD   (nn),SP
        checkZ80Addr(0x1234, 0x1000, 0xDD, 0x21, 0x34, 0x12);  // LD   IX,nn
        checkZ80Addr(0x1234, 0x1000, 0xFD, 0x2A, 0x34, 0x12);  // LD   IY,(nn)
        checkZ80Addr(0x1234, 0x1000, 0x2A, 0x34, 0x12);        // LD   HL,(nn)
        checkZ80Addr(0x1234, 0x1000, 0x32, 0x34, 0x12);        // LD   (nn),A
        checkZ80Addr(0x1234, 0x1000, 0x3A, 0x34, 0x12);        // LD   A,(nn)
        checkZ80Addr(0x1234, 0x1000, 0x01, 0x34, 0x12);        // LD   BC,nn - не адрес, но 16-битные данные
        checkZ80Addr(0x1010, 0x1000, 0x18, 0x0E);              // JR   +0E
        checkZ80Addr(0x0FF2, 0x1000, 0x18, 0xF0);              // JR   -10
        checkZ80Addr(0x0FFE, 0x1000, 0x20, 0xFC);              // JR   NZ,-04
        checkZ80Addr(0x1000, 0x1000, 0x10, 0xFE);              // DJNZ сам на себя
        // Команды без байтов адреса адреса не дают
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xC9);               // RET
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xCF);               // RST  08H
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xE9);               // JP   (HL)
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xED, 0x45);         // RETN
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xED, 0x78);         // IN   A,(C)
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xCB, 0x7E);         // BIT  7,(HL)
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xDD, 0x7E, 0x05);   // LD   A,(IX+05)
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xDD, 0x36, 0x02, 0x77); // LD (IX+02),77
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xDD, 0xCB, 0x00, 0xC6); // SET 0,(IY+00)
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xC6, 0x12);         // ADD  A,12
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xD3, 0x12);         // OUT  (12),A
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xDD, 0x26, 0x12);   // LD   IXH,12
        // Непрочитанный байт (EMPTY/WRONG из таблицы отладчика) адресом не считается
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xC3, 0x34, EMPTY_VALUE);
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0xED, 0x4B, 0x34, WRONG_VALUE);
        checkZ80Addr(EMPTY_VALUE, 0x1000, 0x18, EMPTY_VALUE);

        // Заполнение буфера отладчика на реальном коде дизассемблеров
        final int[] mem = new int[0x10000];
        for (int k = 0; k < mem.length; k++) {
            mem[k] = ((k * 0x5B) + 0x5B) & 0xFF;
        }
        checkFillBuffer("i8080", disAsmI8080, mem);
        checkFillBuffer("Z80"  , disAsmZ80,   mem);

        System.out.printf("%nИТОГ: passed=%d failed=%d%n", fPassed, fFailed);
        if (fFailed > 0) {
            System.exit(1);
        }
    }
}
