package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс "Процессор Intel C8080A (К580ВМ80А)".
 * @author -=AVSh=-
 */
final class ProcessorI8080 implements IClockedDevice {
    // Количество тактов для каждой команды CPU
    private static final int[] CYCLES = {
                 4, 10,      7,  5,      5,  5,  7,  4,      4, 10,      7,  5,      5,  5,  7,  4,
                 4, 10,      7,  5,      5,  5,  7,  4,      4, 10,      7,  5,      5,  5,  7,  4,
                 4, 10,     16,  5,      5,  5,  7,  4,      4, 10,     16,  5,      5,  5,  7,  4,
                 4, 10,     13,  5,     10, 10, 10,  4,      4, 10,     13,  5,      5,  5,  7,  4,
                 5,  5,      5,  5,      5,  5,  7,  5,      5,  5,      5,  5,      5,  5,  7,  5,
                 5,  5,      5,  5,      5,  5,  7,  5,      5,  5,      5,  5,      5,  5,  7,  5,
                 5,  5,      5,  5,      5,  5,  7,  5,      5,  5,      5,  5,      5,  5,  7,  5,
                 7,  7,      7,  7,      7,  7,  7,  7,      5,  5,      5,  5,      5,  5,  7,  5,
                 4,  4,      4,  4,      4,  4,  7,  4,      4,  4,      4,  4,      4,  4,  7,  4,
                 4,  4,      4,  4,      4,  4,  7,  4,      4,  4,      4,  4,      4,  4,  7,  4,
                 4,  4,      4,  4,      4,  4,  7,  4,      4,  4,      4,  4,      4,  4,  7,  4,
                 4,  4,      4,  4,      4,  4,  7,  4,      4,  4,      4,  4,      4,  4,  7,  4,
            0x0B05, 10, 0x0A0A, 10, 0x110B, 11,  7, 11, 0x0B05, 10, 0x0A0A, 10, 0x110B, 17,  7, 11,
            0x0B05, 10, 0x0A0A, 10, 0x110B, 11,  7, 11, 0x0B05, 10, 0x0A0A, 10, 0x110B, 17,  7, 11,
            0x0B05, 10, 0x0A0A, 18, 0x110B, 11,  7, 11, 0x0B05,  5, 0x0A0A,  4, 0x110B, 17,  7, 11,
            0x0B05, 10, 0x0A0A,  4, 0x110B, 11,  7, 11, 0x0B05,  5, 0x0A0A,  4, 0x110B, 17,  7, 11
    };

    // Индексы регистров CPU в массиве fRegs (здесь без enum - для скорости)
    private static final int B  = 0;
    private static final int C  = 1;
    private static final int D  = 2;
    private static final int E  = 3;
    private static final int H  = 4;
    private static final int L  = 5;
    private static final int F  = 6;
    private static final int A  = 7;
    private static final int SP = 8;
    private static final int PC = 9;
    // Ячейка памяти M(HL)
    private static final int M  = 6;

    // Индексы регистровых пар для методов getValRegPair()/setValRegPair() (здесь без enum - для скорости)
    private static final int P_BC  = 0;
    private static final int P_DE  = 2;
    private static final int P_HL  = 4;
    private static final int P_PSW = 6;

    // Регистровая пара для отладчика
    enum DebugRegPair {
        AF, BC, DE, HL, SP, PC
    }

    // Фазы установки состояния "HOLD" CPU (здесь без enum - для скорости)
    private static final int HOLD_IS_NOT_SET  = 0;
    private static final int HOLD_IN_PROCESS  = 1;
    private static final int HOLD_ACKNOWLEDGE = 2;

    private final int[] fRegs;
    private final Trap fCompareTrap;
    private final SpecialistMX fSpMX;
    private final AtomicInteger fCycles;
    private final SortedSet<Trap> fTraps;
    private final MemoryDevicesManager fMDM;
    private final MemoryDevicesManager fIoDM;

    private int fOpCode;
    private boolean fTestResult;
    private volatile int fHoldPhase;
    private volatile boolean fDebugRun;
    private volatile boolean fTrapsFlag;
    private volatile Trap fTrapStepOver;

    /**
     * Конструктор.
     *
     * @param spMX ссылка на объект класса SpecialistMX - "Компьютер 'Специалист MX'"
     * @param mDM  ссылка на объект класса MemoryDevicesManager - "Быстрый диспетчер устройств памяти" (устройства памяти)
     * @param ioDM ссылка на объект класса MemoryDevicesManager - "Быстрый диспетчер устройств памяти" (устройства ввода/вывода)
     */
    ProcessorI8080(@NotNull SpecialistMX spMX, @NotNull MemoryDevicesManager mDM, MemoryDevicesManager ioDM) {
        fSpMX = spMX;
        fMDM  =  mDM;
        fIoDM = ioDM;
                                // 0, 1, 2, 3, 4, 5, 6, 7,  8,  9
        fRegs    = new int[10]; // B, C, D, E, H, L, F, A, SP, PC
        fRegs[F] = 0b0000_0010; // Флаги по умолчанию SZ0A_0P1C

             fCycles = new AtomicInteger();
        fCompareTrap = new Trap(0,0);
              fTraps = new ConcurrentSkipListSet<>();
    }

