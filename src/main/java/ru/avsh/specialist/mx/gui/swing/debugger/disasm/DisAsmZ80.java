package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmResult.*;

/**
 * Класс "Дизассемблер процессора Zilog Z80".
 * Поддерживает основной набор команд, префиксы CB, ED, DD, FD
 * и составные команды DD CB d op / FD CB d op.
 */
public final class DisAsmZ80 implements DisAsm {
    // Максимальная длина команды Z80 (DD CB d op / FD CB d op)
    public  static final int MAX_CMD_LENGTH  = 4;
    // Длина составной команды DD CB d op / FD CB d op
    private static final int INDEX_CB_LENGTH = MAX_CMD_LENGTH;
    // Отображаемая длина команды с префиксом DD/FD
    private static final int INDEX_LENGTH = 2;

    private static final int PREFIX_CB = 0xCB;
    private static final int PREFIX_DD = 0xDD;
    private static final int PREFIX_ED = 0xED;
    private static final int PREFIX_FD = 0xFD;

    // Имена 8-битных регистров в кодировке команды
    private static final String[] REGS    = { "B", "C", "D", "E", "H", "L", "(HL)", "A" };
    // Имена 8-битных регистров для префиксов DD/FD
    private static final String[] REGS_IX = { "B", "C", "D", "E", "IXH", "IXL", "(IX+d)", "A" };
    private static final String[] REGS_IY = { "B", "C", "D", "E", "IYH", "IYL", "(IY+d)", "A" };
    // Имена 16-битных регистровых пар в кодировке команды
    private static final String[] PAIRS_HL = { "BC", "DE", "HL", "SP" };
    // Регистровые пары команд PUSH/POP: четвёртая пара — AF, а не SP
    private static final String[] PAIRS_STACK = { "BC", "DE", "HL", "AF" };
    // Коды условий
    private static final String[] CONDS = { "NZ", "Z", "NC", "C", "PO", "PE", "P", "M" };
    // Мнемоники АЛУ в кодировке команды
    private static final String[] ALU = { "ADD  A,", "ADC  A,", "SUB  ", "SBC  A,", "AND  ", "XOR  ", "OR   ", "CP   " };
    // Блоки команд CB: сдвиги/ротации, операции с битами
    private static final String[] CB_OPS     = { "RLC  ", "RRC  ", "RL   ", "RR   ", "SLA  ", "SRA  ", "SLL  ", "SRL  " };
    private static final String[] CB_BIT_OPS = { "BIT  ", "RES  ", "SET  " };
    // Имена регистров специального назначения для команд ED (IN/OUT)
    private static final String[] ED_REGS = { "B", "C", "D", "E", "H", "L", null, "A" };
    // Блочные команды ED A0..BB: индекс — код со снятым битом 3 (шаг вперёд/назад)
    private static final String[] ED_BLOCK = { "LDI", "CPI", "INI", "OUTI", "LDD", "CPD", "IND", "OUTD" };
    // Режимы прерываний ED 46..7E. Бит 4 (пара) и бит 3 (RETN/RETI) на режим не влияют,
    // поэтому IM повторяется в обеих половинах диапазона: 46 и 66 — IM 0, 4E и 6E — IM 0/1,
    // 56 и 76 — IM 1, 5E и 7E — IM 2
    private static final String[] ED_IM = { "IM   0", "IM   0/1", "IM   1", "IM   2" };
    // Номера векторов RST
    private static final String[] RST = { "00H", "08H", "10H", "18H", "20H", "28H", "30H", "38H" };

    // Длины команд основного набора Z80
    private static final int[] MAIN_LENGTH = buildMainLength();

    /**
     * Возвращает длину команды Z80 по первым байтам.
     *
     * @param bytes первые байты команды (для DD CB / FD CB необходимо два байта)
     * @return длина команды в байтах
     */
    public static int getCmdLength(final int[] bytes) {
        return lengthOf(bytes[0] & 0xFF, byteAt(bytes, 1));
    }

