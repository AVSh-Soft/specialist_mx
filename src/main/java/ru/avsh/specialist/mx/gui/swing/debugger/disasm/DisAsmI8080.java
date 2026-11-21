package ru.avsh.specialist.mx.gui.swing.debugger.disasm;

import org.jetbrains.annotations.NotNull;

import static ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmResult.*;

/**
 * Класс "Дизассемблер процессора Intel 8080A (КР580ВМ80А)".
 * Команда состоит из кода операции и не более чем двух байтов данных,
 * которые идут в порядке младший-старший.
 */
public final class DisAsmI8080 implements DisAsm {
    private static final int MAX_CMD_LENGTH = 3;

    // Имена 8-битных регистров в кодировке команды
    private static final String[] REGS  = { "B", "C", "D", "E", "H", "L", "M", "A" };
    // Коды условий в кодировке команды
    private static final String[] CONDS = { "NZ", "Z", "NC", "C", "PO", "PE", "P", "M" };
    // Мнемоники АЛУ в кодировке команды (поле кода операции)
    private static final String[] ALU   = { "ADD  A,", "ADC  A,", "SUB  ", "SBB  A,", "ANA  ", "XRA  ", "ORA  ", "CMP  " };
    // Имена 16-битных регистровых пар в кодировке команды
    private static final String[] PAIRS = { "B", "D", "H", "SP" };

    // Длина команды процессора Intel 8080A (КР580ВМ80А)
    private static final int[] CMD_LEN = {
            1, 3, 1, 1, 1, 1, 2, 1,
            1, 1, 1, 1, 1, 1, 2, 1,

            1, 3, 1, 1, 1, 1, 2, 1,
            1, 1, 1, 1, 1, 1, 2, 1,

            1, 3, 3, 1, 1, 1, 2, 1,
            1, 1, 3, 1, 1, 1, 2, 1,

            1, 3, 3, 1, 1, 1, 2, 1,
            1, 1, 3, 1, 1, 1, 2, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,

            1, 1, 3, 3, 3, 1, 2, 1,
            1, 1, 3, 3, 3, 3, 2, 1,

            1, 1, 3, 2, 3, 1, 2, 1,
            1, 1, 3, 2, 3, 3, 2, 1,

            1, 1, 3, 1, 3, 1, 2, 1,
            1, 1, 3, 1, 3, 3, 2, 1,

            1, 1, 3, 1, 3, 1, 2, 1,
            1, 1, 3, 1, 3, 3, 2, 1
    };

    /**
     * Возвращает длину команды процессора I8080 по коду операции.
     *
     * @param opCode код операции
     * @return длина команды в байтах
     */
    public static int getCmdLength(final int opCode) {
        return CMD_LEN[opCode & 0xFF];
    }

    /**
     * Формирует текст команды I8080.
     *
     * @param code код операции
     * @param bt1  байт 1 команды (EMPTY/WRONG, если не прочитан)
     * @param bt2  байт 2 команды (EMPTY/WRONG, если не прочитан)
     * @return текст команды
     */
    @NotNull
    private static String text(final int code, final int bt1, final int bt2) {
        switch (code) {
            case 0x00:
                return "NOP";
            // Недокументированные NOP: 8080 выполняет их как обычный NOP,
            // поэтому в дизассемблере они показываются строчными
            case 0x08: case 0x10: case 0x18:
            case 0x20: case 0x28: case 0x30: case 0x38:
                return "nop";
            case 0x01: case 0x11: case 0x21: case 0x31:
                return "LXI  " + PAIRS[(code >> 4) & 3] + "," + hex16(bt2, bt1);
            case 0x02: return "STAX B";
            case 0x12: return "STAX D";
            case 0x22: return "SHLD " + hex16(bt2, bt1);
            case 0x32: return "STA  " + hex16(bt2, bt1);
            case 0x0A: return "LDAX B";
            case 0x1A: return "LDAX D";
            case 0x2A: return "LHLD " + hex16(bt2, bt1);
            case 0x3A: return "LDA  " + hex16(bt2, bt1);
            case 0x03: case 0x13: case 0x23: case 0x33:
                return "INX  " + PAIRS[(code >> 4) & 3];
            case 0x0B: case 0x1B: case 0x2B: case 0x3B:
                return "DCX  " + PAIRS[(code >> 4) & 3];
            case 0x09: case 0x19: case 0x29: case 0x39:
                return "DAD  " + PAIRS[(code >> 4) & 3];
            case 0x04: case 0x0C: case 0x14: case 0x1C:
            case 0x24: case 0x2C: case 0x34: case 0x3C:
                return "INR  " + REGS[(code >> 3) & 7];
            case 0x05: case 0x0D: case 0x15: case 0x1D:
            case 0x25: case 0x2D: case 0x35: case 0x3D:
                return "DCR  " + REGS[(code >> 3) & 7];
            case 0x06: case 0x0E: case 0x16: case 0x1E:
            case 0x26: case 0x2E: case 0x36: case 0x3E:
                return "MVI  " + REGS[(code >> 3) & 7] + "," + hex8(bt1);
            case 0x07: return "RLC";
            case 0x0F: return "RRC";
            case 0x17: return "RAL";
            case 0x1F: return "RAR";
            case 0x27: return "DAA";
            case 0x2F: return "CMA";
            case 0x37: return "STC";
            case 0x3F: return "CMC";
            default:
                return textHigh(code, bt1, bt2);
        }
    }