    @Override
    public synchronized String toString() {
        return String.format("Значения регистров: SZ0A0P1C=%08d, A=%02X, B=%02X, C=%02X, D=%02X, E=%02X, H=%02X, L=%02X, SP=%04X, PC=%04X%n" +
                             "Память с адреса PC: %02X, %02X, %02X, %02X, %02X, ...",
                Integer.parseInt(Integer.toBinaryString(fRegs[F])),
                fRegs[A], fRegs[B], fRegs[C], fRegs[D], fRegs[E], fRegs[H], fRegs[L], fRegs[SP], fRegs[PC],
                fMDM.readByte((fRegs[PC]    ) & 0xFFFF),
                fMDM.readByte((fRegs[PC] + 1) & 0xFFFF),
                fMDM.readByte((fRegs[PC] + 2) & 0xFFFF),
                fMDM.readByte((fRegs[PC] + 3) & 0xFFFF),
                fMDM.readByte((fRegs[PC] + 4) & 0xFFFF));
    }

    /**
     * Получает значение регистра CPU по коду регистра.
     *
     * @param codeReg B = 0, C = 1, D = 2, E = 3, H = 4, L = 5, M = 6, A = 7
     * @return значение регистра
     */
    private int getReg(int codeReg) {
        return codeReg == M ? fMDM.readByte(getRegPair(P_HL)) : fRegs[codeReg];
    }

    /**
     * Устанавливает значение регистра CPU по коду регистра.
     *
     * @param codeReg B = 0, C = 1, D = 2, E = 3, H = 4, L = 5, M = 6, A = 7
     * @param value   значение регистра
     */
    private void setReg(int codeReg, int value) {
        if (codeReg == M) {
            fMDM.writeByte(getRegPair(P_HL), value);
        } else {
            fRegs[codeReg] = value & 0xFF;
        }
    }

    /**
     * Получает значение регистровой пары CPU по коду пары, умноженной на 2.
     *
     * @param codePair BC = 0, DE = 2, HL = 4, PSW = 6
     * @return значение
     */
    private int getRegPair(int codePair) {
        return codePair == P_PSW ? fRegs[codePair] | (fRegs[codePair + 1] << 8) : fRegs[codePair + 1] | (fRegs[codePair] << 8);
    }

    /**
     * Устанавливает значение регистровой пары CPU по коду пары, умноженной на 2.
     *
     * @param codePair BC = 0, DE = 2, HL = 4, PSW = 6
     * @param value    значение
     */
    private void setRegPair(int codePair, int value) {
        if (codePair == P_PSW) {
            fRegs[codePair]     =  value & 0xFF;
            fRegs[codePair + 1] = (value >> 8) & 0xFF;
        } else {
            fRegs[codePair]     = (value >> 8) & 0xFF;
            fRegs[codePair + 1] =  value & 0xFF;
        }
    }

    /**
     * Получает значение флага C (Перенос).
     *
     * @return значение
     */
    private boolean getFlagC() {
        return (fRegs[F] & 1) != 0;
    }

    /**
     * Устанавливает значение флага C (Перенос).
     *
     * @param value значение
     */
    private void setFlagC(boolean value) {
        fRegs[F] = value ? fRegs[F] | 1 : fRegs[F] & 0b1111_1110;
    }

    /**
     * Получает значение флага AC (Дополнительный перенос).
     *
     * @return значение
     */
    private boolean getFlagAC() {
        return (fRegs[F] & 0b1_0000) != 0;
    }

    /**
     * Устанавливает значение флага AC (Дополнительный перенос).
     *
     * @param value значение
     */
    private void setFlagAC(boolean value) {
        fRegs[F] = value ? fRegs[F] | 0b1_0000 : fRegs[F] & 0b1110_1111;
    }

    /**
     * Устанавливает флаги S (Знак), Z (Нуль) и P (Четность).
     *
     * @param r результат вычислений в формате Byte
     */
    private void setFlagsSZP(int r) {
        fRegs[F] = (r & 0x80) != 0 ? fRegs[F] | 0b1000_0000 : fRegs[F] & 0b0111_1111; // S
        fRegs[F] =  r         == 0 ? fRegs[F] | 0b0100_0000 : fRegs[F] & 0b1011_1111; // Z
        fRegs[F] = (((       r & 1) ^
                     ((r >> 1) & 1) ^
                     ((r >> 2) & 1) ^
                     ((r >> 3) & 1) ^
                     ((r >> 4) & 1) ^
                     ((r >> 5) & 1) ^
                     ((r >> 6) & 1) ^
                     ((r >> 7) & 1)) == 0) ? fRegs[F] | 0b100 : fRegs[F] & 0b1111_1011; // P
    }

    /**
     * Складывает два/три байта с установкой всех флагов.
     *
     * @param a первый байт
     * @param b второй байт
     * @param c третий байт
     * @return результат
     */
    private int addByte(int a, int b, int c) {
        int r = a + b + c;
        setFlagC(r > 0xFF);
        setFlagAC((a & 0xF) + (b & 0xF) + (c & 0xF) > 0xF);
        r &= 0xFF;
        setFlagsSZP(r);
        return r;
    }

    /**
     * Вычитает из первого байта второй и третий байт с установкой всех флагов.
     *
     * @param a первый байт
     * @param b второй байт
     * @param c третий байт
     * @return результат
     */
    private int subByte(int a, int b, int c) {
        int r = (a - b - c) & 0xFF;
        setFlagC(a < b + c);
        setFlagAC((a & 0xF) >= (b & 0xF) + (c & 0xF)); // Тут флаг взводится наоборот
        setFlagsSZP(r);
        return r;
    }

    /**
     * Выполняет логическое умножение первого байта на второй с установкой всех флагов.
     *
     * @param a первый байт
     * @param b второй байт
     * @return результат
     */
    private int anaByte(int a, int b) {
        int r = a & b;
        setFlagC(false);
        setFlagAC(((a | b) & 0b1000) != 0);
        setFlagsSZP(r);
        return r;
    }

