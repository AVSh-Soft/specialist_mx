package ru.avsh.specialist.mx.units.types;

import javafx.application.Platform;
import org.jetbrains.annotations.Nullable;
import ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPU;
import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.gui.swing.utils.StubMainFrame;
import ru.avsh.specialist.mx.helpers.Constants;
import ru.avsh.specialist.mx.helpers.Trap;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.ClockSpeedGenerator;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.avsh.specialist.mx.helpers.Constants.SPMX_ICON_FILE;

/**
 * Интерфейс "Процессор, поддерживающий отладку".
 * Расширяет общий контракт процессора операциями, которые нужны только окну отладчика:
 * доступом к регистровым парам и управлением ловушками (точками останова).
 * <p>
 * Ядро, тактовый генератор и устройства работают через {@link CpuCore} и не должны
 * зависеть от этого интерфейса.
 *
 * @author -=AVSh=-
 */
public interface CpuDebugCore extends CpuCore {
    /**
     * Показывает, какой процессор реализует отладочный контракт.
     * (Нужен для заголовка окна отладчика и строки флагов)
     *
     * @return true = Z80, false = I8080
     */
    boolean debugIsZ80();

    /**
     * Возвращает ссылку на эмулятор, который создал процессор.
     * (Нужна только запуску отладчика; в автономном режиме - null)
     *
     * @return ссылка на эмулятор или null
     */
    @Nullable
    SpecialistMX debugGetEmulator();

    /**
     * Возвращает признак "отладчик уже запущен".
     * (Блокирует одновременный запуск нескольких копий отладчика)
     *
     * @return флаг запуска отладчика
     */
    AtomicBoolean debugGetDebugRunFlag();

    /**
     * Запускает отладчик.
     * Общая реализация для обоих процессоров: останавливаем CPU и запоминающие
     * устройства, затем из потока Swing останавливаем тактовый генератор и
     * открываем окно отладчика.
     * Вызов возможен только для потока тактового генератора (блокирует вызов из потока FX/Swing).
     */
    default void startDebugger() {
        final SpecialistMX spMX = debugGetEmulator();
        final AtomicBoolean isDebugRun = debugGetDebugRunFlag();
        if ((spMX != null) && !isDebugRun.get()
                && Thread.currentThread().getName().equals(ClockSpeedGenerator.THREAD_NAME)) {
            // Блокируем возможность одновременного запуска нескольких копий отладчика
            isDebugRun.getAndSet(true);
            // Останавливаем CPU и запоминающие устройства
                        hold(true);
            pauseMemoryUnits(true);
            // Далее работаем в потоке Swing
            EventQueue.invokeLater(() -> {
                // Останавливаем тактовый генератор (обязательно из другого потока!)
                spMX.pause(true, false);
                // Блокируем главное окно
                Platform.runLater(() -> spMX.setPrimaryStagePeerEnabled(false));
                try (final StubMainFrame mainFrame = StubMainFrame.create(
                        DebuggerCPU.getTitle(debugIsZ80()), Constants.getURL(SPMX_ICON_FILE).orElse(null))) {
                    // Отменяем режим "Пауза" только для CPU
                    hold(false);
                    // Удаляем StepOver ловушку, если она вызвала отладчик
                    final int page    = spMX.getPage();
                    final int address = debugGetValRegPair(DebugRegPair.PC);
                    if (debugIsStepOverTrap(page, address)) {
                               debugRemTrap(page, address);
                    }
                    // Выводим окно отладчика
                    final DebuggerCPU debug = new DebuggerCPU(mainFrame, spMX);
                    // После окончания работы - убиваем отладчик
                    debug.getContentPane().removeAll();
                    debug.dispose();
                } finally {
                    // Отменяем блокировку главного окна
                    Platform.runLater(() -> spMX.setPrimaryStagePeerEnabled(true));
                    // Запускаем тактовый генератор и запоминающие устройства
                    spMX.pause(false, true);
                    // Разрешаем запускать отладчик
                    isDebugRun.getAndSet(false);
                }
            });
        }
    }

    /**
     * Получает значение регистровой пары CPU.
     * Старший байт результата - первый регистр пары, младший байт - второй
     * (для AF старший байт - A, младший - F; для SP и PC - адрес целиком).
     *
     * @param regPair регистровая пара
     * @return значение регистровой пары или -1, если пара не поддерживается
     */
    int debugGetValRegPair(DebugRegPair regPair);

    /**
     * Устанавливает значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @param value   значение регистровой пары
     */
    void debugSetValRegPair(DebugRegPair regPair, int value);

    /**
     * Добавляет ловушку.
     *
     * @param page     номер страницы памяти
     * @param address  адрес ловушки
     * @param stepOver true = StepOver ловушка
     */
    void debugAddTrap(int page, int address, boolean stepOver);

    /**
     * Удаляет ловушку.
     *
     * @param page    номер страницы памяти
     * @param address адрес ловушки
     */
    void debugRemTrap(int page, int address);

    /**
     * Проверяет, установлена ли по заданному адресу ловушка (не StepOver).
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка установлена
     */
    boolean debugIsTrap(int page, int address);

    /**
     * Проверяет, установлена ли по заданному адресу StepOver ловушка.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка StepOver установлена
     */
    boolean debugIsStepOverTrap(int page, int address);

    /**
     * Возвращает количество установленных ловушек (без StepOver).
     *
     * @return количество установленных ловушек
     */
    int debugGetTrapCount();

    /**
     * Возвращает ловушку по индексу в списке ловушек (без StepOver).
     *
     * @param index индекс ловушки
     * @return ловушка или null
     */
    @Nullable
    Trap debugGetTrap(int index);

    /**
     * Возвращает индекс ловушки в списке ловушек.
     *
     * @param trap ловушка
     * @return индекс или -1, если ловушка не найдена
     */
    int debugGetTrapIndex(Trap trap);
}