    /**
     * Возвращает адрес из команды Z80 - 16-битный адрес, записанный в ней байтами,
     * которые дизассемблер показывает как адрес ({@code JP nn}, {@code CALL nn},
     * {@code LD HL,(nn)}, {@code LD (nn),HL}, {@code LD A,(nn)}, команды ED с {@code (nn)},
     * {@code LD IX,nn}). Для {@code JR}/{@code DJNZ} адрес перехода вычисляется
     * из смещения относительно адреса команды.
     * Команды без байтов адреса ({@code RET}, {@code RST}, {@code JP (HL)},
     * команды с индексным смещением или с 8-битными данными) адреса не дают.
     *
     * @param address адрес команды
     * @param bytes   байты команды, начиная с кода операции
     * @return адрес из команды или отрицательное значение, если в команде нет адреса
     */
    public static int getCmdAddress(final int address, final int[] bytes) {
        final int code = byteAt(bytes, 0);
        final int bt1  = byteAt(bytes, 1);
        if ((code < 0) || (bt1 < 0)) {
            return -1;
        }
        switch (code) {
            case 0x10: case 0x18: case 0x20: case 0x28: case 0x30: case 0x38:
                // JR/DJNZ: байт после кода операции - знаковое смещение
                return (address + 2 + (byte) bt1) & 0xFFFF;
            case PREFIX_CB:
                return -1;
            case PREFIX_ED:
                // ED xx nn nn: адрес есть только у команд LD rr,(nn) / LD (nn),rr,
                // остальные коды ED занимают два байта и адреса не содержат
                return hasWord(bytes, 2, 3) ? wordAt(bytes, 2, 3) : -1;
            case PREFIX_DD:
            case PREFIX_FD:
                // LD IX,nn / LD (nn),IX / LD IX,(nn); из DD CB / FD CB адрес взят не может,
                // так как байт 1 - код операции CB, а не код с 16-битным операндом
                return ((bt1 == 0x21) || (bt1 == 0x22) || (bt1 == 0x2A)) && hasWord(bytes, 2, 3)
                        ? wordAt(bytes, 2, 3)
                        : -1;
            default:
                break;
        }
        // Основной набор: все команды, где в байтах 1 и 2 записан 16-битный операнд
        switch (code) {
            case 0x01: case 0x11: case 0x21: case 0x31: // LD rr,nn
            case 0x22: case 0x2A: case 0x32: case 0x3A: // LD (nn),HL / LD HL,(nn) / LD (nn),A / LD A,(nn)
            case 0xC2: case 0xCA: case 0xD2: case 0xDA: // JP cc,nn
            case 0xE2: case 0xEA: case 0xF2: case 0xFA:
            case 0xC4: case 0xCC: case 0xD4: case 0xDC: // CALL cc,nn
            case 0xE4: case 0xEC: case 0xF4: case 0xFC:
            case 0xC3: case 0xCD:                       // JP nn / CALL nn
                return hasWord(bytes, 1, 2) ? wordAt(bytes, 1, 2) : -1;
            default:
                return -1;
        }
    }

    /**
     * Проверяет, что байты с заданными индексами прочитаны (в буфере отладчика
     * непрочитанный байт помечен отрицательным значением {@code EMPTY}/{@code WRONG}).
     *
     * @param bytes  байты команды
     * @param lowIdx индекс младшего байта
     * @param highIdx индекс старшего байта
     * @return true = оба байта прочитаны
     */
    private static boolean hasWord(final int[] bytes, final int lowIdx, final int highIdx) {
        return (byteAt(bytes, lowIdx) >= 0) && (byteAt(bytes, highIdx) >= 0);
    }

    /**
     * Возвращает 16-битное значение, собранное из двух байтов команды.
     *
     * @param bytes   байты команды
     * @param lowIdx  индекс младшего байта
     * @param highIdx индекс старшего байта
     * @return 16-битное значение (байты берутся без знака)
     */
    private static int wordAt(final int[] bytes, final int lowIdx, final int highIdx) {
        return (byteAt(bytes, lowIdx) & 0xFF) | ((byteAt(bytes, highIdx) & 0xFF) << 8);
    }

    /**
     * Возвращает длину команды по коду операции и следующему байту (единственное
     * место расчёта длины: используется и таблицей отладчика, и дизассемблированием).
     *
     * @param code код операции (или префикс)
     * @param next следующий байт команды
     * @return длина команды в байтах
     */
    private static int lengthOf(final int code, final int next) {
        switch (code) {
            case PREFIX_CB:
                return 2;
            case PREFIX_ED:
                // ED xx nn nn: только команды LD rr,(nn) / LD (nn),rr имеют адрес,
                // остальные коды ED занимают два байта и адреса не содержат
                return isEdMemOp(next) ? 4 : 2;
            case PREFIX_DD:
            case PREFIX_FD:
                return lengthOfIndex(next);
            default:
                return MAIN_LENGTH[code];
        }
    }