    /**
     * Реализует команду DAA CPU.
     */
    private void cmdDAA() {
        int s = 0;
        int a = getReg(A);
        boolean fc = getFlagC();

        if (getFlagAC() || (a & 0xF) > 9) {
            s = 6;
        }
        if (fc || (a >> 4) > 9 || ((a >> 4) >= 9 && (a & 0xF) > 9)) {
            s |= 0x60;
            fc = true;
        }
        setReg(A, addByte(a, s, 0));
        setFlagC(fc);
    }

    /**
     * Проверяет флаги по коду условия.
     *
     * @param codeCondition код условия от 0 до 7
     * @return результат проверки
     */
    private boolean testFlags(int codeCondition) {
        int f = fRegs[F];

        switch (codeCondition) {
            case 0:         //SZ0A_0P1C
                return (f & 0b0100_0000) == 0;   // NZ
            case 1:
                return (f & 0b0100_0000) != 0;   // Z
            case 2:
                return (f & 0b0000_0001) == 0;   // NC
            case 3:
                return (f & 0b0000_0001) != 0;   // C
            case 4:
                return (f & 0b0000_0100) == 0;   // PO
            case 5:
                return (f & 0b0000_0100) != 0;   // PE
            case 6:
                return (f & 0b1000_0000) == 0;   // P
            case 7:
                return (f & 0b1000_0000) != 0;   // M
            default:
                return false;
        }
    }

    /**
     * Получает значение регистра SP (Указатель стека).
     *
     * @return значение
     */
    private int getSP() {
        return fRegs[SP];
    }

    /**
     * Устанавливает значение регистра SP (Указатель стека).
     *
     * @param value значение
     */
    private void setSP(int value) {
        fRegs[SP] = value & 0xFFFF;
    }

    /**
     * Получает значение регистра PC (Регистр адреса).
     *
     * @return значение
     */
    private int getPC() {
        return fRegs[PC];
    }

    /**
     * Устанавливает значение регистра PC (Регистр адреса).
     *
     * @param value значение
     */
    private void setPC(int value) {
        fRegs[PC] = value & 0xFFFF;
    }

    /**
     * Помещает значение в стек.
     *
     * @param value значение
     */
    private void pushWord(int value) {
        fRegs[SP]  =  (fRegs[SP] - 2) & 0xFFFF;
        fMDM.writeWord(fRegs[SP], value);
    }

    /**
     * Извлекает значение из стека.
     *
     * @return значение
     */
    private int popWord() {
        int v = fMDM.readWord(fRegs[SP]);
        fRegs[SP] = (fRegs[SP] + 2) & 0xFFFF;
        return v;
    }

    /**
     * Читает байт из памяти по адресу, заданному в регистре PC (Регистр адреса)
     * и увеличивает значение регистра PC на 1.
     *
     * @return считанный из устройства памяти байт
     */
    private int nextBytePC() {
        int v = fMDM.readByte(fRegs[PC]);
        fRegs[PC] = (fRegs[PC] + 1) & 0xFFFF;
        return v;
    }

    /**
     * Читает слово из памяти по адресу, заданному в регистре PC (Регистр
     * адреса) и увеличивает значение регистра PC на 2.
     *
     * @return считанное из устройства памяти слово
     */
    private int nextWordPC() {
        int v = fMDM.readWord(fRegs[PC]);
        fRegs[PC] = (fRegs[PC] + 2) & 0xFFFF;
        return v;
    }

    /**
     * Начинает выполнение команды CPU.
     *
     * @return количество циклов команды
     */
    private int cmdStart() {
        fOpCode = nextBytePC();
        int cycles = CYCLES[fOpCode];
        if (cycles < 256) {
            return cycles;
        }
        fTestResult = testFlags((fOpCode >> 3) & 0b111);
        return fTestResult ? cycles >> 8 : cycles & 0xFF;
    }

