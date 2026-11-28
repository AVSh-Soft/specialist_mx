package ru.avsh.specialist.mx.units.types;

import javafx.application.Platform;
import org.jetbrains.annotations.Nullable;
import ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPU;
import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.gui.swing.utils.StubMainFrame;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.ClockSpeedGenerator;
import ru.avsh.specialist.mx.utils.Constants;
import ru.avsh.specialist.mx.utils.Trap;

import java.awt.*;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.avsh.specialist.mx.utils.Constants.SPMX_ICON_FILE;

/**
 * Абстрактный класс "Процессор, поддерживающий отладку".
 * Расширяет общий контракт процессора операциями, которые нужны только окну отладчика:
 * доступом к регистровым парам и управлением ловушками (точками останова).
 * <p>
 * Ядро, тактовый генератор и устройства работают через {@link CpuCore} и не должны
 * зависеть от этого класса.
 *
 * @author -=AVSh=-
 */
public abstract class CpuDebugCore implements CpuCore {
    protected final SpecialistMX fSpMX;

    private final  AtomicBoolean  fIsDebugRun ;
    private final           Trap  fCompareTrap;
    private final SortedSet<Trap> fTraps      ;

    private volatile boolean fTrapsFlag   ;
    private volatile    Trap fTrapStepOver;

    protected CpuDebugCore(@Nullable SpecialistMX fSpMX) {
        this.fSpMX        = fSpMX;
        this.fIsDebugRun  = new AtomicBoolean();
        this.fCompareTrap = new Trap(0,0);
        this.fTraps       = new ConcurrentSkipListSet<>();
    }

    /**
     * Показывает, какой процессор реализует отладочный контракт.
     * (Нужен для заголовка окна отладчика и строки флагов)
     *
     * @return true = Z80, false = I8080
     */
    public abstract boolean debugIsZ80();

    /**
     * Получает значение регистровой пары CPU.
     * Старший байт результата - первый регистр пары, младший байт - второй
     * (для AF старший байт - A, младший - F; для SP и PC - адрес целиком).
     *
     * @param regPair регистровая пара
     * @return значение регистровой пары или -1, если пара не поддерживается
     */
    public abstract int debugGetValRegPair(final DebugRegPair regPair);

    /**
     * Устанавливает значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @param value   значение регистровой пары
     */
    public abstract void debugSetValRegPair(final DebugRegPair regPair, final int value);

    /**
     * Запускает отладчик.
     * Общая реализация для обоих процессоров: останавливаем CPU и запоминающие
     * устройства, затем из потока Swing останавливаем тактовый генератор и
     * открываем окно отладчика.
     * Вызов возможен только для потока тактового генератора (блокирует вызов из потока FX/Swing).
     */
    public void startDebugger() {
        if ((fSpMX != null) && !fIsDebugRun.get()
                && Thread.currentThread().getName().equals(ClockSpeedGenerator.THREAD_NAME)) {
            // Блокируем возможность одновременного запуска нескольких копий отладчика
            fIsDebugRun.getAndSet(true);
            // Останавливаем CPU и запоминающие устройства
                        hold(true);
            pauseMemoryUnits(true);
            // Далее работаем в потоке Swing
            EventQueue.invokeLater(() -> {
                // Останавливаем тактовый генератор (обязательно из другого потока!)
                fSpMX.pause(true, false);
                // Блокируем главное окно
                Platform.runLater(() -> fSpMX.setPrimaryStagePeerEnabled(false));
                try (final StubMainFrame mainFrame = StubMainFrame.create(
                        DebuggerCPU.getTitle(debugIsZ80()), Constants.getURL(SPMX_ICON_FILE).orElse(null))) {
                    // Отменяем режим "Пауза" только для CPU
                    hold(false);
                    // Удаляем StepOver ловушку, если она вызвала отладчик
                    final int page    = fSpMX.getPage();
                    final int address = debugGetValRegPair(DebugRegPair.PC);
                    if (debugIsStepOverTrap(page, address)) {
                               debugRemTrap(page, address);
                    }
                    // Выводим окно отладчика
                    final DebuggerCPU debug = new DebuggerCPU(mainFrame, fSpMX);
                    // После окончания работы - убиваем отладчик
                    debug.getContentPane().removeAll();
                    debug.dispose();
                } finally {
                    // Отменяем блокировку главного окна
                    Platform.runLater(() -> fSpMX.setPrimaryStagePeerEnabled(true));
                    // Запускаем тактовый генератор и запоминающие устройства
                    fSpMX.pause(false, true);
                    // Разрешаем запускать отладчик
                    fIsDebugRun.getAndSet(false);
                }
            });
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
    public void debugClearTraps() {
        fTraps.clear();
        fTrapStepOver =  null;
        fTrapsFlag    = false;
    }

    /**
     * Проверяет, установлена ли по заданному адресу ловушка.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка установлена
     */
    public boolean debugIsTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        return fTrapsFlag && !trap.equals(fTrapStepOver) && (fTraps.contains(trap));
    }

    /**
     * Проверяет, установлена ли по заданному адресу StepOver ловушка.
     * (Метод для вызова из отладчика)
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка StepOver установлена
     */
    public boolean debugIsStepOverTrap(final int page, final int address) {
        final Trap trap = new Trap(page, address);
        return fTrapsFlag && trap.equals(fTrapStepOver) && (fTraps.contains(trap));
    }

    /**
     * Возвращает количество установленных ловушек.
     * (Метод для вызова из отладчика)
     *
     * @return количество установленных ловушек
     */
    public int debugGetTrapCount() {
        return ((fTrapStepOver != null) && fTraps.contains(fTrapStepOver)) ? (fTraps.size() - 1) : fTraps.size();
    }

    /**
     * Возвращает ловушку по индексу в списке ловушек.
     * (Метод для вызова из отладчика)
     *
     * @param index индекс ловушки
     * @return ловушка
     */
    public Trap debugGetTrap(final int index) {
        return fTraps.stream().filter(trap -> !trap.equals(fTrapStepOver)).skip(index).findFirst().orElse(null);
    }

    /**
     * Возвращает индекс ловушки.
     * (Метод для вызова из отладчика)
     *
     * @param trap ловушка
     * @return индекс
     */
    public int debugGetTrapIndex(final Trap trap) {
        return new ArrayList<>(fTraps).indexOf(trap);
    }

    /**
     * Проверяем ловушку для следующей команды CPU.
     * @param pc значение PC
     */
    protected void checkTraps(final int pc) {
        // Проверяем ловушку для следующей команды CPU
        if (fTrapsFlag) {
            // Здесь без порождения новых объектов (для скорости)
            fCompareTrap.change((fSpMX != null) ? fSpMX.getPage() : 0, pc);
            if (fTraps.contains(fCompareTrap)) {
                startDebugger();
            }
        }
    }
}