    /**
     * Возвращает длину команды с префиксом DD/FD по коду операции после префикса.
     *
     * @param bt1 код операции после префикса
     * @return длина команды в байтах
     */
    private static int lengthOfIndex(final int bt1) {
        switch (bt1) {
            case PREFIX_CB: // DD CB d op / FD CB d op
                return INDEX_CB_LENGTH;
            case 0x21: case 0x22: case 0x2A: // LD IX,nn / LD (nn),IX / LD IX,(nn)
            case 0x36:                       // LD (IX+d),n
                return 4;
            // HALT — единственный код в поле назначения LD (IX+d),r, у которого нет смещения.
            // Эмулятор читает его как один байт после префикса, и дизассемблер обязан
            // показывать ту же длину, иначе таблица отладчика разъедется с исполнением.
            case 0x76:
                return INDEX_LENGTH;
            default:
                return usesIndexDisplacement(bt1) ? 3 : INDEX_LENGTH;
        }
    }

    /**
     * Дизассемблирует команду.
     *
     * @param code код операции
     * @param bt1  байт 1
     * @param bt2  байт 2
     * @param bt3  байт 3
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmCmd(final int code,
                                          final int bt1,
                                          final int bt2,
                                          final int bt3) {

        final int len = lengthOf(code, bt1);
        switch (code) {
            case PREFIX_CB:
                return disAsmCB(len, bt1);
            case PREFIX_DD:
                return disAsmIndex(len, bt1, bt2, bt3, true );
            case PREFIX_FD:
                return disAsmIndex(len, bt1, bt2, bt3, false);
            case PREFIX_ED:
                return disAsmED(len, bt1, bt2, bt3);
            default:
                return disAsmMain(code, bt1, bt2);
        }
    }

    /**
     * Дизассемблирует команду основного набора Z80.
     *
     * @param code код операции
     * @param bt1  байт 1
     * @param bt2  байт 2
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmMain(final int code, final int bt1, final int bt2) {
        final int    len  = MAIN_LENGTH[code];
        final String cond = CONDS[(code >> 3) & 7];
        // Байт данных идущий после кода операции
        final String data = hex8(bt1);
        // 16-битное значение, записанное в команде
        final String addr = hex16(bt2, bt1);
        final String text;

        switch (code) {
            case 0x00: text = "NOP"; break;
            case 0x01: case 0x11: case 0x21: case 0x31:
                text = "LD   " + PAIRS_HL[(code >> 4) & 3] + "," + addr;
                break;
            case 0x02: text = "LD   (BC),A"; break;
            case 0x12: text = "LD   (DE),A"; break;
            case 0x22: text = "LD   (" + addr + "),HL"; break;
            case 0x32: text = "LD   (" + addr + "),A"; break;
            case 0x0A: text = "LD   A,(BC)"; break;
            case 0x1A: text = "LD   A,(DE)"; break;
            case 0x2A: text = "LD   HL,(" + addr + ")"; break;
            case 0x3A: text = "LD   A,(" + addr + ")"; break;
            case 0x03: case 0x13: case 0x23: case 0x33:
                text = "INC  " + PAIRS_HL[(code >> 4) & 3];
                break;
            case 0x0B: case 0x1B: case 0x2B: case 0x3B:
                text = "DEC  " + PAIRS_HL[(code >> 4) & 3];
                break;
            case 0x09: case 0x19: case 0x29: case 0x39:
                text = "ADD  HL," + PAIRS_HL[(code >> 4) & 3];
                break;
            case 0x04: case 0x0C: case 0x14: case 0x1C:
            case 0x24: case 0x2C: case 0x34: case 0x3C:
                text = "INC  " + REGS[(code >> 3) & 7];
                break;
            case 0x05: case 0x0D: case 0x15: case 0x1D:
            case 0x25: case 0x2D: case 0x35: case 0x3D:
                text = "DEC  " + REGS[(code >> 3) & 7];
                break;
            case 0x06: case 0x0E: case 0x16: case 0x1E:
            case 0x26: case 0x2E: case 0x36: case 0x3E:
                text = "LD   " + REGS[(code >> 3) & 7] + "," + data;
                break;
            case 0x07: text = "RLCA"; break;
            case 0x0F: text = "RRCA"; break;
            case 0x17: text = "RLA";  break;
            case 0x1F: text = "RRA";  break;
            case 0x27: text = "DAA";  break;
            case 0x2F: text = "CPL";  break;
            case 0x37: text = "SCF";  break;
            case 0x3F: text = "CCF";  break;
            case 0x08: text = "EX   AF,AF'"; break;
            case 0x10: text = "DJNZ " + data; break;
            case 0x18: text = "JR   " + data; break;
            case 0x20: text = "JR   NZ," + data; break;
            case 0x28: text = "JR   Z,"  + data; break;
            case 0x30: text = "JR   NC," + data; break;
            case 0x38: text = "JR   C,"  + data; break;

            case 0x76: text = "HALT"; break;
            // LD r,r'
            default:
                text = disAsmMainHigh(code, cond, data, addr);
                break;
        }
        return new DisAsmResult(len, text, isJmpMain(code), isCallMain(code));
    }

    /**
     * Дизассемблирует команды основного набора с кодами 0x40..0xFF кроме LD r,n.
     *
     * @param code код операции
     * @param cond код условия
     * @param data байт данных
     * @param addr 16-битное значение данных
     * @return текст команды
     */
    @NotNull
    private static String disAsmMainHigh(final int code,
                                         final String cond,
                                         final String data,
                                         final String addr) {

        if ((code >= 0x40) && (code < 0x80) && (code != 0x76)) {
            return "LD   " + REGS[(code >> 3) & 7] + "," + REGS[code & 7];
        }
        if ((code >= 0x80) && (code < 0xC0)) {
            return ALU[(code >> 3) & 7] + REGS[code & 7];
        }
        switch (code) {
            case 0xC0: case 0xC8: case 0xD0: case 0xD8:
            case 0xE0: case 0xE8: case 0xF0: case 0xF8:
                return "RET  " + cond;
            case 0xC7: case 0xCF: case 0xD7: case 0xDF:
            case 0xE7: case 0xEF: case 0xF7: case 0xFF:
                return "RST  " + RST[(code >> 3) & 7];
            case 0xC1: case 0xD1: case 0xE1: case 0xF1:
                return "POP  " + PAIRS_STACK[(code >> 4) & 3];
            case 0xC5: case 0xD5: case 0xE5: case 0xF5:
                return "PUSH " + PAIRS_STACK[(code >> 4) & 3];
            case 0xC2: case 0xCA: case 0xD2: case 0xDA:
            case 0xE2: case 0xEA: case 0xF2: case 0xFA:
                return "JP   " + cond + "," + addr;
            case 0xC4: case 0xCC: case 0xD4: case 0xDC:
            case 0xE4: case 0xEC: case 0xF4: case 0xFC:
                return "CALL " + cond + "," + addr;
            case 0xC3: return "JP   " + addr;
            case 0xC9: return "RET";
            case 0xCD: return "CALL " + addr;
            case 0xE9: return "JP   (HL)";
            case 0xC6: case 0xCE: case 0xD6: case 0xDE:
            case 0xE6: case 0xEE: case 0xF6: case 0xFE:
                return ALU[(code >> 3) & 7] + data;
            case 0xD3: return "OUT  (" + data + "),A";
            case 0xDB: return "IN   A,(" + data + ")";
            case 0xD9: return "EXX";
            case 0xE3: return "EX   (SP),HL";
            case 0xEB: return "EX   DE,HL";
            case 0xF3: return "DI";
            case 0xFB: return "EI";
            case 0xF9: return "LD   SP,HL";
            default:   return "NOP";
        }
    }