    /**
     * Завершает выполнение команды CPU.
     */
    private void cmdFinish() {
        int r;
        int v;
        boolean f;

        switch (fOpCode) {
            // NOP, 0x00, 00000000
            case 0x00:
            // Undocumented NOP.
            case 0x08:
            case 0x10:
            case 0x18:
            case 0x20:
            case 0x28:
            case 0x30:
            case 0x38:
                break;

            // LXI, 0x01, 00rr0001
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (SP)
            case 0x01: // LXI  B, data16
            case 0x11: // LXI  D, data16
            case 0x21: // LXI  H, data16
            case 0x31: // LXI SP, data16
                r = fOpCode >> 3;
                if (r == 6) // SP
                {
                    setSP(nextWordPC());
                } else {
                    setRegPair(r, nextWordPC());
                }
                break;

            // STAX, 0x02, 00rr0010
            // rr - 00 (BC), 01 (DE)
            case 0x02: // STAX B
            case 0x12: // STAX D
                fMDM.writeByte(getRegPair(fOpCode >> 3), getReg(A));
                break;

            // INX, 0x03, 00rr0011
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (SP)
            case 0x03: // INX B
            case 0x13: // INX D
            case 0x23: // INX H
            case 0x33: // INX SP
                r = fOpCode >> 3;
                if (r == 6) // SP
                {
                    setSP(getSP() + 1);
                } else {
                    setRegPair(r, getRegPair(r) + 1);
                }
                break;

            // INR, 0x04, 00rrr100
            // rrr - B, C, D, E, H, L, M, A
            case 0x04: // INR B
            case 0x0C: // INR C
            case 0x14: // INR D
            case 0x1C: // INR E
            case 0x24: // INR H
            case 0x2C: // INR L
            case 0x34: // INR M
            case 0x3C: // INR A
                r = fOpCode >> 3;
                v = (getReg(r) + 1) & 0xFF;
                setReg(r, v);
                setFlagAC((v & 0xF) == 0);
                setFlagsSZP(v);
                break;

            // DCR, 0x05, 00rrr101
            // rrr - B, C, D, E, H, L, M, A
            case 0x05: // DCR B
            case 0x0D: // DCR C
            case 0x15: // DCR D
            case 0x1D: // DCR E
            case 0x25: // DCR H
            case 0x2D: // DCR L
            case 0x35: // DCR M
            case 0x3D: // DCR A
                r = fOpCode >> 3;
                v = (getReg(r) - 1) & 0xFF;
                setReg(r, v);
                setFlagAC((v & 0xF) != 0xF); // Тут флаг взводится наоборот
                setFlagsSZP(v);
                break;

            // MVI, 0x06, 00rrr110
            // rrr - B, C, D, E, H, L, M, A
            case 0x06: // MVI B, data8
            case 0x0E: // MVI C, data8
            case 0x16: // MVI D, data8
            case 0x1E: // MVI E, data8
            case 0x26: // MVI H, data8
            case 0x2E: // MVI L, data8
            case 0x36: // MVI M, data8
            case 0x3E: // MVI A, data8
                setReg(fOpCode >> 3, nextBytePC());
                break;

            case 0x07: // RLC
                v = getReg(A);
                f = (v & 0x80) != 0;
                v = (v << 1) & 0xFF;
                setFlagC(f);
                if (f) {
                    setReg(A, v | 1);
                } else {
                    setReg(A, v);
                }
                break;

            // DAD, 0x09, 00rr1001
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (SP)
            case 0x09: // DAD B
            case 0x19: // DAD D
            case 0x29: // DAD H
            case 0x39: // DAD SP (код = 6)
                r = (fOpCode & 0b0011_0000) >> 3;
                v = getRegPair(P_HL) + (r == 6 ? getSP() : getRegPair(r));
                setFlagC((v & 0x1_0000) != 0);
                setRegPair(P_HL, v);
                break;

            // LDAX, 0x0A, 00rr1010
            // rr - 00 (BC), 01 (DE)
            case 0x0A: // LDAX B
            case 0x1A: // LDAX D
                setReg(A, fMDM.readByte(getRegPair((fOpCode & 0b0011_0000) >> 3)));
                break;

            // DCX, 0x0B, 00rr1011
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (SP)
            case 0x0B: // DCX B
            case 0x1B: // DCX D
            case 0x2B: // DCX H
            case 0x3B: // DCX SP
                r = (fOpCode & 0b0011_0000) >> 3;
                if (r == 6) // SP
                {
                    setSP(getSP() - 1);
                } else {
                    setRegPair(r, getRegPair(r) - 1);
                }
                break;

            case 0x0F: // RRC
                v = getReg(A);
                f = (v & 1) != 0;
                v = (v >> 1) & 0xFF;
                setFlagC(f);
                if (f) {
                    setReg(A, v | 0x80);
                } else {
                    setReg(A, v);
                }
                break;

            case 0x17: // RAL
                v = getReg(A);
                f = getFlagC();
                setFlagC((v & 0x80) != 0);
                v = (v << 1) & 0xFF;
                if (f) {
                    setReg(A, v | 1);
                } else {
                    setReg(A, v);
                }
                break;

            case 0x1F: // RAR
                v = getReg(A);
                f = getFlagC();
                setFlagC((v & 1) != 0);
                v = (v >> 1) & 0xFF;
                if (f) {
                    setReg(A, v | 0x80);
                } else {
                    setReg(A, v);
                }
                break;

            case 0x22: // SHLD addr
                fMDM.writeWord(nextWordPC(), getRegPair(P_HL));
                break;

            case 0x27: // DAA
                cmdDAA();
                break;

            case 0x2A: // LHLD addr
                setRegPair(P_HL, fMDM.readWord(nextWordPC()));
                break;

            case 0x2F: // CMA
                setReg(A, getReg(A) ^ 0xFF);
                break;

            case 0x32: // STA addr
                fMDM.writeByte(nextWordPC(), getReg(A));
                break;

            case 0x37: // STC
                setFlagC(true);
                break;

            case 0x3A: // LDA addr
                setReg(A, fMDM.readByte(nextWordPC()));
                break;

            case 0x3F: // CMC
                setFlagC(!getFlagC());
                break;

            // MOV, 0x40, 01dddsss
            // ddd, sss - B, C, D, E, H, L, M, A
            case 0x40: // MOV B, B
            case 0x41: // MOV B, C
            case 0x42: // MOV B, D
            case 0x43: // MOV B, E
            case 0x44: // MOV B, H
            case 0x45: // MOV B, L
            case 0x46: // MOV B, M
            case 0x47: // MOV B, A

            case 0x48: // MOV C, B
            case 0x49: // MOV C, C
            case 0x4A: // MOV C, D
            case 0x4B: // MOV C, E
            case 0x4C: // MOV C, H
            case 0x4D: // MOV C, L
            case 0x4E: // MOV C, M
            case 0x4F: // MOV C, A

            case 0x50: // MOV D, B
            case 0x51: // MOV D, C
            case 0x52: // MOV D, D
            case 0x53: // MOV D, E
            case 0x54: // MOV D, H
            case 0x55: // MOV D, L
            case 0x56: // MOV D, M
            case 0x57: // MOV D, A

            case 0x58: // MOV E, B
            case 0x59: // MOV E, C
            case 0x5A: // MOV E, D
            case 0x5B: // MOV E, E
            case 0x5C: // MOV E, H
            case 0x5D: // MOV E, L
            case 0x5E: // MOV E, M
            case 0x5F: // MOV E, A

            case 0x60: // MOV H, B
            case 0x61: // MOV H, C
            case 0x62: // MOV H, D
            case 0x63: // MOV H, E
            case 0x64: // MOV H, H
            case 0x65: // MOV H, L
            case 0x66: // MOV H, M
            case 0x67: // MOV H, A

            case 0x68: // MOV L, B
            case 0x69: // MOV L, C
            case 0x6A: // MOV L, D
            case 0x6B: // MOV L, E
            case 0x6C: // MOV L, H
            case 0x6D: // MOV L, L
            case 0x6E: // MOV L, M
            case 0x6F: // MOV L, A

            case 0x70: // MOV M, B
            case 0x71: // MOV M, C
            case 0x72: // MOV M, D
            case 0x73: // MOV M, E
            case 0x74: // MOV M, H
            case 0x75: // MOV M, L
            case 0x77: // MOV M, A

            case 0x78: // MOV A, B
            case 0x79: // MOV A, C
            case 0x7A: // MOV A, D
            case 0x7B: // MOV A, E
            case 0x7C: // MOV A, H
            case 0x7D: // MOV A, L
            case 0x7E: // MOV A, M
            case 0x7F: // MOV A, A
                setReg((fOpCode >> 3) & 0b111, getReg(fOpCode & 0b111));
                break;

            case 0x76: // HLT
                setPC(getPC() - 1);
                startDebugger   ();
                break;

            // ADD, 0x80, 10000rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0x80: // ADD B
            case 0x81: // ADD C
            case 0x82: // ADD D
            case 0x83: // ADD E
            case 0x84: // ADD H
            case 0x85: // ADD L
            case 0x86: // ADD M
            case 0x87: // ADD A
                setReg(A, addByte(getReg(A), getReg(fOpCode & 0b111), 0));
                break;

            // ADC, 0x80, 10001rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0x88: // ADC B
            case 0x89: // ADC C
            case 0x8A: // ADC D
            case 0x8B: // ADC E
            case 0x8C: // ADC H
            case 0x8D: // ADC L
            case 0x8E: // ADC M
            case 0x8F: // ADC A
                setReg(A, addByte(getReg(A), getReg(fOpCode & 0b111), getFlagC() ? 1 : 0));
                break;

            // SUB, 0x90, 10010rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0x90: // SUB B
            case 0x91: // SUB C
            case 0x92: // SUB D
            case 0x93: // SUB E
            case 0x94: // SUB H
            case 0x95: // SUB L
            case 0x96: // SUB M
            case 0x97: // SUB A
                setReg(A, subByte(getReg(A), getReg(fOpCode & 0b111), 0));
                break;

            // SBB, 0x98, 10010rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0x98: // SBB B
            case 0x99: // SBB C
            case 0x9A: // SBB D
            case 0x9B: // SBB E
            case 0x9C: // SBB H
            case 0x9D: // SBB L
            case 0x9E: // SBB M
            case 0x9F: // SBB A
                setReg(A, subByte(getReg(A), getReg(fOpCode & 0b111), getFlagC() ? 1 : 0));
                break;

            // ANA, 0xA0, 10100rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0xA0: // ANA B
            case 0xA1: // ANA C
            case 0xA2: // ANA D
            case 0xA3: // ANA E
            case 0xA4: // ANA H
            case 0xA5: // ANA L
            case 0xA6: // ANA M
            case 0xA7: // ANA A
                setReg(A, anaByte(getReg(A), getReg(fOpCode & 0b111)));
                break;

            // XRA, 0xA0, 10101rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0xA8: // XRA B
            case 0xA9: // XRA C
            case 0xAA: // XRA D
            case 0xAB: // XRA E
            case 0xAC: // XRA H
            case 0xAD: // XRA L
            case 0xAE: // XRA M
            case 0xAF: // XRA A
                v = getReg(A) ^ getReg(fOpCode & 0b111);
                setReg(A, v);
                setFlagsSZP(v);
                setFlagC (false);
                setFlagAC(false);
                break;

            // ORA, 0xB0, 10110rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0xB0: // ORA B
            case 0xB1: // ORA C
            case 0xB2: // ORA D
            case 0xB3: // ORA E
            case 0xB4: // ORA H
            case 0xB5: // ORA L
            case 0xB6: // ORA M
            case 0xB7: // ORA A
                v = getReg(A) | getReg(fOpCode & 0b111);
                setReg(A, v);
                setFlagsSZP(v);
                setFlagC (false);
                setFlagAC(false);
                break;

            // CMP, 0xB0, 10111rrr
            // rrr - B, C, D, E, H, L, M, A
            case 0xB8: // CMP B
            case 0xB9: // CMP C
            case 0xBA: // CMP D
            case 0xBB: // CMP E
            case 0xBC: // CMP H
            case 0xBD: // CMP L
            case 0xBE: // CMP M
            case 0xBF: // CMP A
                subByte(getReg(A), getReg(fOpCode & 0b111), 0);
                break;

            // RNZ, RZ, RNC, RC, RPO, RPE, R, RM, 0xC0, 11ccc000
            // ccc - NZ, Z, NC, C, PO, PE, P, M
            case 0xC0: // RNZ
            case 0xC8: // RZ
            case 0xD0: // RNC
            case 0xD8: // RC
            case 0xE0: // RPO
            case 0xE8: // RPE
            case 0xF0: // R
            case 0xF8: // RM
                if (fTestResult) {
                    setPC(popWord());
                }
                break;

            // POP, 0xC1, 11rr0001
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (PSW)
            case 0xC1: // POP B
            case 0xD1: // POP D
            case 0xE1: // POP H
            case 0xF1: // POP PSW
                r = (fOpCode & 0b0011_0000) >> 3;
                if (r == P_PSW) //                        PSW = SZ0A_0P1C
                {
                    setRegPair(r, popWord() & 0b1111_1111_1101_0111 | 0b10);
                } else {
                    setRegPair(r, popWord());
                }
                break;

            // JNZ, JZ, JNC, JC, JPO, JPE, JP, JM, 0xC2, 11ccc010
            // ccc - NZ, Z, NC, C, PO, PE, P, M
            case 0xC2: // JNZ addr
            case 0xCA: // JZ  addr
            case 0xD2: // JNC addr
            case 0xDA: // JC  addr
            case 0xE2: // JPO addr
            case 0xEA: // JPE addr
            case 0xF2: // JP  addr
            case 0xFA: // JM  addr
                v = nextWordPC();
                if (fTestResult) {
                    setPC(v);
                }
                break;

            // JMP, 0xc3, 1100r011
            case 0xC3: // JMP addr
            case 0xCB: // JMP addr, undocumented
                setPC(nextWordPC());
                break;

            // CNZ, CZ, CNC, CC, CPO, CPE, CP, CM, 0xC4, 11ccc100
            // ccc - NZ, Z, NC, C, PO, PE, P, M
            case 0xC4: // CNZ addr
            case 0xCC: // CZ  addr
            case 0xD4: // CNC addr
            case 0xDC: // CC  addr
            case 0xE4: // CPO addr
            case 0xEC: // CPE addr
            case 0xF4: // CP  addr
            case 0xFC: // CM  addr
                v = nextWordPC();
                if (fTestResult) {
                    pushWord(getPC());
                    setPC(v);
                }
                break;

            // PUSH, 0xC5, 11rr0101
            // rr - 00 (BC), 01 (DE), 10 (HL), 11 (PSW)
            case 0xC5: //* PUSH B
            case 0xD5: //* PUSH D
            case 0xE5: //* PUSH H
            case 0xF5: //* PUSH PSW
                pushWord(getRegPair((fOpCode & 0b0011_0000) >> 3));
                break;

            case 0xC6: // ADI data8
                setReg(A, addByte(getReg(A), nextBytePC(), 0));
                break;

            // RST, 0xC7, 11aaa111
            // aaa - 000(0)-111(7), address = aaa*8 (0 to 0x38).
            case 0xC7: //* RST 0
            case 0xCF: //* RST 1
            case 0xD7: //* RST 2
            case 0xDF: //* RST 3
            case 0xE7: //* RST 4
            case 0xEF: //* RST 5
            case 0xF7: //* RST 5
            case 0xFF: //* RST 7
                pushWord(getPC());
                setPC(fOpCode & 0b0011_1000);
                break;

            // RET, 0xC9, 110r1001
            case 0xC9: // RET
            case 0xD9: // RET, undocumented
                setPC(popWord());
                break;

            // CALL, 0xCD, 11rr1101
            case 0xCD: // CALL addr
            case 0xDD: // CALL, undocumented
            case 0xED:
            case 0xFD:
                v = nextWordPC() ;
                pushWord(getPC());
                setPC(v);
                break;

            case 0xCE: // ACI data8
                setReg(A, addByte(getReg(A), nextBytePC(), getFlagC() ? 1 : 0));
                break;

            case 0xD3: // OUT port8
                v = nextBytePC();
                if (fIoDM == null) {
                    fMDM.writeByte(v | (v << 8), getReg(A)); // На "Специалисте_MX" как запись по адресу (port, port)
                } else {
                    fIoDM.writeByte(v, getReg(A));
                }
                break;

            case 0xD6: // SUI data8
                setReg(A, subByte(getReg(A), nextBytePC(), 0));
                break;

            case 0xDB: // IN port8
                v = nextBytePC();
                if (fIoDM == null) {
                    setReg(A, fMDM.readByte(v | (v << 8))); // На "Специалисте_MX" как запись по адресу (port, port)
                } else {
                    setReg(A, fIoDM.readByte(v));
                }
                break;

            case 0xDE: // SBI data8
                setReg(A, subByte(getReg(A), nextBytePC(), getFlagC() ? 1 : 0));
                break;

            case 0xE3: // XTHL
                v = fMDM.readWord(getSP());
                fMDM.writeWord(getSP(), getRegPair(P_HL));
                setRegPair(P_HL, v);
                break;

            case 0xE6: // ANI data8
                setReg(A, anaByte(getReg(A), nextBytePC()));
                break;

            case 0xE9: // PCHL
                setPC(getRegPair(P_HL));
                break;

            case 0xEB: // XCHG
                r = getRegPair(P_DE);
                setRegPair(P_DE, getRegPair(P_HL));
                setRegPair(P_HL, r);
                break;

            case 0xEE: // XRI data8
                v = getReg(A) ^ nextBytePC();
                setReg(A, v);
                setFlagsSZP(v);
                setFlagC (false);
                setFlagAC(false);
                break;

            // DI/EI, 1111c011
            // C - 0 (DI), 1 (EI)
            case 0xF3: // DI
            case 0xFB: // EI
                break;

            case 0xF6: // ORI data8
                v = getReg(A) | nextBytePC();
                setReg(A, v);
                setFlagsSZP(v);
                setFlagC (false);
                setFlagAC(false);
                break;

            case 0xF9: // SPHL
                setSP(getRegPair(P_HL));
                break;

            case 0xFE: // CPI data8
                subByte(getReg(A), nextBytePC(), 0);
                break;

            default:
                break;
        }
        // Проверям ловушку для следующей команды CPU
        if (fTrapsFlag) {
            // Здесь без порождения новых объектов (для скорости)
            fCompareTrap.change(fSpMX.getPage(), getPC());
            if (fTraps.contains(fCompareTrap)) {
                startDebugger();
            }
        }
    }

