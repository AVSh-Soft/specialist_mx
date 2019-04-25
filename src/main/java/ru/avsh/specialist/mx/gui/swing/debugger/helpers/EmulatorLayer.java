package ru.avsh.specialist.mx.gui.swing.debugger.helpers;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.gui.swing.debugger.DebuggerCPUi8080;
import ru.avsh.specialist.mx.gui.swing.debugger.types.EventType;
import ru.avsh.specialist.mx.gui.swing.debugger.types.MemoryPageType;
import ru.avsh.specialist.mx.helpers.Constants;
import ru.avsh.specialist.mx.helpers.Trap;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.CPUi8080;

import java.util.Observable;

/**
 * Класс "Слой для взаимодействия отладчика с CPU и памятью эмулятора".
 */
public class EmulatorLayer extends Observable {
    private final SpecialistMX fSpMX;

    private boolean fDisableEvents;
    private int     fCodePage;
    private int     fDataPage;

    /**
     * Конструктор.
     */
    public EmulatorLayer(@NotNull SpecialistMX spMX) {
        // Запоминаем ссылку на главный класс эмулятора
        fSpMX = spMX;
        // Заполняем пустой массив предыдущих значений регистровых пар
        if ((getPrevValRegPair(CPUi8080.DebugRegPair.AF) & 0xFF) == 0) {
            saveAllRegPairs();
        }
        // Установим страницу памяти для просмотра кода = странице, в которой работает CPU
        fCodePage = fSpMX.getPage();
        // Восстановим номер страницы для просмотра данных
        fDataPage = PrevStaticData.getPrevDataPage();
    }

    /**
     * Показывает отключены события или нет.
     *
     * @return true = события отключены.
     */
    public boolean isEventsDisabled() {
        return fDisableEvents;
    }

    /**
     * Отключает события.
     */
    public void disableEvents() {
        fDisableEvents = true;
    }

    /**
     * Включает события.
     */
    public void enableEvents() {
        fDisableEvents = false;
    }

    /**
     * Отправляет событие наблюдателям.
     *
     * @param type   тип события
     * @param detail детали события
     */
    public void sendEvent(final EventType type, final Object detail) {
        if (fDisableEvents) {
            return;
        }
        // Отправляем оповещение наблюдателю
             setChanged();
        notifyObservers(new InnerEvent(type, detail));
    }

    /**
     * Выполняет проверку события.
     *
     * @param event       событие
     * @param checkType   проверочный тип события
     * @param checkDetail проверочные детали события (для null детали не важны)
     * @return true = событие необходимо обработать
     */
    public boolean eventCheck(@NotNull final InnerEvent event, final EventType checkType, final Object checkDetail) {
        if (event.getType() == checkType) {
            final Object detail = event.getDetail();
            return (detail == null) || (checkDetail == null) || detail.equals(checkDetail);
        }
        return false;
    }

    /**
     * Выполняет одну команду CPU в режиме "Пауза" тактового генератора.
     *
     * @return true = команда выполнена успешно
     */
    public boolean execOneCmdCPU() {
        final CPUi8080 cpu = fSpMX.getCPU();
        // Если CPU находится в режиме "HOLD" (в результате обращения в порт FFF0), то отменяем этот режим
        cpu.hold(false);
        // Отключаем режим "Пауза" для запоминающие устройств
        cpu.pauseMemoryUnits(false);
        // Выполнем одну команду CPU
        final boolean result = fSpMX.getGen().execOneCmdCPU();
        // Включаем режим "Пауза" для запоминающие устройств
        cpu.pauseMemoryUnits(true);
        return result;
    }

    /**
     * Получает значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @return значение
     */
    public int getValRegPair(final CPUi8080.DebugRegPair regPair) {
        return fSpMX.getCPU().debugGetValRegPair(regPair);
    }

    /**
     * Устанавливает значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @param value   значение
     */
    public void setValRegPair(final CPUi8080.DebugRegPair regPair, final int value) {
        final CPUi8080 cpu = fSpMX.getCPU();
        // Сохраняем предыдущее значение регистровой пары
        PrevStaticData.setPrevRegPairs(regPair, cpu.debugGetValRegPair(regPair));
        // Устанавливаем новое значение
        cpu.debugSetValRegPair(regPair, value);
        // Отправляем событие наблюдателям
        sendEvent(EventType.REG_PAIR, regPair);
    }