    /**
     * Дизассемблирует команды с префиксом CB.
     *
     * @param len длина команды
     * @param bt1 байт 1 (код операции после префикса)
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmCB(final int len, final int bt1) {
        if (bt1 < 0) {
            return new DisAsmResult(len, "CB", false, false);
        }
        final int block = bt1 >> 6;
        // Номер бита кодируется в битах 5-3, но занимает только биты 2-0 после сдвига
        final int bit   = (bt1 >> 3) & 7;
        final String reg = REGS[bt1 & 7];
        if (block == 0) {
            return new DisAsmResult(len, CB_OPS[bit] + reg, false, false);
        }
        return new DisAsmResult(len, CB_BIT_OPS[block - 1] + bit + "," + reg, false, false);
    }

    /**
     * Дизассемблирует команды с префиксом DD/FD.
     *
     * @param len  длина команды по префиксу
     * @param bt1  байт 1 (код операции после префикса)
     * @param bt2  байт 2 (смещение или младший байт адреса)
     * @param bt3  байт 3 (код операции после DD CB или старший байт адреса)
     * @param isIX true = префикс DD (IX), false = префикс FD (IY)
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmIndex(final int len,
                                            final int bt1,
                                            final int bt2,
                                            final int bt3,
                                            final boolean isIX) {

        final String name = isIX ? "DD" : "FD";
        final String pair = isIX ? "IX" : "IY";
        if (bt1 < 0) {
            return new DisAsmResult(len, name, false, false);
        }
        if (bt1 == PREFIX_CB) {
            return disAsmIndexCB(INDEX_CB_LENGTH, bt2, bt3, pair);
        }
        final String text;
        // Смещение (IX+d)/(IY+d) - знаковый байт
        final String disp = hexDisp(bt2);
        // 16-битное значение, записанное в команде
        final String addr = hex16(bt3, bt2);
        final int cmdLen = lengthOfIndex(bt1);
        switch (bt1) {
            case 0x09: text = "ADD  " + pair + ",BC"; break;
            case 0x19: text = "ADD  " + pair + ",DE"; break;
            case 0x29: text = "ADD  " + pair + "," + pair; break;
            case 0x39: text = "ADD  " + pair + ",SP"; break;
            case 0x21: text = "LD   " + pair + "," + addr; break;
            case 0x22: text = "LD   ("  + addr + ")," + pair; break;
            case 0x2A: text = "LD   "   + pair + ",(" + addr + ")"; break;
            case 0x23: text = "INC  " + pair; break;
            case 0x2B: text = "DEC  " + pair; break;
            case 0x34: text = "INC  (" + pair + disp + ")"; break;
            case 0x35: text = "DEC  (" + pair + disp + ")"; break;
            case 0x36: text = "LD   (" + pair + disp + ")," + hex8(bt3); break;
            case 0xE1: text = "POP  " + pair; break;
            case 0xE3: text = "EX   (SP)," + pair; break;
            case 0xE5: text = "PUSH " + pair; break;
            case 0xE9: text = "JP   (" + pair + ")"; break;
            case 0xF9: text = "LD   SP," + pair; break;
            // LD (IX+d),r: записывается обычный регистр, включая H и L
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x77:
                text = "LD   (" + pair + disp + ")," + REGS[bt1 & 7];
                break;
            // ED после префикса DD/FD выполняется как NOP, префикс не действует
            case PREFIX_ED: text = "ED"; break;
            default:
                text = disAsmIndexReg(bt1, bt2, bt3, isIX);
                break;
        }
        return new DisAsmResult(cmdLen, text, bt1 == 0xE9, false);
    }

    /**
     * Проверяет, содержит ли команда DD/FD байт смещения для (IX+d)/(IY+d)
     * либо байт данных для недокументированного LD IXH/IXL/IYH/IYL,n.
     *
     * @param bt1 код операции после префикса
     * @return true = команда содержит байт смещения или данных
     */
    private static boolean usesIndexDisplacement(final int bt1) {
        // LD H,n / LD L,n — при индексном префиксе это LD IXH,n / LD IXL,n
        if ((bt1 == 0x26) || (bt1 == 0x2E)) {
            return true;
        }
        // LD (IX+d),r — поле назначения в битах 5-3 равно 6; проверяется раньше,
        // потому что поле r в битах 2-0 при этом может быть любым
        if ((bt1 >= 0x70) && (bt1 <= 0x77)) {
            return true;
        }
        // LD r,(IX+d) — поле r в битах 2-0 равно 6
        if ((bt1 >= 0x40) && (bt1 < 0x80)) {
            return (bt1 & 7) == 6;
        }
        // ALU A,(IX+d) — поле r в битах 2-0 равно 6
        if ((bt1 >= 0x80) && (bt1 < 0xC0)) {
            return (bt1 & 7) == 6;
        }
        // INC (IX+d) / DEC (IX+d): поле r в битах 5-3
        return (bt1 == 0x34) || (bt1 == 0x35);
    }