    /**
     * Перводит CPU в режим ожидания (выполняется захват "HOLD").
     * (Данный метод нельзя запускать при остановленном тактовом генераторе!!!)
     *
     * @param mode true/false = установить/снять режим "HOLD"
     */
    void hold(boolean mode) {
        if ((fHoldPhase == HOLD_IS_NOT_SET) == mode) {
            if (mode) {
                if ((fCycles.get() <= 1) || Thread.currentThread().getName().equals(ClockGenerator.THREAD_NAME)) {
                    // Для потока тактового генератора останавливаем CPU сразу конечным значением HOLD_ACKNOWLEDGE,
                    // т.к. здесь вызов всегда происходит в последней фазе работы цикла CPU (из метода cmdFinish())
                    fHoldPhase = HOLD_ACKNOWLEDGE;
                } else {
                    // Для других потоков устанавливаем промежуточную фазу остановки CPU и ожидаем её завершения
                    fHoldPhase = HOLD_IN_PROCESS;
                    // noinspection StatementWithEmptyBody
                    while (fHoldPhase == HOLD_IN_PROCESS) {
                        //
                    }
                }
            } else {
                // Запускаем CPU
                fHoldPhase = HOLD_IS_NOT_SET;
            }
        }
    }

    /**
     * Показывает установлен ли режим "HOLD" для CPU.
     *
     * @return true = установлен режим "HOLD"
     */
    boolean isHoldAcknowledge() {
        return fHoldPhase == HOLD_ACKNOWLEDGE;
    }