    /**
     * Получает предыдущее значение регистровой пары CPU.
     *
     * @param regPair регистровая пара
     * @return значение
     */
    public int getPrevValRegPair(final CPUi8080.DebugRegPair regPair) {
        return PrevStaticData.getPrevRegPairs(regPair);
    }

    /**
     * Сохраняет значения всех регистровых пар в массиве.
     */
    public void saveAllRegPairs() {
        for (CPUi8080.DebugRegPair regPair : CPUi8080.DebugRegPair.values()) {
            PrevStaticData.setPrevRegPairs(regPair, getValRegPair(regPair));
        }
    }

    /**
     * Получает визуальное представление регистра флагов.
     *
     * @return значение регистра флагов.
     */
    public String getVisViewsFlagsReg() {
        final char[] r = new char[DebuggerCPUi8080.FLAGS.length()];

        char c;
        int  f = getValRegPair(CPUi8080.DebugRegPair.AF);
        for (int i = DebuggerCPUi8080.FLAGS.length() - 1; i >= 0; i--, f >>= 1) {
            c = DebuggerCPUi8080.FLAGS.charAt(i);
            if ((f & 1) == 0) {
                r[i] = (c == '?') ? '0' : '.';
            } else {
                r[i] = (c == '?') ? '1' :  c ;
            }
        }
        return new String(r);
    }

    /**
     * Показывает как изменилась регистровая пара (сравнивает предыдущее и текущее значение регистровой пары).
     *
     * @param regPair регистровая пара
     * @return -1 = изменений нет, 0 = изменился младший байт, 1 = изменился старший байт, 2 = изменились оба байта
     * (Для SP и PC всегда 2 = оба байта, если были изменения)
     */
    public int getChangesRegPair(final CPUi8080.DebugRegPair regPair) {
        final int xor = getPrevValRegPair(regPair) ^ getValRegPair(regPair);
        if (xor > 0) {
            if        (( xor < 0x100)       && (regPair != CPUi8080.DebugRegPair.SP) && (regPair != CPUi8080.DebugRegPair.PC)) {
                return 0;
            } else if (((xor % 0x100) == 0) && (regPair != CPUi8080.DebugRegPair.SP) && (regPair != CPUi8080.DebugRegPair.PC)) {
                return 1;
            } else {
                return 2;
            }
        }
        return -1;
    }

    /**
     * Добавляет ловушку.
     *
     * @param page     номер страницы памяти
     * @param address  адрес ловушки
     * @param stepOver true = StepOver ловушка
     */
    public void addTrap(final int page, final int address, final boolean stepOver) {
        fSpMX.getCPU().debugAddTrap(page, address, stepOver);
        // Отправляем событие наблюдателям
        if (!stepOver) {
            sendEvent(EventType.TRAPS, new Trap(page, address));
        }
    }

    /**
     * Удаляет ловушку.
     *
     * @param page    номер страницы памяти
     * @param address адрес ловушки
     */
    public void remTrap(final int page, final int address) {
        fSpMX.getCPU().debugRemTrap(page, address);
        // Отправляем событие наблюдателям
        sendEvent(EventType.TRAPS, new Trap(page, address));
    }

    /**
     * Удаляет все ловушки.
     */
    public void clearTraps() {
        fSpMX.getCPU().debugClearTraps();
        // Отправляем событие наблюдателям
        sendEvent(EventType.TRAPS, null);
    }

    /**
     * Проверяет, установлена ли по заданному адресу ловушка.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     * @return true = ловушка установлена
     */
    public boolean isTrap(final int page, final int address) {
        return fSpMX.getCPU().debugIsTrap(page, address);
    }

    /**
     * Возвращает количество установленных ловушек.
     *
     * @return количество установленных ловушек
     */
    public int getTrapCount() {
        return fSpMX.getCPU().debugGetTrapCount();
    }

    /**
     * Возвращает ловушку по индексу в списке ловушек.
     *
     * @param index индекс ловушки
     * @return ловушка
     */
    public Trap getTrap(final int index) {
        return fSpMX.getCPU().debugGetTrap(index);
    }