    /**
     * Проверяет, содержит ли команда ED 16-битный адрес (4 байта вместе с префиксом).
     *
     * @param bt1 код операции после префикса
     * @return true = команда содержит 16-битный адрес
     */
    private static boolean isEdMemOp(final int bt1) {
        return ((bt1 >= 0x43) && (bt1 <= 0x73) && ((bt1 & 0x07) == 3));
    }

    /**
     * Дизассемблирует команды с префиксом DD/FD, работающие с 8-битными регистрами
     * IXH/IXL/IYH/IYL или с ячейкой (IX+d)/(IY+d), а также команды, на которые
     * индексный префикс не влияет (они выполняются как обычные).
     *
     * @param bt1  код операции после префикса
     * @param bt2  байт смещения
     * @param bt3  байт за смещением (для команд, на которые префикс не влияет)
     * @param isIX true = префикс DD (IX), false = префикс FD (IY)
     * @return текст команды
     */
    @NotNull
    private static String disAsmIndexReg(final int bt1, final int bt2, final int bt3, final boolean isIX) {
        final String[] regs = isIX ? REGS_IX : REGS_IY;
        if (bt1 == 0x76) { // HALT — префикс на него не влияет
            return "HALT";
        }
        if ((bt1 >= 0x40) && (bt1 < 0x80)) {
            if ((bt1 & 7) == 6) {
                // LD r,(IX+d): записывается обычный регистр, включая H и L
                // (IXH/IXL сюда не попадают — так же работает и эмулятор)
                return "LD   " + REGS[(bt1 >> 3) & 7] + "," + cell(isIX, bt2);
            }
            // LD r,r': подменяется только сторона, кодирующая H или L
            return "LD   " + halfName(isIX, bt1 >> 3) + "," + halfName(isIX, bt1);
        }
        if ((bt1 >= 0x80) && (bt1 < 0xC0)) {
            return ALU[(bt1 >> 3) & 7] + halfName(isIX, bt1);
        }
        if ((bt1 == 0x26) || (bt1 == 0x2E)) {
            // LD IXH,n / LD IXL,n — байт данных стоит на месте смещения
            return "LD   " + regs[(bt1 >> 3) & 7] + "," + hex8(bt2);
        }
        // Остальные команды не используют индексный регистр: префикс для них — NOP,
        // но операнды (байт данных или 16-битный адрес) остаются у команды
        return disAsmMain(bt1, bt2, bt3).getText();
    }

