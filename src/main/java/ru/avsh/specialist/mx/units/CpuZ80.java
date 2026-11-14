package ru.avsh.specialist.mx.units;

import org.jetbrains.annotations.Nullable;
import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.helpers.Trap;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.memory.MemoryManager;
import ru.avsh.specialist.mx.units.types.CpuDebugCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс "Процессор Zilog Z80".
 * <p>
 * Реализован базовый (документированный) набор команд Z80, а также префиксные наборы
 * {@code CB}, {@code ED}, {@code DD} и {@code FD}, включая альтернативный регистровый
 * файл {@code AF'}, {@code BC'}, {@code DE'}, {@code HL'} (команды {@code EX AF,AF'} и {@code EXX}).
 * <p>
 * Сознательно НЕ реализованы:
 * <ul>
 *     <li>недокументированные команды — {@code SLL}, обращения к {@code IXH}/{@code IXL}/{@code IYH}/{@code IYL},
 *         недокументированные формы {@code ED} ({@code IN F,(C)}, {@code OUT (C),0});</li>
 *     <li>режимы прерываний {@code IM 0/1/2} и регистры {@code I}, {@code R}.</li>
 * </ul>
 * Любой недокументированный байт выполняется как {@code NOP}: команда занимает 4 такта,
 * регистры и флаги не меняются. Это детерминированное правило — эмулятор
 * никогда не падает на неизвестном байте.
 * <p>
 * Тактирование: процессор использует общий с i8080 тактовый генератор,
 * собственной тактовой частоты нет.
 * <p>
 * Отличие регистра флагов от i8080: бит 2 регистра {@code F} у Z80 — это {@code P/V}
 * (переполнение при арифметике, чётность при логических и блочных командах),
 * тогда как у i8080 тот же бит — только чётность {@code P}. Остальные биты
 * отличаются ещё сильнее: бит 5 и бит 3 у Z80 — копии битов результата {@code F5}/{@code F3},
 * бит 1 — {@code N} (признак вычитания), а у i8080 эти биты всегда равны 0 и 1.
 * <p>
 * Следствие: программа для i8080, проверяющая {@code PE}/{@code PO} после арифметики,
 * на Z80 получит другое условие перехода — это документированное поведение Z80,
 * а не ошибка реализации. Для программ, использующих {@code PE}/{@code PO} только после
 * логических команд ({@code ANA}, {@code XRA}, {@code ORA}, {@code CMP} с чётностью в i8080),
 * расхождения нет: у Z80 эти команды тоже формируют чётность.
 *
 * @author -=AVSh=-
 */
public final class CpuZ80 implements CpuDebugCore {
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

    // Индексы регистровых пар для методов getRegPair()/setRegPair()
    private static final int P_BC  = 0;
    private static final int P_DE  = 2;
    private static final int P_HL  = 4;
    private static final int P_PSW = 6;

    // Битовая маска флагов регистра F
    private static final int FLAG_C  = 0x01;
    private static final int FLAG_N  = 0x02;
    private static final int FLAG_PV = 0x04;
    private static final int FLAG_F3 = 0x08;
    private static final int FLAG_H  = 0x10;
    private static final int FLAG_F5 = 0x20;
    private static final int FLAG_Z  = 0x40;
    private static final int FLAG_S  = 0x80;

    // Фазы установки состояния "HOLD" CPU (здесь без enum - для скорости)
    private static final int HOLD_IS_NOT_SET  = 0;
    private static final int HOLD_IN_PROCESS  = 1;
    private static final int HOLD_ACKNOWLEDGE = 2;

    // Активный индексный префикс
    private static final int INDEX_NONE = 0;
    private static final int INDEX_IX   = 1;
    private static final int INDEX_IY   = 2;

    // Таблица чётности байта: true = чётное количество единичных битов
    private static final boolean[] PARITY = new boolean[256];

    static {
        for (int i = 0; i < 256; i++) {
            PARITY[i] = (Integer.bitCount(i) & 1) == 0;
        }
    }

    private final int[]         fRegs      ;
    // Альтернативный регистровый файл (A', F', B', C', D', E', H', L') - индексы те же, что в fRegs
    private final int[]         fRegsAlt   ;
    private final MemoryManager fMemManager;
    private final MemoryManager fIoManager ;
    private final SpecialistMX  fSpMX      ;
    private final AtomicInteger fCycles    ;
    private final AtomicInteger fHoldPhase ;
    private final AtomicBoolean fActive    ;
    private final AtomicBoolean fIsDebugRun;

    private final SortedSet<Trap> fTraps      ;
    private final Trap            fCompareTrap;

    private int     fOpCode   ;
    private int     fIX       ; // Регистр IX
    private int     fIY       ; // Регистр IY
    private int     fIndexMode; // INDEX_NONE / INDEX_IX / INDEX_IY
    private boolean fHalted   ;
    private boolean fInterruptEnabled;

    private volatile boolean fPause       ;
    private volatile boolean fTrapsFlag   ;
    private volatile Trap    fTrapStepOver;

    /**
     * Конструктор.
     *
     * @param spMX       ссылка на объект класса SpecialistMX - "Компьютер 'Специалист MX'" (нужна только отладчику) или null
     * @param memManager ссылка на объект класса MemoryManager - "Быстрый диспетчер запоминающих устройств" (запоминающие устройства)
     * @param ioManager  ссылка на объект класса MemoryManager - "Быстрый диспетчер запоминающих устройств" (устройства ввода/вывода) или null
     */
    public CpuZ80(@Nullable final SpecialistMX spMX, final MemoryManager memManager, final MemoryManager ioManager) {
              fSpMX = spMX;
        fMemManager = memManager;
         fIoManager =  ioManager;
                                // 0, 1, 2, 3, 4, 5, 6, 7,  8,  9
        fRegs    = new int[10]; // B, C, D, E, H, L, F, A, SP, PC
        fRegs[F] = 0b0000_0000; // Флаги по умолчанию SZ5H3PNC
        fRegsAlt = new int[ 8]; // B', C', D', E', H', L', F', A'

             fCycles = new AtomicInteger();
         fIsDebugRun = new AtomicBoolean();
          fHoldPhase = new AtomicInteger();
             fActive = new AtomicBoolean();
        fCompareTrap = new Trap(0, 0);
              fTraps = new ConcurrentSkipListSet<>();
    }

    @Override
    public String toString() {
        return String.format("Значения регистров: SZ5H3PNC=%s, A=%02X, B=%02X, C=%02X, D=%02X, E=%02X, H=%02X, L=%02X, IX=%04X, SP=%04X, PC=%04X%n" +
                             "Память с адреса PC: %02X, %02X, %02X, %02X, %02X, ...",
                Integer.parseInt(Integer.toBinaryString(fRegs[F])),
                fRegs[A], fRegs[B], fRegs[C], fRegs[D], fRegs[E], fRegs[H], fRegs[L], getIndex(), fRegs[SP], fRegs[PC],
                fMemManager.readByte((fRegs[PC]    ) & 0xFFFF),
                fMemManager.readByte((fRegs[PC] + 1) & 0xFFFF),
                fMemManager.readByte((fRegs[PC] + 2) & 0xFFFF),
                fMemManager.readByte((fRegs[PC] + 3) & 0xFFFF),
                fMemManager.readByte((fRegs[PC] + 4) & 0xFFFF));
    }

    /**
     * Включает или выключает процессор.
     * Неактивный процессор находится в массиве тактируемых устройств, но не исполняет команды.
     *
     * @param active true = процессор активен
     */
    @Override
    public void setActive(final boolean active) {
        fActive.set(active);
        if (!active) {
            fHalted = false;
        }
    }

    /**
     * Показывает, активен ли процессор.
     *
     * @return true = процессор активен
     */
    @Override
    public boolean isActive() {
        return fActive.get();
    }

    /**
     * Возвращает состояние регистровой пары, общей для всех процессоров эмулятора.
     * Младший байт результата - первый регистр пары, старший байт - второй
     * (BC, DE, HL; для AF - младший байт F, старший A; SP и PC - адрес целиком).
     *
     * @param commonPair индекс регистровой пары: 0 - BC, 2 - DE, 4 - HL, 6 - AF, 8 - SP, 9 - PC
     * @return значение регистровой пары
     */
    @Override
    public int getCommonRegPair(final int commonPair) {
        switch (commonPair) {
            case P_BC : return getRegPair(P_BC );
            case P_DE : return getRegPair(P_DE );
            case P_HL : return getRegPair(P_HL );
            case P_PSW: return getRegPair(P_PSW);
            case SP:    return getSP();
            case PC:    return getPC();
            default:    return 0;
        }
    }

    /**
     * Устанавливает состояние регистровой пары, общей для всех процессоров эмулятора.
     * Регистры IX и IY не затрагиваются.
     *
     * @param commonPair индекс регистровой пары: 0 - BC, 2 - DE, 4 - HL, 6 - AF, 8 - SP, 9 - PC
     * @param value      значение регистровой пары
     */
    @Override
    public void setCommonRegPair(final int commonPair, final int value) {
        switch (commonPair) {
            case P_BC:  setRegPair(P_BC,  value); break;
            case P_DE:  setRegPair(P_DE,  value); break;
            case P_HL:  setRegPair(P_HL,  value); break;
            case P_PSW: setRegPair(P_PSW, value); break;
            case SP:    setSP(value);             break;
            case PC:    setPC(value);             break;
            default:                              break;
        }
    }

    @Override
    public boolean debugIsZ80() {
        return true;
    }

    @Override
    @Nullable
    public SpecialistMX debugGetEmulator() {
        return fSpMX;
    }

    @Override
    public AtomicBoolean debugGetDebugRunFlag() {
        return fIsDebugRun;
    }

    //~ Методы для отладчика ~

    /**
     * Получает значение регистровой пары CPU.
     * (Метод для вызова из отладчика)
     *
     * @param regPair регистровая пара
     * @return значение или -1, если пара не поддерживается
     */
    @Override
    public synchronized int debugGetValRegPair(final DebugRegPair regPair) {
        switch (regPair) {
            case AF   : return (fRegs[A] << 8) | fRegs[F];
            case BC   : return getRegPair(P_BC);
            case DE   : return getRegPair(P_DE);
            case HL   : return getRegPair(P_HL);
            case IX   : return fIX;
            case IY   : return fIY;
            case SP   : return getSP();
            case PC   :  return getPC();
            case AF_ALT: return (fRegsAlt[A] << 8) | fRegsAlt[F];
            case BC_ALT: return (fRegsAlt[B] << 8) | fRegsAlt[C];
            case DE_ALT: return (fRegsAlt[D] << 8) | fRegsAlt[E];
            case HL_ALT: return (fRegsAlt[H] << 8) | fRegsAlt[L];
            default    : return -1;
        }
    }

    /**
     * Устанавливает значение регистровой пары CPU.
     * Регистр F записывается целиком, без маскирования: у Z80 все восемь бит значащие.
     * (Метод для вызова из отладчика)
     *
     * @param regPair регистровая пара
     * @param value   значение регистровой пары
     */
    @Override
    public synchronized void debugSetValRegPair(final DebugRegPair regPair, final int value) {
        switch (regPair) {
            case AF:
                fRegs[A] = (value >> 8) & 0xFF;
                fRegs[F] =  value       & 0xFF;
                break;
            case BC: setRegPair(P_BC, value); break;
            case DE: setRegPair(P_DE, value); break;
            case HL: setRegPair(P_HL, value); break;
            case IX: fIX = value & 0xFFFF;    break;
            case IY: fIY = value & 0xFFFF;    break;
            case SP: setSP(value);            break;
            case PC: setPC(value);            break;
            case AF_ALT:
                fRegsAlt[A] = (value >> 8) & 0xFF;
                fRegsAlt[F] =  value       & 0xFF;
                break;
            case BC_ALT: setAltRegPair(B, C, value); break;
            case DE_ALT: setAltRegPair(D, E, value); break;
            case HL_ALT: setAltRegPair(H, L, value); break;
            default:                                 break;
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
    @Override
    public void debugAddTrap(final int page, final int address, final boolean stepOver) {
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
    @Override
    public void debugRemTrap(final int page, final int address) {
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
    @Override
    public void debugClearTraps() {
        fTraps.clear();
        fTrapStepOver =  null;
        fTrapsFlag    = false;
    }

    /**
     * Проверяет, установлена ли по заданному адресу ловушка (не StepOver).
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка установлена
     */
    @Override
    public boolean debugIsTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        return fTrapsFlag && !trap.equals(fTrapStepOver) && fTraps.contains(trap);
    }

    /**
     * Проверяет, установлена ли по заданному адресу StepOver ловушка.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка StepOver установлена
     */
    @Override
    public boolean debugIsStepOverTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        return fTrapsFlag && trap.equals(fTrapStepOver) && fTraps.contains(trap);
    }

    /**
     * Возвращает количество установленных ловушек (без StepOver).
     * (Метод для вызова из отладчика)
     *
     * @return количество установленных ловушек
     */
    @Override
    public int debugGetTrapCount() {
        return ((fTrapStepOver != null) && fTraps.contains(fTrapStepOver)) ? (fTraps.size() - 1) : fTraps.size();
    }

    /**
     * Возвращает ловушку по индексу в списке ловушек (без StepOver).
     * (Метод для вызова из отладчика)
     *
     * @param index индекс ловушки
     * @return ловушка или null
     */
    @Override
    public Trap debugGetTrap(final int index) {
        return new ArrayList<>(fTraps).stream().filter(trap -> !trap.equals(fTrapStepOver)).skip(index).findFirst().orElse(null);
    }

    /**
     * Возвращает индекс ловушки в списке ловушек.
     * (Метод для вызова из отладчика)
     *
     * @param trap ловушка
     * @return индекс или -1
     */
    @Override
    public int debugGetTrapIndex(final Trap trap) {
        return new ArrayList<>(fTraps).indexOf(trap);
    }

    /**
     * Проверяет ловушку для адреса следующей команды.
     * Вызывается в конце исполненной команды (как у i8080 в cmdFinish()):
     * к этому моменту PC уже указывает на следующую команду, а текущая
     * исполнена целиком, поэтому команда под ловушкой не выполняется дважды.
     * Пока процессор остановлен командой HALT, ловушки не проверяются.
     */
    private void checkTraps() {
        if (fTrapsFlag && !fHalted) {
            // Ловушка Z80 срабатывает по адресу на любой странице памяти
            final int pc = getPC();
            for (final Trap trap : fTraps) {
                if (trap.getAddress() == pc) {
                    // Отладчик сам снимает StepOver ловушку, поэтому здесь больше ничего не делаем
                    startDebugger();
                    break;
                }
            }
        }
    }

    //=-= Доступ к регистрам =-=

    private int getReg(final int codeReg) {
        return codeReg == M ? fMemManager.readByte(getHL()) : fRegs[codeReg];
    }

    private void setReg(final int codeReg, final int value) {
        if (codeReg == M) {
            fMemManager.writeByte(getHL(), value);
        } else {
            fRegs[codeReg] = value & 0xFF;
        }
    }

    private int getHL() {
        return (fRegs[H] << 8) | fRegs[L];
    }

    private void setHL(final int value) {
        fRegs[H] = (value >> 8) & 0xFF;
        fRegs[L] =  value       & 0xFF;
    }

    /**
     * Возвращает значение активного индексного регистра (IX или IY).
     * Вне индексного префикса возвращает IX.
     */
    private int getIndex() {
        return (fIndexMode == INDEX_IY) ? fIY : fIX;
    }

    /**
     * Устанавливает значение активного индексного регистра (IX или IY).
     * Вне индексного префикса изменяет IX.
     */
    private void setIndex(final int value) {
        if (fIndexMode == INDEX_IY) {
            fIY = value & 0xFFFF;
        } else {
            fIX = value & 0xFFFF;
        }
    }

    private int getRegPair(final int codePair) {
        return (codePair == P_PSW) ? ((fRegs[A] << 8) | fRegs[F]) : ((fRegs[codePair] << 8) | fRegs[codePair + 1]);
    }

    private void setRegPair(final int codePair, final int value) {
        if (codePair == P_PSW) {
            fRegs[F] =  value       & 0xFF;
            fRegs[A] = (value >> 8) & 0xFF;
        } else {
            fRegs[codePair    ] = (value >> 8) & 0xFF;
            fRegs[codePair + 1] =  value       & 0xFF;
        }
    }

    /**
     * Устанавливает значение пары альтернативного регистрового файла.
     *
     * @param reg1  индекс в fRegsAlt первого регистра пары (старший байт)
     * @param reg2  индекс в fRegsAlt второго регистра пары (младший байт)
     * @param value значение пары
     */
    private void setAltRegPair(final int reg1, final int reg2, final int value) {
        fRegsAlt[reg1] = (value >> 8) & 0xFF;
        fRegsAlt[reg2] =  value       & 0xFF;
    }

    /**
     * Меняет местами регистр основного и альтернативного файла.
     * Используется командами {@code EX AF,AF'} (пара A/F) и {@code EXX} (пары B/C, D/E, H/L).
     *
     * @param reg1 индекс первого регистра пары
     * @param reg2 индекс второго регистра пары
     */
    private void swapRegs(final int reg1, final int reg2) {
        int value = fRegs   [reg1]; fRegs   [reg1] = fRegsAlt[reg1]; fRegsAlt[reg1] = value;
            value = fRegs   [reg2]; fRegs   [reg2] = fRegsAlt[reg2]; fRegsAlt[reg2] = value;
    }

    private int getSP() {
        return fRegs[SP];
    }

    private void setSP(final int value) {
        fRegs[SP] = value & 0xFFFF;
    }

    private int getPC() {
        return fRegs[PC];
    }

    private void setPC(final int value) {
        fRegs[PC] = value & 0xFFFF;
    }

    private void pushWord(final int value) {
        final int sp = (getSP() - 2) & 0xFFFF;
        setSP(sp);
        fMemManager.writeByte(sp, value & 0xFF);
        fMemManager.writeByte((sp + 1) & 0xFFFF, (value >> 8) & 0xFF);
    }

    private int popWord() {
        final int sp  = getSP();
        final int val = fMemManager.readByte(sp) | (fMemManager.readByte((sp + 1) & 0xFFFF) << 8);
        setSP((sp + 2) & 0xFFFF);
        return val & 0xFFFF;
    }

    private int nextBytePC() {
        final int pc = getPC();
        setPC((pc + 1) & 0xFFFF);
        return fMemManager.readByte(pc);
    }

    private int nextWordPC() {
        final int lo = nextBytePC();
        final int hi = nextBytePC();
        return (hi << 8) | lo;
    }

    //=-= Флаги =-=

    private boolean getFlag(final int flag) {
        return (fRegs[F] & flag) != 0;
    }

    private void setFlag(final int flag, final boolean value) {
        if (value) {
            fRegs[F] |=  flag;
        } else {
            fRegs[F] &= ~flag;
        }
        fRegs[F] &= 0xFF;
    }

    private boolean getFlagC() {
        return getFlag(FLAG_C);
    }

    private void setFlagC(final boolean value) {
        setFlag(FLAG_C, value);
    }

    /**
     * Устанавливает флаги S, Z, F5, F3 и P/V по значению байта.
     * Используется для логических операций и результата пересылки.
     */
    private void setFlagsSZ5H3P(final int r) {
        final int v = r & 0xFF;
        fRegs[F] = (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0)
                 | (PARITY[v] ? FLAG_PV : 0);
    }

    /**
     * Устанавливает флаги S, Z, F5, F3 по значению байта без изменения C, P/V, N, H.
     */
    private void setFlagsSZ5H3(final int r) {
        final int v = r & 0xFF;
        fRegs[F] = (fRegs[F] & (FLAG_C | FLAG_PV | FLAG_N | FLAG_H))
                 | (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0);
    }

    /**
     * Считает флаги результата команды INC/DEC (флаг C сохраняется вызывающим кодом).
     */
    private static int incDecFlags(final int r, final int delta) {
        final int v = (r + delta) & 0xFF;
        int f = (v & (FLAG_S | FLAG_F5 | FLAG_F3)) | (v == 0 ? FLAG_Z : 0);
        if (delta > 0) {
            f |= ((r & 0x0F) == 0x0F) ? FLAG_H : 0;
            f |= (r == 0x7F)          ? FLAG_PV : 0;
        } else {
            f |= ((r & 0x0F) == 0x00) ? FLAG_H : 0;
            f |= (r == 0x80)          ? FLAG_PV : 0;
            f |= FLAG_N;
        }
        return f;
    }

    private int addByte(final int a, final int b, final boolean withCarry) {
        final int c   = withCarry ? (getFlagC() ? 1 : 0) : 0;
        final int res = a + b + c;
        final int h   = ((a & 0x0F) + (b & 0x0F) + c) > 0x0F ? FLAG_H : 0;
        final int ovf = (((a ^ res) & (b ^ res) & 0x80) != 0) ? FLAG_PV : 0;
        fRegs[A] = res & 0xFF;
        fRegs[F] = (fRegs[A] & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (fRegs[A] == 0 ? FLAG_Z : 0)
                 | h | ovf | ((res > 0xFF) ? FLAG_C : 0);
        return fRegs[A];
    }

    private int subByte(final int a, final int b, final boolean withCarry, final boolean storeResult) {
        final int c   = withCarry ? (getFlagC() ? 1 : 0) : 0;
        final int res = a - b - c;
        final int h   = ((a & 0x0F) - (b & 0x0F) - c) < 0 ? FLAG_H : 0;
        final int ovf = (((a ^ b) & (a ^ res) & 0x80) != 0) ? FLAG_PV : 0;
        final int v   = res & 0xFF;
        if (storeResult) {
            fRegs[A] = v;
        }
        fRegs[F] = (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0)
                 | h | ovf | FLAG_N | ((res < 0) ? FLAG_C : 0);
        return v;
    }

    private int andByte(final int a, final int b) {
        final int v = a & b;
        fRegs[A] = v;
        fRegs[F] = (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0)
                 | (PARITY[v] ? FLAG_PV : 0)
                 | FLAG_H;
        return v;
    }

    private int xorByte(final int a, final int b) {
        final int v = (a ^ b) & 0xFF;
        fRegs[A] = v;
        fRegs[F] = (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0)
                 | (PARITY[v] ? FLAG_PV : 0);
        return v;
    }

    private int orByte(final int a, final int b) {
        final int v = (a | b) & 0xFF;
        fRegs[A] = v;
        fRegs[F] = (v & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (v == 0 ? FLAG_Z : 0)
                 | (PARITY[v] ? FLAG_PV : 0);
        return v;
    }

    private int incByte(final int v) {
        final int c = fRegs[F] & FLAG_C;
        fRegs[F] = incDecFlags(v, +1) | c;
        return (v + 1) & 0xFF;
    }

    private int decByte(final int v) {
        final int c = fRegs[F] & FLAG_C;
        fRegs[F] = incDecFlags(v, -1) | c;
        return (v - 1) & 0xFF;
    }

    private void cmdDAA() {
        final int     a   = fRegs[A];
        final boolean n   = getFlag(FLAG_N);
        final boolean h   = getFlag(FLAG_H);
        final boolean c   = getFlag(FLAG_C);
        int     correction = 0;
        boolean setCarry   = c;

        // Документированный алгоритм Z80: при N=1 проверка тетрад не выполняется,
        // коррекция определяется только флагами H и C (в i8080 иначе)
        if (h || (!n && ((a & 0x0F) > 9))) {
            correction |= 0x06;
        }
        if (c || (!n && (a > 0x99))) {
            correction |= 0x60;
            setCarry    = true;
        } else if (h && (a > 0xF9)) {
            setCarry = true;
        }

        final int     newA;
        final boolean newH;
        if (n) {
            // Вычитание: коррекция выполняется вычитанием
            newA = (a - correction) & 0xFF;
            newH = h && (((a & 0x0F) < (correction & 0x0F)));
        } else {
            // Сложение: коррекция выполняется сложением
            newA = (a + correction) & 0xFF;
            newH = ((a & 0x0F) + (correction & 0x0F)) > 0x0F;
        }

        fRegs[A] = newA;
        fRegs[F] = (fRegs[F] & FLAG_N)
                 | (newA & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (newA == 0 ? FLAG_Z : 0)
                 | (newH ? FLAG_H : 0)
                 | (PARITY[newA] ? FLAG_PV : 0)
                 | (setCarry ? FLAG_C : 0);
    }

    private boolean testFlags(final int codeCondition) {
        switch (codeCondition & 0b111) {
            case 0: return !getFlag(FLAG_Z);  // NZ
            case 1: return  getFlag(FLAG_Z);  // Z
            case 2: return !getFlag(FLAG_C);  // NC
            case 3: return  getFlag(FLAG_C);  // C
            case 4: return !getFlag(FLAG_PV); // PO
            case 5: return  getFlag(FLAG_PV); // PE
            case 6: return !getFlag(FLAG_S);  // P
            default: return getFlag(FLAG_S);  // M
        }
    }

    //=-= Цикл CPU =-=

    @Override
    public boolean cycle() {
        if (!fActive.get()) {
            return true;
        }
        if (fCycles.get() <= 1) {
            switch (fHoldPhase.get()) {
                case HOLD_IS_NOT_SET:
                    fCycles.set(cmdStart());
                    return false;
                case HOLD_IN_PROCESS:
                    fHoldPhase.getAndSet(HOLD_ACKNOWLEDGE);
                    return true;
                case HOLD_ACKNOWLEDGE:
                    return true;
                default:
                    fHoldPhase.getAndSet(HOLD_IS_NOT_SET);
                    fCycles.set(cmdStart());
                    return false;
            }
        } else {
            if (fCycles.decrementAndGet() == 1) {
                // Команда исполнена целиком: проверяем ловушку для следующей
                // (как у i8080 в cmdFinish()), PC уже указывает на неё
                checkTraps();
                return true;
            }
        }
        return false;
    }

    private int cmdStart() {
        if (fHalted) {
            return 4;
        }
        fIndexMode = INDEX_NONE;
        fOpCode    = nextBytePC();
        return cmdExec();
    }

    @Override
    public void reset(final boolean clear) {
        if (clear) {
            Arrays.fill(fRegs   , 0);
            Arrays.fill(fRegsAlt, 0);
            fIX        = 0;
            fIY        = 0;
            fIndexMode = INDEX_NONE;
            fHalted    = false;
            fInterruptEnabled = false;
        } else {
            setPC(0);
        }
    }

    @Override
    public void pause(final boolean mode) {
        fPause = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Z80";
    }

    @Override
    public void hold(final boolean mode) {
        if ((fHoldPhase.get() == HOLD_IS_NOT_SET) == mode) {
            if (mode) {
                if (Thread.currentThread().getName().equals(ClockSpeedGenerator.THREAD_NAME)) {
                    fHoldPhase.getAndSet(HOLD_ACKNOWLEDGE);
                } else {
                    fHoldPhase.getAndSet(HOLD_IN_PROCESS);
                    while (!fPause && (fHoldPhase.get() == HOLD_IN_PROCESS)) {
                        Thread.yield();
                    }
                }
            } else {
                fHoldPhase.getAndSet(HOLD_IS_NOT_SET);
            }
        }
    }

    @Override
    public boolean isHoldAcknowledge() {
        return fHoldPhase.get() == HOLD_ACKNOWLEDGE;
    }

    @Override
    public void pauseMemoryUnits(final boolean mode) {
        fMemManager.pause(mode);
        if (fIoManager != null) {
            fIoManager.pause(mode);
        }
    }

    @Override
    public void run(final int address) {
        setPC(address);
        fHalted = false;
    }

    @Override
    public void reset(final int address, final boolean resetMemoryUnits) {
        reset(true);
        setPC(address);
        if (resetMemoryUnits) {
            fMemManager.reset(false);
            if (fIoManager != null) {
                fIoManager.reset(false);
            }
        }
    }

    //=-= Исполнение команд =-=

    private int cmdExec() {
        switch (fOpCode) {
            case 0xDD: // Префикс IX
                fIndexMode = INDEX_IX;
                return execPrefixed();
            case 0xFD: // Префикс IY
                fIndexMode = INDEX_IY;
                return execPrefixed();
            case 0xCB: // Набор CB без индексного префикса
                return execCB(nextBytePC(), false);
            case 0xED: // Набор ED
                return execED();
            default:
                return execMain(fOpCode);
        }
    }

    /**
     * Выполняет команду после префикса DD/FD.
     * Повторный префикс и префикс ED выполняются как NOP.
     */
    private int execPrefixed() {
        final int op = nextBytePC();

        switch (op) {
            case 0xDD:
                fIndexMode = INDEX_IX;
                return 4;
            case 0xFD:
                fIndexMode = INDEX_IY;
                return 4;
            case 0xED:
                return 4;
            case 0xCB: { // Индексный набор CB
                final int d = (byte) nextBytePC();
                final int cbOp = nextBytePC();
                return execIndexedCB(d, cbOp);
            }
            default:
                break;
        }

        final int cycles = execMain(op);
        fIndexMode = INDEX_NONE;
        return cycles;
    }

    /**
     * Выполняет команду CB с индексным операндом (IX+d)/(IY+d).
     * Для кодов, отличных от (IX+d), выполняется NOP (обращения к IXH/IXL не поддерживаются).
     */
    private int execIndexedCB(final int d, final int op) {
        final int group = op >> 6;
        final int yyy   = (op >> 3) & 0b111;
        final int address = (getIndex() + d) & 0xFFFF;
        final int operand = fMemManager.readByte(address);
        fIndexMode = INDEX_NONE;

        if (group == 1) {
            // BIT b,(IX+d) — запись в регистр не выполняется (недокументированная форма)
            checkBit(operand, yyy, address);
            return 20;
        }

        final int result = applyCBGroup(group, yyy, operand);
        fMemManager.writeByte(address, result);
        final boolean carry = getFlagC();
        setFlagsSZ5H3P(result);
        setFlagC(carry);
        // Недокументированную запись результата в регистр r не реализуем
        return 23;
    }

    private int applyCBGroup(final int group, final int bit, final int operand) {
        switch (group) {
            case 0:
                return shiftRotate(bit, operand);
            case 2:
                return operand & ~(1 << bit);
            default:
                return operand | (1 << bit);
        }
    }

    private void checkBit(final int operand, final int bit, final int address) {
        final boolean set = (operand & (1 << bit)) != 0;
        fRegs[F] = (fRegs[F] & FLAG_C)
                 | (set ? 0 : (FLAG_Z | FLAG_PV))
                 | (set && (bit == 7) ? FLAG_S : 0)
                 | FLAG_H
                 | ((address >> 8) & (FLAG_F5 | FLAG_F3));
    }

    /**
     * Выполняет команду из набора CB (без индексного префикса).
     *
     * @param op код операции CB
     * @return количество тактов
     */
    private int execCB(final int op, final boolean withIndex) {
        final int group = op >> 6;
        final int yyy   = (op >> 3) & 0b111;
        final int r     =  op       & 0b111;
        final int operand = getReg(r);

        if (group == 1) {
            // BIT b,r
            checkBit(operand, yyy, 0);
            return 8;
        }

        // RLC/RRC/RL/RR/SLA/SRA/SLL/SRL, RES b,r, SET b,r
        final int result = applyCBGroup(group, yyy, operand);
        setReg(r, result);
        final boolean carry = getFlagC();
        setFlagsSZ5H3P(result);
        setFlagC(carry);
        return 8;
    }

    private int shiftRotate(final int kind, final int operand) {
        final int v = operand & 0xFF;
        final int result;
        switch (kind) {
            case 0: // RLC
                result = ((v << 1) | (v >> 7)) & 0xFF;
                setFlagC((v & 0x80) != 0);
                break;
            case 1: // RRC
                result = ((v >> 1) | (v << 7)) & 0xFF;
                setFlagC((v & 0x01) != 0);
                break;
            case 2: // RL
                result = ((v << 1) | (getFlagC() ? 1 : 0)) & 0xFF;
                setFlagC((v & 0x80) != 0);
                break;
            case 3: // RR
                result = ((v >> 1) | (getFlagC() ? 0x80 : 0)) & 0xFF;
                setFlagC((v & 0x01) != 0);
                break;
            case 4: // SLA
                result = (v << 1) & 0xFF;
                setFlagC((v & 0x80) != 0);
                break;
            case 5: // SRA
                result = ((v >> 1) | (v & 0x80)) & 0xFF;
                setFlagC((v & 0x01) != 0);
                break;
            case 6: // SLL — недокументированная команда, выполняем как SLA
                result = (v << 1) & 0xFF;
                setFlagC((v & 0x80) != 0);
                break;
            default: // SRL
                result = (v >> 1) & 0xFF;
                setFlagC((v & 0x01) != 0);
                break;
        }
        return result & 0xFF;
    }

    private int execED() {
        final int op = nextBytePC();

        // Блочные команды
        switch (op) {
            case 0xA0: return blockTransfer(false, false); // LDI
            case 0xA1: return blockCompare (false, false); // CPI
            case 0xA2: return blockInPort  (false, false); // INI
            case 0xA3: return blockOutPort (false, false); // OUTI
            case 0xA8: return blockTransfer(true , false); // LDD
            case 0xA9: return blockCompare (true , false); // CPD
            case 0xAA: return blockInPort  (true , false); // IND
            case 0xAB: return blockOutPort (true , false); // OUTD
            case 0xB0: return blockTransfer(false, true ); // LDIR
            case 0xB1: return blockCompare (false, true ); // CPIR
            case 0xB2: return blockInPort  (false, true ); // INIR
            case 0xB3: return blockOutPort (false, true ); // OTIR
            case 0xB8: return blockTransfer(true , true ); // LDDR
            case 0xB9: return blockCompare (true , true ); // CPDR
            case 0xBA: return blockInPort  (true , true ); // INDR
            case 0xBB: return blockOutPort (true , true ); // OTDR
            default:
                break;
        }

        switch (op) {
            case 0x44: case 0x4C: case 0x54: case 0x5C: // NEG
            case 0x64: case 0x6C: case 0x74: case 0x7C: {
                final int a = fRegs[A];
                fRegs[A] = 0;
                subByte(0, a, false, true);
                return 8;
            }
            case 0x45: case 0x4D: case 0x55: case 0x5D: // RETN
            case 0x65: case 0x6D: case 0x75: case 0x7D: // RETI
                setPC(popWord());
                fInterruptEnabled = true;
                return 14;

            case 0x46: case 0x4E: case 0x66: case 0x6E: // IM 0 / IM 0/1
            case 0x56: case 0x76:                        // IM 1
            case 0x5E: case 0x7E:                        // IM 2
                // Режимы прерываний вне поддерживаемого набора
                return 8;

            case 0x57: // LD A,I
            case 0x5F: // LD A,R
                // Регистры I и R считаем нулевыми
                fRegs[A] = 0;
                setFlagsSZ5H3(0);
                if (fInterruptEnabled) {
                    fRegs[F] |= FLAG_PV;
                }
                return 9;
            case 0x47: // LD I,A
            case 0x4F: // LD R,A
                // Регистры I и R не реализуются
                return 9;

            case 0x67: // RRD
            case 0x6F: { // RLD
                final int hl  = getHL();
                final int mem = fMemManager.readByte(hl);
                final int a   = fRegs[A];
                if (op == 0x67) { // RRD
                    fMemManager.writeByte(hl, ((a & 0x0F) << 4) | (mem >> 4));
                    fRegs[A] = (a & 0xF0) | (mem & 0x0F);
                } else {          // RLD
                    fMemManager.writeByte(hl, ((mem << 4) | (a & 0x0F)) & 0xFF);
                    fRegs[A] = (a & 0xF0) | (mem >> 4);
                }
                fRegs[F] = (fRegs[F] & FLAG_C)
                         | (fRegs[A] & (FLAG_S | FLAG_F5 | FLAG_F3))
                         | (fRegs[A] == 0 ? FLAG_Z : 0)
                         | (PARITY[fRegs[A]] ? FLAG_PV : 0);
                return 18;
            }

            default:
                break;
        }

        if ((op & 0b1100_0111) == 0b0100_0000) { // IN r,(C)
            final int value = inPort(fRegs[C]);
            setFlagsSZ5H3P(value);
            final int dst = (op >> 3) & 0b111;
            if (dst != 0b110) {
                fRegs[dst] = value & 0xFF;
            }
            return 12;
        }
        if ((op & 0b1100_0111) == 0b0100_0001) { // OUT (C),r
            final int src = (op >> 3) & 0b111;
            outPort(fRegs[C], src == 0b110 ? 0 : fRegs[src]);
            return 12;
        }

        switch (op) {
            // ADC HL,ss / SBC HL,ss
            case 0x4A: return adcHL(getRegPair(P_BC));
            case 0x5A: return adcHL(getRegPair(P_DE));
            case 0x6A: return adcHL(getRegPair(P_HL));
            case 0x7A: return adcHL(getSP());
            case 0x42: return sbcHL(getRegPair(P_BC));
            case 0x52: return sbcHL(getRegPair(P_DE));
            case 0x62: return sbcHL(getRegPair(P_HL));
            case 0x72: return sbcHL(getSP());
            default:
                break;
        }

        // Остальные ED-коды — недокументированные, выполняем как NOP
        return 8;
    }

    private int adcHL(final int value) {
        final int hl  = getHL();
        final int c   = getFlagC() ? 1 : 0;
        final int res = hl + value + c;
        final int h   = ((hl & 0x0FFF) + (value & 0x0FFF) + c) > 0x0FFF ? FLAG_H : 0;
        final int ovf = ((((hl ^ res) & (value ^ res)) & 0x8000) != 0) ? FLAG_PV : 0;
        final int r   = res & 0xFFFF;
        setHL(r);
        fRegs[F] = ((r >> 8) & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (r == 0 ? FLAG_Z : 0)
                 | h | ovf | ((res > 0xFFFF) ? FLAG_C : 0);
        return 15;
    }

    private int sbcHL(final int value) {
        final int hl  = getHL();
        final int c   = getFlagC() ? 1 : 0;
        final int res = hl - value - c;
        final int h   = ((hl & 0x0FFF) - (value & 0x0FFF) - c) < 0 ? FLAG_H : 0;
        final int ovf = ((((hl ^ value) & (hl ^ res)) & 0x8000) != 0) ? FLAG_PV : 0;
        final int r   = res & 0xFFFF;
        setHL(r);
        fRegs[F] = ((r >> 8) & (FLAG_S | FLAG_F5 | FLAG_F3))
                 | (r == 0 ? FLAG_Z : 0)
                 | h | ovf | FLAG_N | ((res < 0) ? FLAG_C : 0);
        return 15;
    }

    private int blockTransfer(final boolean decrement, final boolean repeat) {
        final int step = decrement ? -1 : 1;
        final int hl   = getHL();
        final int de   = getRegPair(P_DE);
        final int val  = fMemManager.readByte(hl);
        fMemManager.writeByte(de, val);
        setHL((hl + step) & 0xFFFF);
        setRegPair(P_DE, (de + step) & 0xFFFF);
        final int bc = (getRegPair(P_BC) - 1) & 0xFFFF;
        setRegPair(P_BC, bc);

        fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_C))
                 | (bc != 0 ? FLAG_PV : 0);

        if (repeat && (bc != 0)) {
            setPC((getPC() - 2) & 0xFFFF);
            return 21;
        }
        return 16;
    }

    private int blockCompare(final boolean decrement, final boolean repeat) {
        final int step = decrement ? -1 : 1;
        final int hl   = getHL();
        final int val  = fMemManager.readByte(hl);
        final int a    = fRegs[A];
        final int res  = (a - val) & 0xFF;
        final int h    = ((a & 0x0F) - (val & 0x0F)) < 0 ? FLAG_H : 0;

        setHL((hl + step) & 0xFFFF);
        final int bc = (getRegPair(P_BC) - 1) & 0xFFFF;
        setRegPair(P_BC, bc);

        fRegs[F] = (fRegs[F] & FLAG_C)
                 | (res & (FLAG_S | FLAG_F3 | FLAG_F5))
                 | (res == 0 ? FLAG_Z : 0)
                 | h
                 | (bc != 0 ? FLAG_PV : 0)
                 | FLAG_N;

        if (repeat && (bc != 0) && (res != 0)) {
            setPC((getPC() - 2) & 0xFFFF);
            return 21;
        }
        return 16;
    }

    private int blockInPort(final boolean decrement, final boolean repeat) {
        final int value = inPort(fRegs[C]);
        fMemManager.writeByte(getHL(), value);
        setHL((getHL() + (decrement ? -1 : 1)) & 0xFFFF);
        final int b = (fRegs[B] - 1) & 0xFF;
        fRegs[B] = b;
        fRegs[F] = (fRegs[F] & FLAG_C)
                 | (value & (FLAG_S | FLAG_F3 | FLAG_F5))
                 | (b == 0 ? FLAG_Z : 0)
                 | FLAG_N;

        if (repeat && (b != 0)) {
            setPC((getPC() - 2) & 0xFFFF);
            return 21;
        }
        return 16;
    }

    private int blockOutPort(final boolean decrement, final boolean repeat) {
        final int value = fMemManager.readByte(getHL());
        outPort(fRegs[C], value);
        setHL((getHL() + (decrement ? -1 : 1)) & 0xFFFF);
        final int b = (fRegs[B] - 1) & 0xFF;
        fRegs[B] = b;
        fRegs[F] = (fRegs[F] & FLAG_C)
                 | (value & (FLAG_S | FLAG_F3 | FLAG_F5))
                 | (b == 0 ? FLAG_Z : 0)
                 | FLAG_N;

        if (repeat && (b != 0)) {
            setPC((getPC() - 2) & 0xFFFF);
            return 21;
        }
        return 16;
    }

    /**
     * Читает порт ввода. При отсутствии менеджера устройств ввода/вывода
     * используется та же схема, что и в CPUi8080: чтение по адресу (port | port &lt;&lt; 8) в памяти.
     */
    private int inPort(final int port) {
        final int p = port & 0xFF;
        if (fIoManager == null) {
            return fMemManager.readByte(p | (p << 8));
        }
        return fIoManager.readByte(p);
    }

    private void outPort(final int port, final int value) {
        final int p = port & 0xFF;
        if (fIoManager == null) {
            fMemManager.writeByte(p | (p << 8), value & 0xFF);
        } else {
            fIoManager.writeByte(p, value & 0xFF);
        }
    }

    //=-= Основной набор команд =-=

    private int execMain(final int op) {
        final boolean indexed = fIndexMode != INDEX_NONE;

        switch (op) {
            case 0x00: // NOP
                return 4;

            case 0x08: // EX AF,AF'
                swapRegs(A, F);
                return 4;
            case 0xD9: // EXX
                swapRegs(B, C);
                swapRegs(D, E);
                swapRegs(H, L);
                return 4;
            case 0x76: // HALT
                fHalted = true;
                // Оставляем PC на байте HALT: отладчик показывает остановленную команду,
                // а продолжение исполнения сдвигает PC с неё (см. debugResumeFromHalt)
                setPC((getPC() - 1) & 0xFFFF);
                startDebugger();
                return 4;

            case 0x01: // LD BC,nn
                setRegPair(P_BC, nextWordPC());
                return 10;
            case 0x11: // LD DE,nn
                setRegPair(P_DE, nextWordPC());
                return 10;
            case 0x21: { // LD HL,nn / LD IX,nn / LD IY,nn
                final int value = nextWordPC();
                if (indexed) {
                    setIndex(value);
                    return 14;
                }
                setHL(value);
                return 10;
            }
            case 0x31: // LD SP,nn
                setSP(nextWordPC());
                return 10;

            case 0x02: // LD (BC),A
                fMemManager.writeByte(getRegPair(P_BC), fRegs[A]);
                return 7;
            case 0x12: // LD (DE),A
                fMemManager.writeByte(getRegPair(P_DE), fRegs[A]);
                return 7;
            case 0x22: { // LD (nn),HL / LD (nn),IX / LD (nn),IY
                final int addr = nextWordPC();
                final int word = indexed ? getIndex() : getHL();
                fMemManager.writeByte(addr, word & 0xFF);
                fMemManager.writeByte((addr + 1) & 0xFFFF, (word >> 8) & 0xFF);
                return indexed ? 20 : 16;
            }
            case 0x32: // LD (nn),A
                fMemManager.writeByte(nextWordPC(), fRegs[A]);
                return 13;

            case 0x0A: // LD A,(BC)
                fRegs[A] = fMemManager.readByte(getRegPair(P_BC));
                return 7;
            case 0x1A: // LD A,(DE)
                fRegs[A] = fMemManager.readByte(getRegPair(P_DE));
                return 7;
            case 0x2A: { // LD HL,(nn) / LD IX,(nn) / LD IY,(nn)
                final int addr = nextWordPC();
                final int word = fMemManager.readByte(addr) | (fMemManager.readByte((addr + 1) & 0xFFFF) << 8);
                if (indexed) {
                    setIndex(word);
                    return 20;
                }
                setHL(word);
                return 16;
            }
            case 0x3A: // LD A,(nn)
                fRegs[A] = fMemManager.readByte(nextWordPC());
                return 13;

            case 0x03: // INC BC
                setRegPair(P_BC, (getRegPair(P_BC) + 1) & 0xFFFF);
                return 6;
            case 0x13: // INC DE
                setRegPair(P_DE, (getRegPair(P_DE) + 1) & 0xFFFF);
                return 6;
            case 0x23: // INC HL / INC IX / INC IY
                if (indexed) {
                    setIndex((getIndex() + 1) & 0xFFFF);
                    return 10;
                }
                setHL((getHL() + 1) & 0xFFFF);
                return 6;
            case 0x33: // INC SP
                setSP((getSP() + 1) & 0xFFFF);
                return 6;

            case 0x0B: // DEC BC
                setRegPair(P_BC, (getRegPair(P_BC) - 1) & 0xFFFF);
                return 6;
            case 0x1B: // DEC DE
                setRegPair(P_DE, (getRegPair(P_DE) - 1) & 0xFFFF);
                return 6;
            case 0x2B: // DEC HL / DEC IX / DEC IY
                if (indexed) {
                    setIndex((getIndex() - 1) & 0xFFFF);
                    return 10;
                }
                setHL((getHL() - 1) & 0xFFFF);
                return 6;
            case 0x3B: // DEC SP
                setSP((getSP() - 1) & 0xFFFF);
                return 6;

            case 0x04: case 0x0C: case 0x14: case 0x1C: // INC r
            case 0x24: case 0x2C: case 0x34: case 0x3C: {
                final int r = (op >> 3) & 0b111;
                if (indexed) {
                    return execIndexedIncDec(r, true);
                }
                setReg(r, incByte(getReg(r)));
                return (r == M) ? 11 : 4;
            }
            case 0x05: case 0x0D: case 0x15: case 0x1D: // DEC r
            case 0x25: case 0x2D: case 0x35: case 0x3D: {
                final int r = (op >> 3) & 0b111;
                if (indexed) {
                    return execIndexedIncDec(r, false);
                }
                setReg(r, decByte(getReg(r)));
                return (r == M) ? 11 : 4;
            }

            case 0x06: case 0x0E: case 0x16: case 0x1E: // LD r,n
            case 0x26: case 0x2E: case 0x36: case 0x3E: {
                final int r = (op >> 3) & 0b111;
                if (indexed) {
                    return execIndexedLoadImm(r);
                }
                setReg(r, nextBytePC());
                return (r == M) ? 10 : 7;
            }

            case 0x07: { // RLCA
                final int a = fRegs[A];
                final boolean c = (a & 0x80) != 0;
                fRegs[A] = ((a << 1) | (c ? 1 : 0)) & 0xFF;
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | (c ? FLAG_C : 0);
                return 4;
            }
            case 0x0F: { // RRCA
                final int a = fRegs[A];
                final boolean c = (a & 0x01) != 0;
                fRegs[A] = ((a >> 1) | (c ? 0x80 : 0)) & 0xFF;
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | (c ? FLAG_C : 0);
                return 4;
            }
            case 0x17: { // RLA
                final int a = fRegs[A];
                final boolean c = (a & 0x80) != 0;
                fRegs[A] = ((a << 1) | (getFlagC() ? 1 : 0)) & 0xFF;
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | (c ? FLAG_C : 0);
                return 4;
            }
            case 0x1F: { // RRA
                final int a = fRegs[A];
                final boolean c = (a & 0x01) != 0;
                fRegs[A] = ((a >> 1) | (getFlagC() ? 0x80 : 0)) & 0xFF;
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | (c ? FLAG_C : 0);
                return 4;
            }
            case 0x27: // DAA
                cmdDAA();
                return 4;
            case 0x2F: // CPL
                fRegs[A] = (~fRegs[A]) & 0xFF;
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV | FLAG_C))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | FLAG_H | FLAG_N;
                return 4;
            case 0x37: // SCF
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | FLAG_C;
                return 4;
            case 0x3F: // CCF
                fRegs[F] = (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
                         | (fRegs[A] & (FLAG_F5 | FLAG_F3))
                         | (getFlagC() ? FLAG_H : FLAG_C);
                return 4;

            case 0x09: // ADD HL,BC / ADD IX,BC / ADD IY,BC
                addWord(getRegPair(P_BC), indexed);
                return indexed ? 15 : 11;
            case 0x19: // ADD HL,DE / ADD IX,DE / ADD IY,DE
                addWord(getRegPair(P_DE), indexed);
                return indexed ? 15 : 11;
            case 0x29: // ADD HL,HL / ADD IX,IX / ADD IY,IY
                addWord(indexed ? getIndex() : getHL(), indexed);
                return indexed ? 15 : 11;
            case 0x39: // ADD HL,SP / ADD IX,SP / ADD IY,SP
                addWord(getSP(), indexed);
                return indexed ? 15 : 11;

            case 0x10: { // DJNZ e
                final int d = (byte) nextBytePC();
                fRegs[B] = (fRegs[B] - 1) & 0xFF;
                if (fRegs[B] != 0) {
                    setPC((getPC() + d) & 0xFFFF);
                    return 13;
                }
                return 8;
            }
            case 0x18: { // JR e
                final int d = (byte) nextBytePC();
                setPC((getPC() + d) & 0xFFFF);
                return 12;
            }
            case 0x20: case 0x28: case 0x30: case 0x38: { // JR cc,e
                final int d = (byte) nextBytePC();
                if (testFlags((op >> 3) & 0b011)) {
                    setPC((getPC() + d) & 0xFFFF);
                    return 12;
                }
                return 7;
            }

            case 0x40: case 0x41: case 0x42: case 0x43: // LD r,r'
            case 0x44: case 0x45: case 0x46: case 0x47:
            case 0x48: case 0x49: case 0x4A: case 0x4B:
            case 0x4C: case 0x4D: case 0x4E: case 0x4F:
            case 0x50: case 0x51: case 0x52: case 0x53:
            case 0x54: case 0x55: case 0x56: case 0x57:
            case 0x58: case 0x59: case 0x5A: case 0x5B:
            case 0x5C: case 0x5D: case 0x5E: case 0x5F:
            case 0x60: case 0x61: case 0x62: case 0x63:
            case 0x64: case 0x65: case 0x66: case 0x67:
            case 0x68: case 0x69: case 0x6A: case 0x6B:
            case 0x6C: case 0x6D: case 0x6E: case 0x6F:
            case 0x70: case 0x71: case 0x72: case 0x73:
            case 0x74: case 0x75: case 0x77:
            case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F: {
                final int dst = (op >> 3) & 0b111;
                final int src =  op       & 0b111;
                if (indexed) {
                    return execIndexedLoad(dst, src);
                }
                setReg(dst, getReg(src));
                return ((dst == M) || (src == M)) ? 7 : 4;
            }

            case 0x80: case 0x81: case 0x82: case 0x83: // ADD A,r
            case 0x84: case 0x85: case 0x86: case 0x87:
                return execALU(op, 0);
            case 0x88: case 0x89: case 0x8A: case 0x8B: // ADC A,r
            case 0x8C: case 0x8D: case 0x8E: case 0x8F:
                return execALU(op, 1);
            case 0x90: case 0x91: case 0x92: case 0x93: // SUB r
            case 0x94: case 0x95: case 0x96: case 0x97:
                return execALU(op, 2);
            case 0x98: case 0x99: case 0x9A: case 0x9B: // SBC A,r
            case 0x9C: case 0x9D: case 0x9E: case 0x9F:
                return execALU(op, 3);
            case 0xA0: case 0xA1: case 0xA2: case 0xA3: // AND r
            case 0xA4: case 0xA5: case 0xA6: case 0xA7:
                return execALU(op, 4);
            case 0xA8: case 0xA9: case 0xAA: case 0xAB: // XOR r
            case 0xAC: case 0xAD: case 0xAE: case 0xAF:
                return execALU(op, 5);
            case 0xB0: case 0xB1: case 0xB2: case 0xB3: // OR r
            case 0xB4: case 0xB5: case 0xB6: case 0xB7:
                return execALU(op, 6);
            case 0xB8: case 0xB9: case 0xBA: case 0xBB: // CP r
            case 0xBC: case 0xBD: case 0xBE: case 0xBF:
                return execALU(op, 7);

            case 0xC0: case 0xC8: case 0xD0: case 0xD8: // RET cc
            case 0xE0: case 0xE8: case 0xF0: case 0xF8:
                if (testFlags((op >> 3) & 0b111)) {
                    setPC(popWord());
                    return 11;
                }
                return 5;

            case 0xC9: // RET
                setPC(popWord());
                return 10;

            case 0xC1: // POP BC
                setRegPair(P_BC, popWord());
                return 10;
            case 0xD1: // POP DE
                setRegPair(P_DE, popWord());
                return 10;
            case 0xE1: // POP HL / POP IX / POP IY
                if (indexed) {
                    setIndex(popWord());
                    return 14;
                }
                setHL(popWord());
                return 10;
            case 0xF1: // POP AF
                setRegPair(P_PSW, popWord());
                return 10;

            case 0xC5: // PUSH BC
                pushWord(getRegPair(P_BC));
                return 11;
            case 0xD5: // PUSH DE
                pushWord(getRegPair(P_DE));
                return 11;
            case 0xE5: // PUSH HL / PUSH IX / PUSH IY
                if (indexed) {
                    pushWord(getIndex());
                    return 15;
                }
                pushWord(getHL());
                return 11;
            case 0xF5: // PUSH AF
                pushWord(getRegPair(P_PSW));
                return 11;

            case 0xC2: case 0xCA: case 0xD2: case 0xDA: // JP cc,nn
            case 0xE2: case 0xEA: case 0xF2: case 0xFA: {
                final int addr = nextWordPC();
                if (testFlags((op >> 3) & 0b111)) {
                    setPC(addr);
                }
                return 10;
            }
            case 0xC3: // JP nn
                setPC(nextWordPC());
                return 10;

            case 0xC4: case 0xCC: case 0xD4: case 0xDC: // CALL cc,nn
            case 0xE4: case 0xEC: case 0xF4: case 0xFC: {
                final int addr = nextWordPC();
                if (testFlags((op >> 3) & 0b111)) {
                    pushWord(getPC());
                    setPC(addr);
                    return 17;
                }
                return 10;
            }
            case 0xCD: { // CALL nn
                final int addr = nextWordPC();
                pushWord(getPC());
                setPC(addr);
                return 17;
            }

            case 0xC6: // ADD A,n
                addByte(fRegs[A], nextBytePC(), false);
                return 7;
            case 0xCE: // ADC A,n
                addByte(fRegs[A], nextBytePC(), true);
                return 7;
            case 0xD6: // SUB n
                subByte(fRegs[A], nextBytePC(), false, true);
                return 7;
            case 0xDE: // SBC A,n
                subByte(fRegs[A], nextBytePC(), true, true);
                return 7;
            case 0xE6: // AND n
                andByte(fRegs[A], nextBytePC());
                return 7;
            case 0xEE: // XOR n
                xorByte(fRegs[A], nextBytePC());
                return 7;
            case 0xF6: // OR n
                orByte(fRegs[A], nextBytePC());
                return 7;
            case 0xFE: // CP n
                subByte(fRegs[A], nextBytePC(), false, false);
                return 7;

            case 0xD3: { // OUT (n),A
                final int port = nextBytePC();
                outPort(port, fRegs[A]);
                return 11;
            }
            case 0xDB: { // IN A,(n)
                final int port = nextBytePC();
                fRegs[A] = inPort(port) & 0xFF;
                return 11;
            }

            case 0xE3: { // EX (SP),HL / EX (SP),IX / EX (SP),IY
                final int sp  = getSP();
                final int mem = fMemManager.readByte(sp) | (fMemManager.readByte((sp + 1) & 0xFFFF) << 8);
                final int reg = indexed ? getIndex() : getHL();
                fMemManager.writeByte(sp, reg & 0xFF);
                fMemManager.writeByte((sp + 1) & 0xFFFF, (reg >> 8) & 0xFF);
                if (indexed) {
                    setIndex(mem);
                    return 23;
                }
                setHL(mem);
                return 19;
            }
            case 0xE9: // JP (HL) / JP (IX) / JP (IY)
                setPC(indexed ? getIndex() : getHL());
                return indexed ? 8 : 4;
            case 0xEB: { // EX DE,HL
                final int de = getRegPair(P_DE);
                setRegPair(P_DE, getHL());
                setHL(de);
                return 4;
            }
            case 0xF9: // LD SP,HL / LD SP,IX / LD SP,IY
                setSP(indexed ? getIndex() : getHL());
                return indexed ? 10 : 6;

            case 0xF3: // DI
                fInterruptEnabled = false;
                return 4;
            case 0xFB: // EI
                fInterruptEnabled = true;
                return 4;

            case 0xC7: case 0xCF: case 0xD7: case 0xDF: // RST
            case 0xE7: case 0xEF: case 0xF7: case 0xFF:
                pushWord(getPC());
                setPC(op & 0b0011_1000);
                return 11;

            default:
                // Недокументированный байт — NOP
                return 4;
        }
    }

    /**
     * Выполняет арифметико-логическую операцию с регистром.
     *
     * @param op   код команды
     * @param kind 0 = ADD, 1 = ADC, 2 = SUB, 3 = SBC, 4 = AND, 5 = XOR, 6 = OR, 7 = CP
     * @return количество тактов
     */
    private int execALU(final int op, final int kind) {
        final int r = op & 0b111;

        if (fIndexMode != INDEX_NONE) {
            if ((r == H) || (r == L)) {
                // IXH/IXL — недокументированные, выполняем как NOP
                return 11;
            }
            final int d = (byte) nextBytePC();
            final int operand = (r == M)
                              ? fMemManager.readByte((getIndex() + d) & 0xFFFF)
                              : fRegs[r];
            execALUOperation(kind, operand);
            return (r == M) ? 19 : 11;
        }

        execALUOperation(kind, getReg(r));
        return (r == M) ? 7 : 4;
    }

    private void execALUOperation(final int kind, final int operand) {
        switch (kind) {
            case 0:
                addByte(fRegs[A], operand, false);
                break;
            case 1:
                addByte(fRegs[A], operand, true);
                break;
            case 2:
                subByte(fRegs[A], operand, false, true);
                break;
            case 3:
                subByte(fRegs[A], operand, true, true);
                break;
            case 4:
                andByte(fRegs[A], operand);
                break;
            case 5:
                xorByte(fRegs[A], operand);
                break;
            case 6:
                orByte(fRegs[A], operand);
                break;
            default:
                subByte(fRegs[A], operand, false, false);
                break;
        }
    }

    /**
     * INC/DEC (IX+d) или INC/DEC r при индексном префиксе.
     */
    private int execIndexedIncDec(final int r, final boolean increment) {
        if (r == M) {
            final int d = (byte) nextBytePC();
            final int address = (getIndex() + d) & 0xFFFF;
            final int value = fMemManager.readByte(address);
            fMemManager.writeByte(address, increment ? incByte(value) : decByte(value));
            return 23;
        }
        if ((r == H) || (r == L)) {
            // IXH/IXL — недокументированные, выполняем как NOP
            return 8;
        }
        fRegs[r] = increment ? incByte(fRegs[r]) : decByte(fRegs[r]);
        return 8;
    }

    /**
     * LD r,n при индексном префиксе.
     */
    private int execIndexedLoadImm(final int r) {
        if (r == M) {
            final int d = (byte) nextBytePC();
            final int value = nextBytePC();
            fMemManager.writeByte((getIndex() + d) & 0xFFFF, value);
            return 19;
        }
        if ((r == H) || (r == L)) {
            // IXH/IXL — недокументированные, выполняем как NOP
            return 11;
        }
        fRegs[r] = nextBytePC();
        return 11;
    }

    /**
     * LD r,r' при индексном префиксе.
     */
    private int execIndexedLoad(final int dst, final int src) {
        if ((dst == M) && (src == M)) {
            // LD (IX+d),(IX+d) — недокументированная, выполняем как NOP
            return 19;
        }
        if ((dst == M) || (src == M)) {
            final int d = (byte) nextBytePC();
            final int address = (getIndex() + d) & 0xFFFF;
            if (dst == M) {
                fMemManager.writeByte(address, fRegs[src]);
            } else {
                fRegs[dst] = fMemManager.readByte(address);
            }
            return 19;
        }
        if ((dst == H) || (dst == L) || (src == H) || (src == L)) {
            // IXH/IXL/IYH/IYL — недокументированные, выполняем как NOP
            return 11;
        }
        fRegs[dst] = fRegs[src];
        return 11;
    }

    /**
     * Флаги результата 16-битного прибавления (ADD HL,ss / ADD IX,pp).
     * <p>
     * S, Z, P/V сохраняются; N сбрасывается; H — перенос из бита 11, C — перенос из бита 15;
     * недокументированные F5/F3 — копии битов 5 и 3 старшего байта результата.
     *
     * @param a   первое слагаемое
     * @param b   второе слагаемое
     * @param res 32-битная сумма a + b (старшие разряды нужны для переноса из бита 15)
     * @return маска флагов для записи в регистр F
     */
    private int addWordFlags(final int a, final int b, final int res) {
        final int r = res & 0xFFFF;
        final int h = ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF ? FLAG_H : 0;
        return (fRegs[F] & (FLAG_S | FLAG_Z | FLAG_PV))
             | ((r >> 8) & (FLAG_F5 | FLAG_F3))
             | h | ((res > 0xFFFF) ? FLAG_C : 0);
    }

    /**
     * ADD HL,ss / ADD IX,ss / ADD IY,ss.
     *
     * @param value    прибавляемая регистровая пара
     * @param indexed  true = складывать с IX/IY (префикс DD/FD), false = с HL
     */
    private void addWord(final int value, final boolean indexed) {
        final int dst = indexed ? getIndex() : getHL();
        final int res = dst + value;
        if (indexed) {
            setIndex(res & 0xFFFF);
        } else {
            setHL(res & 0xFFFF);
        }
        fRegs[F] = addWordFlags(dst, value, res);
    }
}