    /**
     * Возвращает индекс ловушки.
     *
     * @param trap ловушка
     * @return индекс
     */
    public int getTrapIndex(final Trap trap) {
        return fSpMX.getCPU().debugGetTrapIndex(trap);
    }

    /**
     * Возвращает номер страницы памяти, в которой просматривается код.
     *
     * @return номер страницы
     */
    public synchronized int getCodePage() {
        return fCodePage;
    }

    /**
     * Возвращает номер страницы памяти, в которой просматриваются данные.
     *
     * @return номер страницы
     */
    public synchronized int getDataPage() {
        return fDataPage;
    }

    /**
     * Возвращает номер страницы памяти, в которой работает CPU.
     *
     * @return номер страницы
     */
    public int getCpuPage() {
        return fSpMX.getPage();
    }

    /**
     * Устанавливает номер страницы памяти для просмотра кода.
     *
     * @param page номер страницы
     */
    public synchronized void setCodePage(final int page) {
        if (fCodePage != page) {
            fCodePage  = page;
            sendEvent(EventType.PAGE, MemoryPageType.CODE);
        }
    }

    /**
     * Устанавливает номер страницы памяти для просмотра данных.
     *
     * @param page номер страницы
     */
    public synchronized void setDataPage(final int page) {
        if (fDataPage != page) {
            fDataPage  = page;
            sendEvent(EventType.PAGE, MemoryPageType.DATA);
        }
    }

    /**
     * Устанавливает номер страницы памяти для CPU.
     *
     * @param page номер страницы
     */
    public void setCpuPage(final int  page) {
        if (fSpMX.getPage() != page) {
            fSpMX.setPage(page);
            sendEvent(EventType.PAGE, MemoryPageType.CPU);
        }
    }

    /**
     * Возвращает количество страниц памяти (учитываются все страницы памяти - основная, RAM и ROM диски).
     *
     * @return количество страниц памяти
     */
    public int getNumPages() {
        return Constants.NUMBER_PAGES_RAMDISK + 2; // Основная память и ROM-диск не учтены в параметре NUMBER_PAGES_RAMDISK
    }

    /**
     * Возвращает имя страницы памяти.
     *
     * @param page номер страницы
     * @return имя страницы
     */
    public String getPageName(final int page) {
        if (page == 0) {
            return "RAM";
        } else if ((page > 0) && (page < getNumPages() - 1)) {
            return String.format("RAM disk %d", page);
        } else {
            return "ROM disk";
        }
    }

    /**
     * Читает байт из памяти (прямое чтение).
     *
     * @param page    страница памяти
     * @param address адрес
     * @return считанный байт
     */
    public synchronized int readByte(final int page, final int address) {
        final int curPage  = fSpMX.getPage();
        if (      curPage != page) {
            fSpMX.setPage(   page);
        }
        final int result   = fSpMX.readByte(address);
        if (page  !=      curPage) {
            fSpMX.setPage(curPage);
        }
        return result;
    }

    /**
     * Читает байт из памяти (безопасное чтение).
     *
     * @param page    страница памяти
     * @param address адрес
     * @return считанный байт
     */
    public synchronized int debugReadByte(final int page, final int address) {
        final int curPage  = fSpMX.getPage();
        if (      curPage != page) {
            fSpMX.setPage(   page);
        }
        final int result   = fSpMX.debugReadByte(address);
        if (page  !=      curPage) {
            fSpMX.setPage(curPage);
        }
        return result;
    }

    /**
     * Записывает байт в память.
     *
     * @param page    страница памяти
     * @param address адрес
     * @param value   байт
     */
    public synchronized void writeByte(final int page, final int address, final int value) {
        final int curPage  = fSpMX.getPage();
        if (      curPage != page) {
            fSpMX.setPage(   page);
        }
        fSpMX.writeByte(address, value);
        if (page !=       curPage) {
            fSpMX.setPage(curPage);
        }
        sendEvent(EventType.MEMORY, page);
    }

    /**
     * Метод должен вызываться после выполнения каждого шага CPU.
     */
    public synchronized void afterStep() {
        // Устанавливаем Code Page = CPU Page
        fCodePage = fSpMX.getPage();
        // Отправляем событие наблюдателям
        sendEvent(EventType.STEP, null);
    }
}