    /**
     * Возвращает имя 8-битного регистра для команды с индексным префиксом:
     * поле 4 (H) и 5 (L) кодируют IXH/IXL (IYH/IYL), остальные имена без изменений.
     *
     * @param isIX true = префикс DD (IX), false = префикс FD (IY)
     * @param r    поле регистра (0..7)
     * @return имя регистра
     */
    private static String halfName(final boolean isIX, final int r) {
        final int reg = r & 7;
        if ((reg == 4) || (reg == 5)) {
            return (isIX ? "IX" : "IY") + ((reg == 4) ? "H" : "L");
        }
        return REGS[reg];
    }

    /**
     * Возвращает имя ячейки памяти (IX+d)/(IY+d).
     *
     * @param isIX true = префикс DD (IX), false = префикс FD (IY)
     * @param disp байт смещения
     * @return текст ячейки памяти
     */
    private static String cell(final boolean isIX, final int disp) {
        return "(" + (isIX ? "IX" : "IY") + hexDisp(disp) + ")";
    }

    /**
     * Дизассемблирует команды с префиксом DD CB / FD CB.
     *
     * @param len  длина команды
     * @param bt2  байт смещения
     * @param bt3  код операции после префикса CB
     * @param pair имя индексного регистра
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmIndexCB(final int len, final int bt2, final int bt3, final String pair) {
        if (bt3 < 0) {
            return new DisAsmResult(len, "CB", false, false);
        }
        final int block =  bt3 >> 6;
        final int bit   = (bt3 >> 3) & 7;
        final int reg   = bt3 & 7;
        final String cell = "(" + pair + hexDisp(bt2) + ")";
        // Для DD CB / FD CB поле r=6 кодирует (IX+d)/(IY+d): регистр в мнемонике не выводится
        final String[] regs = { "B", "C", "D", "E", "H", "L", null, "A" };
        final String suffix = (reg == 6) ? "" : "," + regs[reg];
        final String text;
        if (block == 1) {
            // BIT b,(IX+d) - результат проверки бита не сохраняется в регистре
            text = "BIT  " + bit + "," + cell;
        } else if (block == 0) {
            text =     CB_OPS[bit      ] + cell + suffix;
        } else {
            text = CB_BIT_OPS[block - 1] + bit  + "," + cell + suffix;
        }
        return new DisAsmResult(len, text, false, false);
    }

    /**
     * Дизассемблирует команды с префиксом ED.
     *
     * @param len длина команды
     * @param bt1 байт 1 (код операции после префикса)
     * @param bt2 байт 2 (младший байт адреса)
     * @param bt3 байт 3 (старший байт адреса)
     * @return результат дизассемблирования
     */
    @NotNull
    private static DisAsmResult disAsmED(final int len, final int bt1, final int bt2, final int bt3) {
        if (bt1 < 0) {
            return new DisAsmResult(len, "ED", false, false);
        }
        final String text;
        final boolean isJmp;
        final String pair = PAIRS_HL[(bt1 >> 4) & 3];
        final String addr = hex16(bt3, bt2);
        // Код операции ED кодирует пару в битах 5-4
        switch (bt1) {
            case 0x40: case 0x48: case 0x50: case 0x58: // IN r,(C), r = 6 — недокументированное IN F,(C)
            case 0x60: case 0x68: case 0x70: case 0x78: {
                final String reg = ED_REGS[(bt1 >> 3) & 7];
                text  = "IN   " + ((reg == null) ? "F" : reg) + ",(C)";
                isJmp = false;
                break;
            }

            case 0x41: case 0x49: case 0x51: case 0x59: // OUT (C),r, r = 6 — недокументированное OUT (C),0
            case 0x61: case 0x69: case 0x71: case 0x79: {
                final String reg = ED_REGS[(bt1 >> 3) & 7];
                text  = "OUT  (C)," + ((reg == null) ? "0" : reg);
                isJmp = false;
                break;
            }

            case 0x44: case 0x4C: case 0x54: case 0x5C:
            case 0x64: case 0x6C: case 0x74: case 0x7C:
                text  = "NEG";
                isJmp = false;
                break;

            // RETN (ED 45/4D/55/5D) и RETI (ED 65/6D/75/7D — те же коды с установленным битом 3)
            case 0x45: case 0x4D: case 0x55: case 0x5D:
                text  = "RETN";
                isJmp = true;
                break;

            case 0x65: case 0x6D: case 0x75: case 0x7D:
                text  = "RETI";
                isJmp = true;
                break;

            // IM 0 / IM 0/1 / IM 1 / IM 2. Бит 3 (RETN/RETI) и бит 4 (пара) на режим
            // не влияют, поэтому IM 0 повторяется как ED 46 и ED 66, IM 0/1 — как 4E и 6E,
            // IM 1 — как 56 и 76, IM 2 — как 5E и 7E
            case 0x46: case 0x66:
                text  = ED_IM[0];
                isJmp = false;
                break;

            case 0x4E: case 0x6E:
                text  = ED_IM[1];
                isJmp = false;
                break;

            case 0x56: case 0x76:
                text  = ED_IM[2];
                isJmp = false;
                break;

            case 0x5E: case 0x7E:
                text  = ED_IM[3];
                isJmp = false;
                break;

            case 0x47:
                text  = "LD   I,A";
                isJmp = false;
                break;

            case 0x4F:
                text  = "LD   R,A";
                isJmp = false;
                break;

            case 0x57:
                text  = "LD   A,I";
                isJmp = false;
                break;

            case 0x5F:
                text  = "LD   A,R";
                isJmp = false;
                break;

            case 0x67:
                text  = "RRD";
                isJmp = false;
                break;

            case 0x6F:
                text  = "RLD";
                isJmp = false;
                break;

            default:
                text  = disAsmEDMem(bt1, pair, addr);
                isJmp = false;
                break;
        }
        return new DisAsmResult(len, text, isJmp, false);
    }

