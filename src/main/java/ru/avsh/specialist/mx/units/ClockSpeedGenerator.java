package ru.avsh.specialist.mx.units;

import ru.avsh.specialist.mx.units.types.ClockedUnit;
import ru.avsh.specialist.mx.units.types.CpuCore;
import ru.avsh.specialist.mx.units.types.ObserverUnit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Класс "Тактовый генератор".
 */
public final class ClockSpeedGenerator implements Runnable {
    // Основные константы тактового генератора
    public  static final String THREAD_NAME      =   "ClockSpeedGenerator"; // Имя потока
    public  static final int    CLOCK_SPEED      =              2_000_000 ; // Тактовая частота в Гц
    public  static final int    CYCLES           =                 66_000 ; // Количество тактов, которое необходимо выполнить
    public  static final long   CYCLES_FOR_NANOS = CYCLES * 1_000_000_000L;

    private final Object             fMutex;
    private final List<ClockedUnit > fClockedUnits ;
    private final List<ObserverUnit> fObserverUnits;

    private volatile CpuCore  fCPU       ;
    private volatile int      fIndexCPU  ;
    private volatile boolean  fWaitFlag  ;
    private volatile boolean  fPauseFlag ;
    private volatile int      fClockSpeed;
    private volatile long     fCyclesTime;
    private volatile long     fTimeSpent ;
    private volatile long     fCyclesCounter  ;
    private volatile boolean  fPrevCpuHoldMode;

    /**
     * Конструктор.
     */
    public ClockSpeedGenerator(int clockSpeed) {
        fMutex          = new Object    ();
        fClockedUnits   = new CopyOnWriteArrayList<>();
        fObserverUnits  = new CopyOnWriteArrayList<>();
        fIndexCPU       =   -1;
        fPauseFlag      = true;
        setClockSpeed(clockSpeed);
    }

    @Override
    public String toString() {
        return String.format("Частота процессора: %.1fМГц", getClockSpeed() / 1_000_000F);
    }