    /**
     * Переводит устройства памяти и устройства ввода/вывода, подключенные к CPU, в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     */
    void pauseMemoryDevices(boolean mode) {
        fMDM.pauseMemoryDevices(mode);
        if (fIoDM != null) {
            fIoDM.pauseMemoryDevices(mode);
        }
    }

    /**
     * Выполняет запуск с заданного адреса.
     *
     * @param address адрес запуска
     */
    synchronized void run(int address) {
        setPC(address);
    }

    /**
     * Выполняет сброс CPU.
     *
     * @param address            адрес запуска
     * @param resetMemoryDevices true - выполняет сброс устройств памяти
     */
    synchronized void reset(int address, boolean resetMemoryDevices) {
        Arrays.fill(fRegs, 0);
        fRegs[F] = 0b0000_0010; // по умолчанию SZ0A_0P1C
        setPC(address);
        if (resetMemoryDevices) {
            fMDM.resetMemoryDevices(false);
            if (fIoDM != null) {
                fIoDM.resetMemoryDevices(false);
            }
        }
    }

    /**
     * Запускает отладчик.
     */
    private void startDebugger() {
        // Вызов возможен только для потока тактового генератора (блокирует вызов из потока Swing)
        if (!fDebugRun && Thread.currentThread().getName().equals(ClockGenerator.THREAD_NAME)) {
            // Блокируем возможность одновременного запуска нескольких копий отладчика
             fDebugRun = true;
            // Останавливаем CPU и устройства памяти
                          hold(true);
            pauseMemoryDevices(true);
            // Далее работаем в потоке Swing
            EventQueue.invokeLater(() -> {
                try {
                    // Останавливаем тактовый генератор
                    fSpMX.pause(true, false);
                    try {
                        // Отменяем режим "Пауза" только для CPU
                        hold(false);
                        // Удаляем StepOver ловушку, если она вызвала отладчик
                        int page    = fSpMX.getPage();
                        int address = debugGetValRegPair(DebugRegPair.PC);
                        if (debugIsStepOverTrap(page, address)) {
                                   debugRemTrap(page, address);
                        }
                        // Выводим окно отладчика
                        DebuggerI8080 debug = new DebuggerI8080(fSpMX);
                        // После окончания работы - убиваем отладчик
                        debug.getContentPane().removeAll();
                        debug.dispose();
                    } finally {
                        // Запускаем тактовый генератор и устройства памяти
                        fSpMX.pause(false, true);
                    }
                } finally {
                    fDebugRun = false;
                }
            });
        }
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // -= Методы для отладчика =-

    /**
     * Получает значение регистровой пары CPU.
     * (Метод для вызова из отладчика)
     *
     * @param regPair регистровая пара
     * @return значение
     */
    synchronized int debugGetValRegPair(final DebugRegPair regPair) {
        switch (regPair) {
            case AF:
                return (fRegs[A] << 8) | fRegs[F];
            case BC:
                return (fRegs[B] << 8) | fRegs[C];
            case DE:
                return (fRegs[D] << 8) | fRegs[E];
            case HL:
                return (fRegs[H] << 8) | fRegs[L];
            case SP:
                return fRegs[SP];
            case PC:
                return fRegs[PC];
        }
        return -1;
    }

    /**
     * Устанавливает значение регистровой пары CPU.
     * (Метод для вызова из отладчика)
     *
     * @param regPair регистровая пара
     * @param value   значение
     */
    synchronized void debugSetValRegPair(final DebugRegPair regPair, final int value) {
        switch (regPair) {
            case AF:
                fRegs[A]  = (value >> 8) & 0xFF;
                fRegs[F]  =  value & 0b1101_0111 | 0b10;
                break;
            case BC:
                fRegs[B]  = (value >> 8) & 0xFF;
                fRegs[C]  =  value & 0xFF;
                break;
            case DE:
                fRegs[D]  = (value >> 8) & 0xFF;
                fRegs[E]  =  value & 0xFF;
                break;
            case HL:
                fRegs[H]  = (value >> 8) & 0xFF;
                fRegs[L]  =  value & 0xFF;
                break;
            case SP:
                fRegs[SP] = value & 0xFFFF;
                break;
            case PC:
                fRegs[PC] = value & 0xFFFF;
                break;
        }
    }

    /**
     * Добавляет ловушку.
     * (Метод для вызова из отладчика)
     *
     * @param page     номер страницы памяти
     * @param address  адрес ловушки
     * @param stepOver true = StepOver ловушка
     */
    void debugAddTrap(final int page, final int address, final boolean stepOver) {
        final Trap trap = new Trap(page, address);
        if (!stepOver && trap.equals(fTrapStepOver)) {
            fTrapStepOver = null;
        }
        if (!fTraps.contains(trap)) {
            if (stepOver) {
                if (fTrapStepOver != null) {
                    fTraps.remove(fTrapStepOver);
                }
                fTrapStepOver = trap;
            }
            fTraps.add(trap);
        }
        fTrapsFlag = !fTraps.isEmpty();
    }

    /**
     * Удаляет ловушку.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес ловушки
     */
    void debugRemTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        if (trap.equals(fTrapStepOver)) {
            fTrapStepOver = null;
        }
        fTraps.remove(trap);
        fTrapsFlag = !fTraps.isEmpty();
    }