    /**
     * Дизассемблирует команды ED с обращением к памяти и 16-битной арифметикой.
     *
     * @param bt1  код операции после префикса
     * @param pair имя 16-битной регистровой пары
     * @param addr 16-битное значение данных
     * @return текст команды
     */
    @NotNull
    private static String disAsmEDMem(final int bt1, final String pair, final String addr) {
        switch (bt1) {
            case 0x43: return "LD   (" + addr + ")," + pair;
            case 0x4B: return "LD   " + pair + ",(" + addr + ")";
            case 0x53: return "LD   (" + addr + "),DE";
            case 0x5B: return "LD   DE,(" + addr + ")";
            case 0x63: return "LD   (" + addr + "),HL";
            case 0x6B: return "LD   HL,(" + addr + ")";
            case 0x73: return "LD   (" + addr + "),SP";
            case 0x7B: return "LD   SP,(" + addr + ")";
            // ADC HL,ss / SBC HL,ss — пара кодируется битами 5-4
            case 0x4A: case 0x5A: case 0x6A: case 0x7A: return "ADC  HL," + pair;
            case 0x42: case 0x52: case 0x62: case 0x72: return "SBC  HL," + pair;
            default:
                break;
        }
        if ((bt1 >= 0xA0) && (bt1 <= 0xBB)) {
            final String block = ED_BLOCK[bt1 & 7];
            return ((bt1 & 8) == 0) ? block : block.concat("R");
        }
        // ED 77 и ED 7F — недокументированные дубликаты NEG
        if ((bt1 == 0x77) || (bt1 == 0x7F)) {
            return "NEG";
        }
        // Остальные коды ED реальный Z80 выполняет как NOP длиной два байта
        return "NOP";
    }