    @Override
    public void run() {
        long     startTime = System.nanoTime();
        long remainingTime;
        long       endTime;

        // Присваиваем имя потоку
        Thread.currentThread().setName(THREAD_NAME);

        try {
            // Основной цикл работы тактового генератора
            for (; ; ) {
                // Если установлена пауза -
                if (fPauseFlag) {
                    // завершаем команду CPU
                    execOneCmdCPU();
                    // переводим поток генератора в состояние ожидания
                    synchronized (fMutex) {
                        // Здесь выполняем присвоение fWaitFlag = fPauseFlag с одновременной проверкой
                        while (fWaitFlag = fPauseFlag) {
                            fMutex.wait();
                        }
                    }
                    // после завершения ожидания, замеряем начальное время
                    startTime = System.nanoTime();
                }

                // Рассчитываем конечное время выполнения заданного количества тактов
                // (подгонка из-за погрешности System.nanoTime())
                endTime = startTime + fCyclesTime - 100_000L;
                // Выполняем заданное количество тактов генератора без задержек
                synchronized (this) {
                    final long end = fCyclesCounter + CYCLES;
                    for (; !fPauseFlag && (fCyclesCounter < end); fCyclesCounter++) {
                        // Выполняем один такт у тактируемых устройств
                        for (final ClockedUnit fClockedUnit : fClockedUnits) {
                            fClockedUnit.cycle();
                        }
                    }
                }

                if (!fPauseFlag) {
                    // Если было прерывание потока - выходим из цикла
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    // Усыпляем поток на оставшееся время выполнения заданного количества тактов
                    final long currentTime = System.nanoTime();
                    if ((remainingTime = endTime - currentTime) >= 1_000_000L) {
                        fTimeSpent = currentTime -   startTime;
                        Thread.sleep(remainingTime / 1_000_000L);
                    }
                    // Делаем точную подгонку под оставшееся время выполнения заданного количества тактов
                    // noinspection StatementWithEmptyBody
                    while (endTime > (startTime = System.nanoTime())) {
                        //
                    }
                }
            }
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Добавляет тактируемое устройство.
     *
     * @param clockedUnit тактируемое устройство
     */
    public synchronized void addClockedUnit(final ClockedUnit clockedUnit) {
        if ((clockedUnit != null) && !fClockedUnits.contains(clockedUnit)) {
            if ((clockedUnit instanceof CpuCore) && (fCPU == null)) {
                // Первый зарегистрированный процессор считается активным до вызова setActiveCpu()
                fCPU      = (CpuCore) clockedUnit;
                fIndexCPU = fClockedUnits.size() ;
            }
            fClockedUnits.add(clockedUnit);
        }
    }

    /**
     * Добавляет устройство-наблюдатель.
     *
     * @param observerUnit устройство-наблюдатель
     */
    public synchronized void addObserverUnit(final ObserverUnit observerUnit) {
        if ((observerUnit != null) && !fObserverUnits.contains(observerUnit)) {
            fObserverUnits.add(observerUnit);
        }
    }

    /**
     * Возвращает время для выполнения CYCLES тактов.
     *
     * @return время в наносекундах.
     */
    public long getCyclesTime() {
        return fCyclesTime;
    }

    /**
     * Возвращает значение счетчика тактов.
     *
     * @return значение счетчика тактов
     */
    public long getCyclesCounter() {
        return fCyclesCounter;
    }

    /**
     * Задает процессор, который считается активным для режима "Пауза".
     * (Вспомогательный метод для смены процессора в эмуляторе)
     *
     * @param cpu процессор или null
     */
    public synchronized void setActiveCpu(final CpuCore cpu) {
        if (cpu != null) {
            fCPU = cpu;
            for (int index = 0; index < fClockedUnits.size(); index++) {
                if (fClockedUnits.get(index) == cpu) {
                    fIndexCPU = index;
                    break;
                }
            }
        }
    }

    /**
     * Возвращает значение тактовой частоты.
     *
     * @return тактовая частота в Гц
     */
    public int getClockSpeed() {
        return fClockSpeed;
    }

    /**
     * Возвращает приблизительное значение пиковой тактовой частоты
     * (зависит от ПК, на котором работает эмулятор и участка выполняемого кода).
     *
     * @return тактовая частота в Гц
     */
    public int getPeakClockSpeed() {
        return (fTimeSpent == 0) ? CLOCK_SPEED : (int) (CYCLES_FOR_NANOS / fTimeSpent);
    }

    /**
     * Задает тактовую частоту.
     *
     * @param clockSpeed тактовая частота в Гц
     */
    public synchronized void setClockSpeed(final int clockSpeed) {
        fClockSpeed = clockSpeed;
        fCyclesTime = Math.round(CYCLES_FOR_NANOS / (double) clockSpeed);
        fObserverUnits.forEach(ObserverUnit::update);
    }

    /**
     * Показывает приостановлен тактовый генератор или нет.
     *
     * @return - true = тактовый генератор приостановлен
     */
    public boolean isPaused() {
        return fPauseFlag;
    }

    /**
     * Переводит тактовый генератор в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     * @param mem  true = устанавливать/снимать режим "Пауза" и для запоминающих устройств
     */
    public void pause(final boolean mode, final boolean mem) {
        if (fPauseFlag != mode) {
            if (mode) {
                // Останавливаем CPU и устройства памяти
                if ((fCPU != null) && mem) {
                    // CPU
                    fPrevCpuHoldMode = fCPU.isHoldAcknowledge();
                    fCPU.hold            (true);
                    // Устройства
                    fCPU.pauseMemoryUnits(true);
                }

                // Останавливаем тактовый генератор
                fPauseFlag = true;
                for (int i = 1000; !fWaitFlag && (i > 0); i--) {
                    try {
                        Thread.sleep(1L); // спим 1мс
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                // Пробуждаем тактовый генератор
                fPauseFlag = false;
                synchronized (fMutex) {
                    fMutex.notifyAll();
                }

                // Пробуждаем CPU и устройства памяти
                if ((fCPU != null) && mem) {
                    // Устройства
                    fCPU.pauseMemoryUnits(false);
                    // CPU
                    if (!fPrevCpuHoldMode) {
                        fCPU.hold(false);
                    }
                }
            }
        }
    }

    /**
     * Выполняет одну команду CPU в режиме "Пауза" тактового генератора.
     * Пошагово исполняется активный процессор; неактивные процессоры тактируются,
     * но команд не исполняют.
     * (Метод для вызова из отладчика)
     *
     * @return true = команда выполнена успешно
     */
    public synchronized boolean execOneCmdCPU() {
        final CpuCore cpu = fCPU;
        if (fPauseFlag && (fIndexCPU >= 0) && !cpu.isHoldAcknowledge()) {
            boolean flag = true;
            do {
                // Адресуемся к массиву через локальную копию индекса: его может сменить setActiveCpu()
                final int activeIndex = fIndexCPU;
                for (int index = 0; index < fClockedUnits.size(); index++) {
                    if ( index == activeIndex) {
                         flag  = fClockedUnits.get(index).cycle();
                    } else {
                        fClockedUnits.get(index).cycle();
                    }
                }
            } while (!flag);
            return true;
        }
        return false;
    }
}