    /**
     * Формирует текст команды для кодов 0x40..0xFF.
     *
     * @param code код операции
     * @param bt1  байт 1 команды
     * @param bt2  байт 2 команды
     * @return текст команды
     */
    @NotNull
    private static String textHigh(final int code, final int bt1, final int bt2) {
        final String cond  = CONDS[(code >> 3) & 7];
        final String value = hex16(bt2, bt1);
        // MOV r,r'
        if ((code >= 0x40) && (code <= 0x7F)) {
            return (code == 0x76) ? "HLT" : "MOV  " + REGS[(code >> 3) & 7] + "," + REGS[code & 7];
        }
        // ALU A,r
        if ((code >= 0x80) && (code <= 0xBF)) {
            return ALU[(code >> 3) & 7] + REGS[code & 7];
        }
        switch (code) {
            case 0xC0: case 0xC8: case 0xD0: case 0xD8:
            case 0xE0: case 0xE8: case 0xF0: case 0xF8:
                return "R" + cond;
            case 0xC7: case 0xCF: case 0xD7: case 0xDF:
            case 0xE7: case 0xEF: case 0xF7: case 0xFF:
                return "RST  " + ((code >> 3) & 7);
            case 0xC1: case 0xD1: case 0xE1:
                return "POP  " + PAIRS[(code >> 4) & 3];
            case 0xF1: return "POP  PSW";
            case 0xC5: case 0xD5: case 0xE5:
                return "PUSH " + PAIRS[(code >> 4) & 3];
            case 0xF5: return "PUSH PSW";
            case 0xC2: case 0xCA: case 0xD2: case 0xDA:
            case 0xE2: case 0xEA: case 0xF2: case 0xFA:
                return "J"   + cond + " "  + value;
            case 0xC4: case 0xCC: case 0xD4: case 0xDC:
            case 0xE4: case 0xEC: case 0xF4: case 0xFC:
                return "C"   + cond + " "  + value;
            case 0xC3: return "JMP  " + value;
            case 0xCB: return "jmp  " + value;
            case 0xC9: return "RET";
            case 0xD9: return "ret";
            case 0xCD: return "CALL " + value;
            case 0xDD: case 0xED: case 0xFD:
                return "call " + value;
            case 0xC6: case 0xCE: case 0xD6: case 0xDE:
            case 0xE6: case 0xEE: case 0xF6: case 0xFE:
                return ALU[(code >> 3) & 7] + hex8(bt1) + "H";
            case 0xD3: return "OUT  " + hex8(bt1) + "H";
            case 0xDB: return "IN   " + hex8(bt1) + "H";
            case 0xE3: return "XTHL";
            case 0xE9: return "PCHL";
            case 0xEB: return "XCHG";
            case 0xF3: return "DI"  ;
            case 0xFB: return "EI"  ;
            case 0xF9: return "SPHL";
            default:   return "NOP" ;
        }
    }

    /**
     * Проверяет, является ли код операции командой передачи управления.
     *
     * @param opCode код операции
     * @return true = команда передачи управления
     */
    private static boolean isJmpOpCode(final int opCode) {
        switch (opCode) {
            case 0xC3: case 0xCB:
            case 0xC2: case 0xCA: case 0xD2: case 0xDA:
            case 0xE2: case 0xEA: case 0xF2: case 0xFA:
                return true;
            default:
                return false;
        }
    }

    /**
     * Проверяет, является ли код операции командой вызова подпрограммы.
     *
     * @param opCode код операции
     * @return true = команда вызова подпрограммы
     */
    private static boolean isCallOpCode(final int opCode) {
        switch (opCode) {
            case 0xCD: case 0xDD: case 0xED: case 0xFD:
            case 0xC4: case 0xCC: case 0xD4: case 0xDC:
            case 0xE4: case 0xEC: case 0xF4: case 0xFC:
            case 0xC7: case 0xCF: case 0xD7: case 0xDF:
            case 0xE7: case 0xEF: case 0xF7: case 0xFF:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getMaxCmdLength() {
        return MAX_CMD_LENGTH;
    }

    @Override
    @NotNull
    public String getMaxCmdText() {
        return "LXI  SP,FFFF";
    }

    @Override
    @NotNull
    public DisAsmResult disAsm(final int address, final int[] bytes) {
        final int code   = bytes[0] & 0xFF;
        final int len    = CMD_LEN[code];
        final int bt1    = byteAt(bytes, 1);
        final int bt2    = byteAt(bytes, 2);
        final String txt =   text(code, bt1, bt2);

        return new DisAsmResult(len, txt, isJmpOpCode(code), isCallOpCode(code));
    }
}