    /**
     * Удаляет все ловушки.
     * (Метод для вызова из отладчика)
     */
    void debugClearTraps() {
        fTraps.clear();
        fTrapStepOver =  null;
        fTrapsFlag    = false;
    }

    /**
     * Проверяет, установлена ли по заданному адресу StepOver ловушка.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка StepOver установлена
     */
    private boolean debugIsStepOverTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        return fTrapsFlag && trap.equals(fTrapStepOver) && (fTraps.contains(trap));
    }

    /**
     * Проверяет, установлена ли по заданному адресу ловушка.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка установлена
     */
    boolean debugIsTrap(final int page, final int address) {
        final Trap trap = new Trap(page,  address);
        return fTrapsFlag && !trap.equals(fTrapStepOver) && (fTraps.contains(trap));
    }

    /**
     * Возвращает количество установленных ловушек.
     * (Метод для вызова из отладчика)
     *
     * @return количество установленных ловушек
     */
    int debugGetTrapCount() {
        return ((fTrapStepOver != null) && fTraps.contains(fTrapStepOver)) ? (fTraps.size() - 1) : fTraps.size();
    }

    /**
     * Возвращает ловушку по индексу в списке ловушек.
     * (Метод для вызова из отладчика)
     *
     * @param index индекс ловушки
     * @return ловушка
     */
    Trap debugGetTrap(final int index) {
        return fTraps.stream().filter(trap -> !trap.equals(fTrapStepOver)).skip(index).findFirst().orElse(null);
    }
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public boolean cycle() {
        if (fCycles.get() <= 1)  {
            switch (fHoldPhase) {
                case HOLD_IS_NOT_SET:
                    fCycles.set(cmdStart());
                    return false;
                case HOLD_IN_PROCESS:
                    fHoldPhase = HOLD_ACKNOWLEDGE;
                    return true;
                case HOLD_ACKNOWLEDGE:
                    return true;
                default:
                    fHoldPhase = 0;
                    fCycles.set(cmdStart());
                    return false;
            }
        } else {
            if (fCycles.decrementAndGet() == 1) {
                cmdFinish();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ((o == null) || (getClass() != o.getClass())) return false;
        ProcessorI8080 processorI8080 = (ProcessorI8080) o;
        return Objects.equals(fSpMX, processorI8080.fSpMX) &&
               Objects.equals( fMDM, processorI8080.fMDM ) &&
               Objects.equals(fIoDM, processorI8080.fIoDM);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fSpMX, fMDM, fIoDM);
    }
}
//=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
/**
 * Класс "Ловушка".
 */
final class Trap implements Comparable<Trap> {
    private int fValue;

    /**
     * Конструктор.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     */
    Trap(int page, int address) {
        fValue = (page << 16) | address;
    }

    /**
     * Возвращает номер страницы памяти.
     *
     * @return номер страницы памяти
     */
    int getPage() {
        return fValue >> 16;
    }

    /**
     * Возвращает адрес.
     *
     * @return адрес
     */
    int getAddress() {
        return fValue & 0xFFFF;
    }

    /**
     * Изменяет ловушку.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     */
    void change(int page, int address) {
        fValue = (page << 16) | address;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Trap) && (fValue == ((Trap) obj).fValue);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(fValue);
    }

    @Override
    public int compareTo(@NotNull Trap anotherTrap) {
        return Integer.compare(this.fValue, anotherTrap.fValue);
    }
}
//=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=