    /**
     * Формирует знаковое смещение (IX+d)/(IY+d): {@code +05}, {@code -02}.
     *
     * @param value байт смещения
     * @return знаковое смещение или пустая строка, если байт не прочитан
     */
    @NotNull
    private static String hexDisp(final int value) {
        if (value < 0) {
            return "";
        }
        final int disp = (byte) value;
        return String.format("%s%02X", (disp < 0) ? "-" : "+", Math.abs(disp));
    }

    /**
     * Проверяет, является ли команда основного набора командой передачи управления.
     *
     * @param code код операции
     * @return true = команда передачи управления
     */
    private static boolean isJmpMain(final int code) {
        switch (code) {
            case 0x10: case 0x18: case 0x20: case 0x28: case 0x30: case 0x38:
            case 0xC0: case 0xC8: case 0xD0: case 0xD8: case 0xE0: case 0xE8: case 0xF0: case 0xF8:
            case 0xC2: case 0xCA: case 0xD2: case 0xDA: case 0xE2: case 0xEA: case 0xF2: case 0xFA:
            case 0xC3: case 0xC9: case 0xE9:
                return true;
            default:
                return false;
        }
    }

    /**
     * Проверяет, является ли команда основного набора командой вызова подпрограммы.
     *
     * @param code код операции
     * @return true = команда вызова подпрограммы
     */
    private static boolean isCallMain(final int code) {
        switch (code) {
            case 0xC4: case 0xCC: case 0xD4: case 0xDC:
            case 0xE4: case 0xEC: case 0xF4: case 0xFC:
            case 0xCD:
            case 0xC7: case 0xCF: case 0xD7: case 0xDF:
            case 0xE7: case 0xEF: case 0xF7: case 0xFF:
                return true;
            default:
                return false;
        }
    }

    /**
     * Формирует таблицу длин команд основного набора Z80.
     *
     * @return таблица длин команд
     */
    private static int[] buildMainLength() {
        final int[] length = new int[0x100];
        Arrays.fill(length, 1);
        // LD r,n
        for (int code = 0x06; code <= 0x3E; code += 0x08) {
            length[code] = 2;
        }
        // LD rr,nn и LD (nn),rr / LD rr,(nn)
        length[0x01] = length[0x11] = length[0x21] = length[0x31] = 3;
        length[0x22] = length[0x2A] = length[0x32] = length[0x3A] = 3;
        // JR и DJNZ
        length[0x10] = length[0x18] = 2;
        length[0x20] = length[0x28] = length[0x30] = length[0x38] = 2;
        // Условные переходы JP cc,nn
        for (int code = 0xC2; code <= 0xFA; code += 0x08) {
            length[code] = 3;
        }
        // Безусловный переход и вызовы CALL nn / CALL cc,nn
        length[0xC3] = 3;
        for (int code = 0xC4; code <= 0xFC; code += 0x08) {
            length[code] = 3;
        }
        length[0xCD] = 3;
        // ALU A,n
        for (int code = 0xC6; code <= 0xFE; code += 0x08) {
            length[code] = 2;
        }
        // IN A,(n) и OUT (n),A
        length[0xD3] = length[0xDB] = 2;
        return length;
    }

    @Override
    public int getMaxCmdLength() {
        return MAX_CMD_LENGTH;
    }

    @Override
    @NotNull
    public String getMaxCmdText() {
        return "LD   (IX-80),(IX-80)";
    }

    @Override
    @NotNull
    public DisAsmResult disAsm(final int address, final int[] bytes) {
        final int code = bytes[0] & 0xFF;
        final int bt1  = byteAt(bytes, 1);
        final int bt2  = byteAt(bytes, 2);
        final int bt3  = byteAt(bytes, 3);

        return disAsmCmd(code, bt1, bt2, bt3);
    }
}

