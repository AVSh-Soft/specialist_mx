package ru.avsh.specialist.mx.gui.swing.debugger;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsm;
import ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmI8080;
import ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmResult;
import ru.avsh.specialist.mx.gui.swing.debugger.disasm.DisAsmZ80;
import ru.avsh.specialist.mx.gui.swing.debugger.helpers.EmulatorLayer;
import ru.avsh.specialist.mx.gui.swing.debugger.helpers.InnerEvent;
import ru.avsh.specialist.mx.gui.swing.debugger.helpers.PrevStaticData;
import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.gui.swing.debugger.types.EventType;
import ru.avsh.specialist.mx.gui.swing.debugger.types.MemoryPageType;
import ru.avsh.specialist.mx.gui.swing.utils.JFormattedTextFieldExt;
import ru.avsh.specialist.mx.helpers.Constants;
import ru.avsh.specialist.mx.helpers.Trap;
import ru.avsh.specialist.mx.root.SpecialistMX;
import ru.avsh.specialist.mx.units.memory.units.MainMemory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.table.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

import static javax.swing.JOptionPane.*;
import static javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt;

/**
 * Класс "Отладчик для активного процессора эмулятора (I8080 или Z80)".
 *
 * @author -=AVSh=-
 */
public final class DebuggerCPU extends JDialog {
    private static final long serialVersionUID = -4782408965788448666L;

    // Значение пустого элемента буфера   (обязательно отрицательное значение)
    public static final int EMPTY_VALUE = -1;
    // Значение неверного элемента буфера (обязательно отрицательное значение)
    public static final int WRONG_VALUE = -2;

    private static final String TITLE_I8080 = "Отладчик для процессора Intel 8080A (КР580ВМ80А)";
    private static final String TITLE_Z80   = "Отладчик для процессора Zilog Z80";

    private static final String INI_OPTION_FRAME_WIDTH  = "DebugFrameWidth" ;
    private static final String INI_OPTION_FRAME_HEIGHT = "DebugFrameHeight";

    // Кодовая таблица знаков -=КОИ8=-
    private static final String KOI8 =
          // 0123456789ABCDEF0123456789ABCDEF01 23456789ABCDEF0123456789ABCDEF
            "·········☺·↑··→↓·············←↖· !\"#$%&'()*+,-./0123456789:;<=>?" +
          // 0123456789ABCDEF0123456789AB CDEF0123456789ABCDEF0123456789ABCDEF
            "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~·" +
          // 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
            "································································"  +
            "юабцдефгхийклмнопярстужвьызшэщчъЮАБЦДЕФГХИЙКЛМНОПЯРСТУЖВЬЫЗШЭЩЧЪ"  ;

    // Символ, который игнорируется в таблице знаков -=КОИ8=-
    private static final char SKIP_CHAR = '·';

    // Имена флагов процессоров от старшего бита регистра F к младшему биту,
    // должно быть ровно 8 символов, т.к. каждый отвечает за отдельный флаг
    private static final String FLAGS_I8080 = "SZ?A?P?C"; // SZ0A0P1C - тут '?' неизменяемые биты в регистре F у I8080
    private static final String FLAGS_Z80   = "SZ5H3PNC"; // SZ5H3PNC

    // Дизассемблеры процессоров
    private static final DisAsm DISASM_I8080 = new DisAsmI8080();
    private static final DisAsm DISASM_Z80   = new DisAsmZ80  ();

    // Максимальная длина команды среди поддерживаемых процессоров
    private static final int DA_MAX_CMD_LENGTH = Math.max(DISASM_I8080.getMaxCmdLength(), DISASM_Z80.getMaxCmdLength());
    // Отступ колонки "Команда" дизассемблера (ширина колонки рассчитывается по тексту команд)
    private static final int DA_CMD_PADDING = 6;
    // Ширина колонки "Команда" дизассемблера по самому длинному тексту команд поддерживаемых процессоров
    private static final int DA_CMD_WIDTH;
    static {
        final FontMetrics fontMetrics = new Canvas().getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        DA_CMD_WIDTH = DA_CMD_PADDING + Math.max(
                fontMetrics.stringWidth(DISASM_I8080.getMaxCmdText()),
                fontMetrics.stringWidth(DISASM_Z80.getMaxCmdText()));
    }
    // Ширина и высота кнопки флага
    private static final int FLAG_BUTTON_WIDTH  = 17;
    private static final int FLAG_BUTTON_HEIGHT = 17;

    // Константы дизассемблера
    private static final int FOR_ALIGNMENT = 16; // Длина участка для выравнивания кода
    private static final int BUF_SIZE      = 64; // Размер буфера
    private static final int THREE_QUARTER = BUF_SIZE - (BUF_SIZE >> 2);

    // Количество строк и колонок таблицы с дизассемблированными данными (просмотр кода)
    private static final int DA_NUM_ROWS = 0x1_0000;
    // Колонки таблицы с дизассемблированными данными (просмотр кода):
    // ловушка, адрес, байты команды (по числу максимума среди процессоров), мнемоника
    private static final int DA_COL_TRP     = 0;
    private static final int DA_COL_ADR     = 1;
    private static final int DA_COL_BT0     = 2;
    private static final int DA_COL_BT3     = 5;
    private static final int DA_COL_CMD     = 6;
    private static final int DA_NUM_COLUMNS = 7;

    // Количество колонок таблицы с регистровыми парами CPU (количество строк зависит от активного процессора)
    private static final int CR_NUM_COLUMNS = 3;
    // Колонки таблицы с регистровыми парами CPU
    private static final int CR_COL_REG = 0;
    private static final int CR_COL_EQU = 1;
    private static final int CR_COL_DAT = 2;

    // Количество строк и колонок таблицы с данными стека
    private static final int SP_NUM_ROWS    = 12;
    private static final int SP_NUM_COLUMNS =  1;
    // Колонки таблицы с данными стека
    private static final int SP_COL_DAT = 0;

    // Количество колонок таблицы с данными ловушек
    private static final int TP_NUM_COLUMNS = 2;
    // Колонки таблицы с данными ловушек
    private static final int TP_COL_PAG = 0;
    private static final int TP_COL_ADR = 1;

    // Количество строк и колонок таблицы с данными памяти (просмотр данных)
    private static final int MD_NUM_ROWS    = DA_NUM_ROWS >> 4;
    private static final int MD_NUM_COLUMNS = 18;
    // Колонки таблицы с данными памяти (просмотр данных)
    private static final int MD_COL_ADR =  0;
    private static final int MD_COL_B00 =  1;
    private static final int MD_COL_B01 =  2;
    private static final int MD_COL_B02 =  3;
    private static final int MD_COL_B03 =  4;
    private static final int MD_COL_B04 =  5;
    private static final int MD_COL_B05 =  6;
    private static final int MD_COL_B06 =  7;
    private static final int MD_COL_B07 =  8;
    private static final int MD_COL_B08 =  9;
    private static final int MD_COL_B09 = 10;
    private static final int MD_COL_B10 = 11;
    private static final int MD_COL_B11 = 12;
    private static final int MD_COL_B12 = 13;
    private static final int MD_COL_B13 = 14;
    private static final int MD_COL_B14 = 15;
    private static final int MD_COL_B15 = 16;
    private static final int MD_COL_STR = 17;

    // Прочие константы
    private static final Border FOCUS_BORDER          = new LineBorder (Color.black);
    private static final Border NO_FOCUS_BORDER       = new EmptyBorder(1, 1, 1, 1);
    private static final Color  YELLOW_GREEN          = new Color(128, 255, 0);
    private static final Color  YELLOW_RED            = new Color(255, 128, 0);
    private static final Font   HEADER_FONT           = new Font(Font.MONOSPACED, Font.BOLD , 11);
    private static final Font   DEFAULT_FONT          = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    private static final String BYTE_MASK             = "HH"   ;
    private static final String WORD_MASK             = "HHHH" ;
    private static final String STR_ADDRESS           = "Адрес";
    private static final String STR16_MASK            = "****************"   ;
    private static final String EDITING_OR_NAVIGATING = "EditingOrNavigating";

    private final transient SpecialistMX  fSpMX;
    private final transient EmulatorLayer fEmulatorLayer;

    private final DisAsmTable fDisAsmTable;
    private final MemDatTable fMemDatTable;

    // Строка имён флагов активного процессора и признак отображения пар IX/IY
    private final String  fFlags;
    private final boolean fIsZ80;
    private final boolean fShowIndexRegisters;

    private int fFocusedAddress;

    /**
     * Возвращает заголовок окна для активного процессора.
     *
     * @param isZ80 true = активен процессор Z80
     * @return заголовок окна
     */
    public static String getTitle(final boolean isZ80) {
        return isZ80 ? TITLE_Z80 : TITLE_I8080;
    }

    /**
     * Возвращает заголовок окна для активного процессора эмулятора.
     *
     * @param spMX ссылка на главный класс эмулятора
     * @return заголовок окна
     */
    public static String getTitle(final @NotNull SpecialistMX spMX) {
        return getTitle(spMX.isZ80());
    }

    /**
     * Конструктор.
     *
     * @param spMX ссылка на главный класс эмулятора.
     */
    public DebuggerCPU(Frame owner, @NotNull SpecialistMX spMX) {
        super(owner, true);

        // Запоминаем ссылку на главный класс эмулятора
        fSpMX  = spMX;
        // Процессор, который сейчас отлаживается
        fIsZ80 = spMX.isZ80();
        fFlags = fIsZ80 ? FLAGS_Z80 : FLAGS_I8080;
        fShowIndexRegisters = fIsZ80;
        // Инициализируем слой для взаимодействия с CPU и памятью
        fEmulatorLayer  = new EmulatorLayer(spMX);
        // Инициализируем модели и таблицы для просмотра кода и данных
        fDisAsmTable    = new DisAsmTable  (new DisAsmTableModel());
        fMemDatTable    = new MemDatTable  (new MemDatTableModel());
        fFocusedAddress = -1;
        // Инициализируем остальные компоненты и обработчики событий
        initComponents();
    }

    private void initComponents() {
        setTitle(fIsZ80 ? TITLE_Z80 : TITLE_I8080);

        final JPanel dialogPane   = new JPanel();
        final JPanel contentPanel = new JPanel();
        final JPanel buttonBar    = new JPanel();

        final JScrollPane disAsmScrollPane = new JScrollPane();
        final JScrollPane regCpuScrollPane = new JScrollPane();
        final JScrollPane memDatScrollPane = new JScrollPane();
        final JScrollPane  stackScrollPane = new JScrollPane();
        final JScrollPane  trapsScrollPane = new JScrollPane();

        // Байтовые поля для редактора ячеек таблицы fDisAsmTable
        final JFormattedTextFieldExt byteFieldDAT = new JFormattedTextFieldExt(BYTE_MASK, '0');
        final DefaultCellEditor byteCellEditorDAT = new      DefaultCellEditor(byteFieldDAT);

        // Двухбайтовые поля для редактора ячеек таблицы regCpuTable
        final JFormattedTextFieldExt wordFieldRCT = new JFormattedTextFieldExt(WORD_MASK, '0');
        final DefaultCellEditor wordCellEditorRCT = new      DefaultCellEditor(wordFieldRCT);

        // Двухбайтовые поля для редактора ячеек таблицы stackTable
        final JFormattedTextFieldExt wordFieldST  = new JFormattedTextFieldExt(WORD_MASK, '0');
        final DefaultCellEditor wordCellEditorST  = new      DefaultCellEditor(wordFieldST);

        // Байтовые поля для редактора ячеек таблицы fMemDatTable
        final JFormattedTextFieldExt byteFieldMDT = new JFormattedTextFieldExt(BYTE_MASK, '0');
        final DefaultCellEditor byteCellEditorMDT = new      DefaultCellEditor(byteFieldMDT);

        // Строковое поле из 16 символов для редактора ячеек таблицы fMemDatTable
        final JFormattedTextFieldExt strFieldMDT  = new JFormattedTextFieldExt(STR16_MASK, ' ');
        final DefaultCellEditor strCellEditorMDT  = new      DefaultCellEditor(strFieldMDT);

        // Параметры таблицы fDisAsmTable с дизассемблированными данными
        for (int i = DA_COL_BT0; i <= DA_COL_BT3; i++) {
            fDisAsmTable.getColumnModel().getColumn(i).setCellEditor(byteCellEditorDAT);
        }

        // Параметры таблицы fMemDatTable с данными памяти
        for (int i = MD_COL_B00; i <= MD_COL_B15; i++) {
            fMemDatTable.getColumnModel().getColumn(i).setCellEditor(byteCellEditorMDT);
        }
        fMemDatTable.getColumnModel().getColumn(MD_COL_STR).setCellEditor(strCellEditorMDT);

        // Метка для вывода информации из регистра флагов CPU
        final FlagsRegLabel flagsRegLabel = new FlagsRegLabel();

        // Метка для вывода информации о текущей странице CPU
        final CpuMemPageLabel cpuMemPageLabel = new CpuMemPageLabel();

        // Кнопки установки/сброса флагов CPU (по кнопке на каждый значащий бит регистра F):
        // у I8080 - S Z A P C, у Z80 - S Z 5 H 3 P N C. Обработчик находит бит по названию кнопки
        // в строке fFlags, поэтому набор кнопок задаётся только этой строкой
        final JButton[] flagButtons = new JButton[fFlags.length()];
        {
            final Dimension dim = new Dimension(FLAG_BUTTON_WIDTH, FLAG_BUTTON_HEIGHT);
            final Insets    ins = new Insets   (1, 0, 1, 0);
            for (int i = 0; i < flagButtons.length; i++) {
                final char flagName = fFlags.charAt(i);
                final JButton button = new JButton(String.valueOf(flagName));
                button.setMinimumSize  (dim);
                button.setPreferredSize(dim);
                button.setMargin       (ins);
                // Для I8080 блокируем управление для битов с именами '?'
                if (flagName == '?') {
                    button.setEnabled(false);
                } else {
                    button.setMnemonic   (flagName);
                    button.setToolTipText("Флаг " + flagName + " (бит " + (7 - i) + " регистра F)");
                }
                flagButtons[i] = button;
            }
        }

        // Модель и таблица для вывода информации из регистровых пар CPU
        final RegCpuTable regCpuTable = new RegCpuTable(new RegCpuTableModel());
        regCpuTable.getColumnModel().getColumn(CR_COL_DAT).setCellEditor(wordCellEditorRCT);

        // Модель и таблица для вывода информации из стека CPU
        final StackTable stackTable = new StackTable(new StackTableModel());
        stackTable.getColumnModel().getColumn(SP_COL_DAT).setCellEditor(wordCellEditorST);

        // Модель и таблица для вывода информации о ловушках
        final TrapsTable trapsTable = new TrapsTable(new TrapsTableModel());

        // Кнопки для работы с ловушками
        final JButton deleteTrapButton = new JButton("Delete");
        final JButton clearTrapsButton = new JButton("Clear" );
        {
            final Dimension dim = new Dimension(78, 14);
            final Insets    ins = new Insets   ( 1, 1, 1, 1);
            deleteTrapButton.setMinimumSize (dim);
            deleteTrapButton.setMargin      (ins);
            deleteTrapButton.setFont(HEADER_FONT);
            deleteTrapButton.setToolTipText ("Удаляет выбранную ловушку");
            clearTrapsButton.setMinimumSize (dim);
            clearTrapsButton.setMargin      (ins);
            clearTrapsButton.setFont(HEADER_FONT);
            clearTrapsButton.setToolTipText ("Удаляет все ловушки");
        }

        // Переключатель страниц памяти для просмотра кода
        final CodeMemPagesComboBox codeMemPagesComboBox = new CodeMemPagesComboBox();

        // Переключатель страниц памяти для просмотра данных
        final DataMemPagesComboBox dataMemPagesComboBox = new DataMemPagesComboBox();

        final JButton   findButton = new JButton("Find"  );
        final JButton selectButton = new JButton("Select");
        final JButton   saveButton = new JButton("Save"  );
        final JButton   loadButton = new JButton("Load"  );

        // Основные кнопки управления отладчиком
        final Action performStep = new AbstractAction("F6 Step") {
            private static final long serialVersionUID = 6179694362946312263L;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                step();
            }
        };

        final Action performStepOver = new AbstractAction("F7 Step Over") {
            private static final long serialVersionUID = -8028618186701256410L;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                stepOver();
            }
        };

        final JButton     gotoButton = new JButton("Go to");
        final JButton     toPCButton = new JButton("To PC");
        final JButton      runButton = new JButton("Run to cursor");
        final JButton     stepButton = new JButton(performStep    );
        final JButton stepOverButton = new JButton(performStepOver);
        final JButton       okButton = new JButton("OK");

            stepButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "performStep"    );
            stepButton.getActionMap().put("performStep"    , performStep    );
        stepOverButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "performStepOver");
        stepOverButton.getActionMap().put("performStepOver", performStepOver);

            findButton.setMnemonic('F');
            findButton.setToolTipText("Осуществляет поиск данных");
          selectButton.setMnemonic('e');
          selectButton.setToolTipText("Осуществляет выделение диапазона адресов в таблице просмотра данных");
            saveButton.setMnemonic('v');
            saveButton.setToolTipText("Сохраняет выделенный диапазон адресов в таблице просмотра данных на диск");
            loadButton.setMnemonic('L');
            loadButton.setToolTipText("Загружает файл в страницу таблицы просмотра данных с адреса под курсором");
            gotoButton.setMnemonic('G');
            gotoButton.setToolTipText("Выполняет переход к заданному адресу в таблицах просмотра кода/данных");
            toPCButton.setMnemonic('T');
            toPCButton.setToolTipText("Выполняет переход к странице CPU и адресу = PC в таблице просмотра кода");
             runButton.setMnemonic('R');
             runButton.setToolTipText("Запускает CPU с места, указанного желтым курсором в таблице просмотра кода");
            stepButton.setToolTipText("Выполняет один шаг CPU");
        stepOverButton.setToolTipText("Выполняет один шаг CPU с обходом вызываемых подпрограмм");

        // --- Настраиваем расположение компонентов ---
        // -=- contentPane -=-
        final Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        // -=- dialogPane -=-
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            // -=- contentPanel -=-
            {
                contentPanel.setPreferredSize(new Dimension(DA_CMD_WIDTH + 529, 565));
                contentPanel.setLayout(new GridBagLayout());
                ((GridBagLayout) contentPanel.getLayout()).columnWidths  =
                        new    int[] {DA_CMD_WIDTH + 177,
                                      FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH,
                                      FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH, FLAG_BUTTON_WIDTH,
                                      79,  79, 10};
                ((GridBagLayout) contentPanel.getLayout()).rowHeights    =
                        new    int[] {22, 22, 203, 20, 22, 22, 22, 22, 100, 0};
                ((GridBagLayout) contentPanel.getLayout()).columnWeights =
                        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};
                ((GridBagLayout) contentPanel.getLayout()).rowWeights    =
                        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0E-4};

                // -=- disAsmScrollPane -=-
                disAsmScrollPane.setViewportView(fDisAsmTable);
                contentPanel.add(disAsmScrollPane,
                                 new GridBagConstraints(0, 0, 1, 8, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 5), 0, 0));

                // -=- flagsRegLabel -=-
                // Метка регистра флагов стоит в одной полосе с кнопками флагов: с полосы шириной
                // в 8 столбцов по 16 px (у I8080 используются 5 из них)
                contentPanel.add(flagsRegLabel,
                                 new GridBagConstraints(1, 0, 8, 1, 0.0, 0.0,
                                                        GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, 1, 0), 0, 0));

                // -=- flagButtons -=-
                // Кнопки управления флагами в один ряд под flagsRegLabel
                for (int i = 0; i < flagButtons.length; i++) {
                    contentPanel.add(flagButtons[i],
                                     new GridBagConstraints(i + 1, 1, 1, 1, 0.0, 0.0,
                                                            GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                            new Insets(0, 1, 0, 1), 0, 0));
                }

                // -=- regCpuPanel -=-
                // Под кнопками управления флагами
                regCpuScrollPane.setViewportView(regCpuTable);
                contentPanel.add(regCpuScrollPane,
                        new GridBagConstraints(1, 2, 5, 2, 0.0, 0.0,
                                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                new Insets(5, 5, 3, 0), 0, 0));

                // -=- stackScrollPane -=-
                // Под кнопками управления флагами, правее regCpuPanel
                stackScrollPane.setViewportView(stackTable);
                contentPanel.add(stackScrollPane,
                        new GridBagConstraints(6, 2, 3, 2, 0.0, 0.0,
                                GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                new Insets(5, 7, 3, 7), 0, 0));

                // -=- trapsScrollPane -=-
                // Правее flagsRegLabel
                trapsScrollPane.setViewportView(trapsTable);
                contentPanel.add(trapsScrollPane,
                                 new GridBagConstraints(9, 0, 2, 3, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 5, 0, 0), 0, 0));

                // -=- deleteTrapButton -=-
                // Под trapsScrollPane
                contentPanel.add(deleteTrapButton,
                                 new GridBagConstraints(9, 3, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                        new Insets(0, 5, 0, 1), 0, 0));

                // -=- clearTrapsButton -=-
                // Под trapsScrollPane, правее deleteTrapButton
                contentPanel.add(clearTrapsButton,
                                 new GridBagConstraints(10, 3, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                        new Insets(0, 1, 0, 0), 0, 0));


                // -=- cpuMemPageLabel -=-
                // Под regCpuPanel, stackScrollPane, deleteTrapButton, clearTrapsButton
                contentPanel.add(cpuMemPageLabel,
                                 new GridBagConstraints(1, 4, 10, 1, 0.0, 0.0,
                                                        GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, 5, 0), 0, 0));

                // -=- codeMemPagesComboBox -=-
                // Под cpuMemPageLabel
                contentPanel.add(codeMemPagesComboBox,
                                 new GridBagConstraints(1, 6, 9,  1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, DA_CMD_WIDTH - 131), 0, 0));


                // -=- dataMemPagesComboBox -=-
                // Под codeMemPagesComboBox
                contentPanel.add(dataMemPagesComboBox,
                                 new GridBagConstraints(1, 7, 9, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, DA_CMD_WIDTH - 131), 0, 0));

                // -=- memDatScrollPane -=-
                // Под codeMemPagesComboBox
                memDatScrollPane.setViewportView(fMemDatTable);
                contentPanel.add(memDatScrollPane,
                                 new GridBagConstraints(0, 8, 10, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(1, 0, 4, DA_CMD_WIDTH - 131), 0, 0));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            // -=- buttonBar -=-
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridBagLayout());
                ((GridBagLayout) buttonBar.getLayout()).columnWidths  = new    int[] {  0,   0,   0,   0,   0,   0,  80};
                ((GridBagLayout) buttonBar.getLayout()).columnWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};

                //---- findButton ----
                buttonBar.add(findButton, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- selectButton ----
                buttonBar.add(selectButton, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- saveButton ----
                buttonBar.add(saveButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- loadButton ----
                buttonBar.add(loadButton, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- gotoButton ----
                buttonBar.add(gotoButton, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));

                //---- toPCButton ----
                buttonBar.add(toPCButton, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));

                //---- runButton ----
                buttonBar.add(runButton, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));

                //---- stepButton ----
                buttonBar.add(stepButton, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));

                //---- stepOverButton ----
                buttonBar.add(stepOverButton, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));

                //---- okButton ----
                buttonBar.add(okButton, new GridBagConstraints(6, 1, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);

        // -=-=-=-=- Обработчики событий -=-=-=-=-
        {
            // Срабатывает в начале и конце редактирования - корректируем позицию курсора,
            // устанавливаем значение и фокус компонента JFormattedTextField
            final HierarchyListener hierarchyListener = hierarchyEvent -> {
                if ((hierarchyEvent.getChangedParent() instanceof JTable) &&
                        (hierarchyEvent.getComponent() instanceof JFormattedTextField)) {
                    final JTable              tbl = (JTable)              hierarchyEvent.getChangedParent();
                    final JFormattedTextField ftf = (JFormattedTextField) hierarchyEvent.getComponent    ();

                    if (!tbl.isEditing() && !ftf.isFocusOwner() && (ftf.getCaretPosition() > 0)) {
                        ftf.setValue(tbl.getModel().getValueAt(getFocusedRowModel(tbl), getFocusedColumnModel(tbl)));
                        ftf.requestFocusInWindow ();
                        ftf.setCaretPosition    (0);
                    }
                }
            };
            // Добавляем в компоненты
            byteFieldDAT.addHierarchyListener(hierarchyListener);
            wordFieldRCT.addHierarchyListener(hierarchyListener);
             wordFieldST.addHierarchyListener(hierarchyListener);
            byteFieldMDT.addHierarchyListener(hierarchyListener);
             strFieldMDT.addHierarchyListener(hierarchyListener);
        }

        {
            // При потере фокуса на редактируемом байте/слове - отменяем редактирование
            final FocusAdapter focusAdapter = new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent focusEvent) {
                    if (((Component) focusEvent.getSource()).getParent() instanceof JTable) {
                        final JTable tbl = (JTable) ((Component) focusEvent.getSource()).getParent();
                        if (tbl.isEditing()) {
                            tbl.getCellEditor().cancelCellEditing();
                        }
                    }
                }
            };
            // Добавляем в компоненты
            byteFieldDAT.addFocusListener(focusAdapter);
            wordFieldRCT.addFocusListener(focusAdapter);
             wordFieldST.addFocusListener(focusAdapter);
            byteFieldMDT.addFocusListener(focusAdapter);
             strFieldMDT.addFocusListener(focusAdapter);
        }

        // При потере фокуса на таблице fDisAsmTable - убираем пометку адресов
        fDisAsmTable.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                fFocusedAddress  =  -1;
                fDisAsmTable.repaint();
            }
        });

        // Изменяем реакцию на клавишу Enter в таблице fDisAsmTable
        fDisAsmTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
        fDisAsmTable.getActionMap()
                .put(EDITING_OR_NAVIGATING, new AbstractAction() {
            private static final long serialVersionUID = -6938596434272573049L;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final TableModel model = fDisAsmTable.getModel();

                final int rowM =    getFocusedRowModel(fDisAsmTable);
                final int colM = getFocusedColumnModel(fDisAsmTable);
                if ((colM >= DA_COL_BT0) && (colM <= DA_COL_BT3)) {
                    // Редактирование ячейки с байтом команды
                    if (model.isCellEditable(rowM, colM)) {
                        fDisAsmTable.editCellAt(
                                fDisAsmTable.convertRowIndexToView   (rowM),
                                fDisAsmTable.convertColumnIndexToView(colM));
                    }
                } else if ((colM == DA_COL_CMD)
                        && (fFocusedAddress >= 0)
                        && ((DisAsmTableModel) model).isJmpAndCallCmd(rowM)) {
                    // Переход по адресу в команде
                    fDisAsmTable.gotoAddress(fFocusedAddress, DA_COL_ADR);
                }
            }
        });

        {
            final AbstractAction abstractAction = new AbstractAction() {
                private static final long serialVersionUID = 2593281858748981540L;

                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    final JTable table = (JTable) actionEvent.getSource();

                    final int rowM =    getFocusedRowModel(table);
                    final int colM = getFocusedColumnModel(table);
                    // Редактирование ячейки
                    if ((   ((table instanceof RegCpuTable) && (colM == CR_COL_DAT))
                         || ((table instanceof StackTable ) && (colM == SP_COL_DAT))
                         || ((table instanceof MemDatTable) && (colM >= MD_COL_B00))) && table.getModel().isCellEditable(rowM, colM)) {
                        table.editCellAt(table.convertRowIndexToView(rowM), table.convertColumnIndexToView(colM));
                    }
                }
            };

            // Изменяем реакцию на клавишу Enter в таблице regCpuTable
            regCpuTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            regCpuTable.getActionMap().put(EDITING_OR_NAVIGATING, abstractAction);

            // Изменяем реакцию на клавишу Enter в таблице stackTable
            stackTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            stackTable.getActionMap().put(EDITING_OR_NAVIGATING, abstractAction);

            // Изменяем реакцию на клавишу Enter в таблице fMemDatTable
            fMemDatTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            fMemDatTable.getActionMap().put(EDITING_OR_NAVIGATING, abstractAction);
        }

        // Изменяем реакцию на клавишу Enter в таблице trapsTable
        trapsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
        trapsTable.getActionMap().put(EDITING_OR_NAVIGATING, new AbstractAction() {
            private static final long serialVersionUID = 4734432549045273397L;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final int rowM = getFocusedRowModel((JTable) actionEvent.getSource());
                if ((rowM != -1) && (rowM < fEmulatorLayer.getTrapCount())) {
                    final Trap trap = fEmulatorLayer.getTrap(rowM);
                    // Переходим на ловушку в таблице с кодом
                    fEmulatorLayer.setCodePage(trap.getPage());
                      fDisAsmTable.gotoAddress(trap.getAddress(), DA_COL_ADR);
                }
            }
        });

        // Изменяем реакцию на двойной клик мышью в колонке DA_COL_CMD таблицы fDisAsmTable
        fDisAsmTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if ((mouseEvent.getClickCount() == 2)
                        && (getFocusedColumnModel(fDisAsmTable) == DA_COL_CMD)
                        && (fFocusedAddress >= 0)
                        && ((DisAsmTableModel) fDisAsmTable.getModel()).isJmpAndCallCmd(getFocusedRowModel(fDisAsmTable))) {
                    fDisAsmTable.gotoAddress(fFocusedAddress, DA_COL_ADR); // Переход по адресу в команде
                }
            }
        });

        // Изменяем реакцию на двойной клик мышью в колонках таблицы с ловушками trapsTable
        trapsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    final int rowM = getFocusedRowModel((JTable) mouseEvent.getComponent());
                    if ((rowM != -1) && (rowM < fEmulatorLayer.getTrapCount())) {
                        Trap trap = fEmulatorLayer.getTrap(rowM);
                        // Переходим на ловушку в таблице с кодом
                        fEmulatorLayer.setCodePage(trap.getPage());
                          fDisAsmTable.gotoAddress(trap.getAddress(), DA_COL_ADR);
                    }
                }
            }
        });

        {
            // Обработчик событий для кнопок установки/сброса флагов
            final ActionListener actionListener = actionEvent -> {
                final int index = fFlags.indexOf(actionEvent.getActionCommand());
                if (index != -1) {
                    // Инвертируем бит, соответствующий названию кнопки
                    fEmulatorLayer.setValRegPair(DebugRegPair.AF, fEmulatorLayer.getValRegPair(DebugRegPair.AF) ^ (1 << (7 - index)));
                }
            };
            // Добавляем в компоненты
            for (final JButton flagButton : flagButtons) {
                if (flagButton != null) {
                    flagButton.addActionListener(actionListener);
                }
            }
        }

        // Обработчик событий для кнопки удаления ловушки
        deleteTrapButton.addActionListener(actionEvent -> {
            final int rowM = getFocusedRowModel(trapsTable);
            if ((rowM != -1) && (rowM < fEmulatorLayer.getTrapCount())) {
                Trap trap = fEmulatorLayer.getTrap(rowM);
                fEmulatorLayer.remTrap(trap.getPage(), trap.getAddress());
            }
        });

        // Обработчик событий для кнопки очистки ловушек
        clearTrapsButton.addActionListener(actionEvent -> fEmulatorLayer.clearTraps());

        // Обработчик выбора страницы памяти Code RAM
        codeMemPagesComboBox.addItemListener (itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                final int index = ((JComboBox) itemEvent.getSource()).getSelectedIndex();
                // Учитываются особенности класса MainMemory
                fEmulatorLayer.setCodePage((index == (fEmulatorLayer.getNumPages() - 1)) ? MainMemory.ROM_DISK : index);
            }
        });

        // Обработчик выбора страницы памяти Data RAM
        dataMemPagesComboBox.addItemListener (itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                final int index = ((JComboBox) itemEvent.getSource()).getSelectedIndex();
                // Учитываются особенности класса MainMemory
                // Выделение диапазона адресов закреплена за страницей: сохраняем у покидаемой, восстанавливаем у новой
                fMemDatTable.updateDataPage((index == (fEmulatorLayer.getNumPages() - 1)) ? MainMemory.ROM_DISK : index);
            }
        });

        // Определяем обработчики кнопок
          findButton.addActionListener(actionEvent -> findData          ());
        selectButton.addActionListener(actionEvent -> selectAddressRange());
          saveButton.addActionListener(actionEvent -> saveSelectedBlock ());
          loadButton.addActionListener(actionEvent -> loadBlockFromFile ());
          gotoButton.addActionListener(actionEvent -> gotoAddress       ());
          toPCButton.addActionListener(actionEvent -> {
            fEmulatorLayer.setCodePage(fEmulatorLayer.getCpuPage());
              fDisAsmTable.gotoAddress(fEmulatorLayer.getValRegPair(DebugRegPair.PC), DA_COL_ADR);
        });
         runButton.addActionListener(actionEvent -> runToCursor());
          okButton.addActionListener(actionEvent -> {
            fEmulatorLayer.saveAllRegPairs();
            setVisible(false);
        });

        // Обрабатываем события закрытия окна отладчика
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Срабатывает при нажатии по [X] (срабатывает раньше, чем получит управление вызвавшая отладчик программа)
                fEmulatorLayer.saveAllRegPairs();
            }

            // Срабатывает всегда после закрытия окна отладчика (срабатывает позже, чем получит управление вызвавшая отладчик программа)
            @Override
            public void windowClosed(WindowEvent e) {
                // Запоминаем номер страницы Data RAM
                PrevStaticData.setPrevDataPage      (fEmulatorLayer.getDataPage()      );
                // Запоминаем выделенные диапазоны адресов по страницам памяти.
                  fMemDatTable.savePageSelection    (fEmulatorLayer.getDataPage      ());
                PrevStaticData.setPrevDataSelections(  fMemDatTable.getPageSelections());
                // Запоминаем положение окна отладчика
                PrevStaticData.setPrevLocation(getLocation());
                // Запоминаем размеры фрейма в ini-файл
                fSpMX.putIni(Constants.INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , getWidth ());
                fSpMX.putIni(Constants.INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, getHeight());
            }
        });

        // -=-=-=-=- Завершение формирования окна отладчика -=-=-=-=-
        setSize(contentPanel.getPreferredSize().width, contentPanel.getPreferredSize().height);
        setMinimumSize(getSize());
        pack();

        // Восстанавливаем размеры фрейма из ini-файла
        {
            final Integer width  = fSpMX.getIni(Constants.INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , Integer.class);
            final Integer height = fSpMX.getIni(Constants.INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, Integer.class);
            if ((width != null) && (height != null)) {
                setSize(width, height);
            }
        }

        // Восстанавливаем выделенные диапазоны адресов по страницам памяти
        fMemDatTable.setPageSelections   (PrevStaticData.getPrevDataSelections());
        // Восстанавливаем выделенный диапазон адресов текущей страницы
        fMemDatTable.restorePageSelection(fEmulatorLayer.getDataPage());
        // Устанавливаем курсор на адрес = PC в таблице fDisAsmTable
        fDisAsmTable.gotoAddress         (fEmulatorLayer.getValRegPair(DebugRegPair.PC), DA_COL_ADR);

        final Point prevLocation = PrevStaticData.getPrevLocation();
        if ((prevLocation.getX() < 1.0D) && (prevLocation.getY() < 1.0D)) {
            // Выводим окно отладчика в центре родительского окна
            setLocationRelativeTo(getOwner());
        } else {
            // Иначе выводим окно на предыдущую позицию
            setLocation(prevLocation);
        }

        // Показываем окно отладчика
        setVisible(true);
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для дизассемблера".
     */
    private class DisAsmTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 4535761990838509705L;

        // Индекс адреса команды
        private static final int IND_ADR  = 0;
        // Индекс байта 0 команды CPU (код команды CPU)
        private static final int IND_CMD  = 1;
        // Индекс байта 1 команды CPU
        private static final int IND_BT1  = 2;
        // Индекс байта 2 команды CPU
        private static final int IND_BT2  = 3;
        // Индекс байта 3 команды CPU (только для Z80)
        private static final int IND_BT3  = 4;
        // Максимальная длина команды среди поддерживаемых процессоров
        // (размер буфера отладчика не должен зависеть от выбранного процессора)
        private final int fMaxCmdLength = DA_MAX_CMD_LENGTH;
        // Размер буфера: адрес + код операции + байты данных команды (всего fMaxCmdLength байт)
        private final int fRecSize      = fMaxCmdLength + 1;
        // Количество байт данных команды в буфере (кроме кода операции)
        private final int fNumDataBytes = fMaxCmdLength - 1;

        private final int[][] fStartBuffer;
        private final int[][] fMovedBuffer;

        /**
         * Конструктор.
         */
        DisAsmTableModel() {
            super();
            fStartBuffer = new int[BUF_SIZE][fRecSize]; // [x][0] - под адрес, [x][1..fRecSize-1] - под данные команды CPU
            fMovedBuffer = new int[BUF_SIZE][fRecSize]; // [x][0] - под адрес, [x][1..fRecSize-1] - под данные команды CPU
        }

        /**
         * Возвращает длину команды по заданному адресу.
         *
         * @param page    страница памяти
         * @param address адрес команды
         * @return длина команды в байтах
         */
        private int getCmdLength(final int page, final int address) {
            final int code = fEmulatorLayer.debugReadByte(page, address);
            if (fIsZ80) {
                final int next = fEmulatorLayer.debugReadByte(page, (address + 1) & 0xFFFF);
                return DisAsmZ80.getCmdLength(new int[] { code, next });
            }
            return DisAsmI8080.getCmdLength(code);
        }

        /**
         * Заполняет буфер адресами и данными команд CPU.
         *
         * @param buf         буфер
         * @param codeAddress адрес точного начала кода
         */
        private void fillBuffer(final int[][] buf, final int codeAddress) {
            final int page =  fEmulatorLayer.getCodePage();
            final int pc   = (fEmulatorLayer.getCpuPage () == page) ? fEmulatorLayer.getValRegPair(DebugRegPair.PC) : 0;

            int address = codeAddress;
            for (int i  = 0; i < BUF_SIZE; i++) {
                buf[i][IND_ADR] = address;
                final int  cmdLen  = getCmdLength(page, address);
                // Коллизия: PC попадает внутрь команды - показываем только байты до PC
                final boolean collide = (pc > address) && (pc < address + cmdLen);
                buf[i][IND_CMD] = fEmulatorLayer.debugReadByte(page, address);
                for (int j = 1; j <= fNumDataBytes; j++) {
                    if (collide) {
                        buf[i][IND_CMD + j] = (pc == address + j) ? WRONG_VALUE : EMPTY_VALUE;
                    } else {
                        buf[i][IND_CMD + j] = (j < cmdLen)
                                ? fEmulatorLayer.debugReadByte(page, (address + j) & 0xFFFF)
                                : EMPTY_VALUE;
                    }
                }
                address = (address + (collide ? pc - address : cmdLen)) & 0xFFFF;
            }
        }

        /**
         * Возвращает буфер (массив) с данными команды CPU по заданному адресу.
         *
         * @param address адрес
         * @return массив с данными команды CPU
         */
        private int[] getBuffer(final int address) {
            // Заполняем буфер данных из начальных адресов (таблица постоянно читает данные из начальной позиции)
            if (fStartBuffer[BUF_SIZE - 1][IND_ADR] == 0) {
                fillBuffer(fStartBuffer, 0);
            }

            final int[][] curBuf;
            // Начальный адрес данных после fStartBuffer
            int movedStart = (fStartBuffer[BUF_SIZE - 1][IND_ADR] + getRecordLength(fStartBuffer[BUF_SIZE - 1])) & 0xFFFF;
            // Определяем из какого буфера брать данные
            if (address < movedStart) {
                 curBuf = fStartBuffer;
            } else {
                 curBuf = fMovedBuffer;
                // Если в буфере данных из текущих адресов нет необходимых данных - заполняем буфер
                if ((address < fMovedBuffer[0][IND_ADR]) || (address > fMovedBuffer[BUF_SIZE - 1][IND_ADR])) {
                    if (address >= movedStart    + THREE_QUARTER + FOR_ALIGNMENT) {
                        if (address    >  0xFFFF - THREE_QUARTER) {
                            movedStart =  0xFFFF - BUF_SIZE      - FOR_ALIGNMENT;
                        } else {
                            movedStart = address - THREE_QUARTER - FOR_ALIGNMENT;
                        }
                        // Пытаемся выполнить выравнивание кода (в надежде, что повезет :-)
                        //noinspection StatementWithEmptyBody
                        for (int page = fEmulatorLayer.getCodePage(), endAddress = movedStart + FOR_ALIGNMENT, len;
                             movedStart + (len = getCmdLength(page, endAddress)) < endAddress;
                             movedStart += len) {
                            //
                        }
                    }
                    fillBuffer(fMovedBuffer, movedStart);
                }
            }
            // Ищем в выбранном буфере данные по заданному адресу
            for (int i = 0; i < BUF_SIZE; i++) {
                if (curBuf[i][IND_ADR] == address) {
                    return curBuf[i];
                }
            }
            return new int[0];
        }

        /**
         * Возвращает длину команды из буфера.
         *
         * @param buf буфер
         * @return длина команды в байтах
         */
        private int getRecordLength(final int[] buf) {
            int result = 1;
            if (buf[IND_CMD] >= 0) {
                while ((result <= fNumDataBytes) && (buf[IND_CMD + result] >= 0)) {
                    result++;
                }
            }
            return result;
        }

        /**
         * Возвращает результат дизассемблирования для буфера.
         *
         * @param buf буфер
         * @return результат дизассемблирования
         */
        @NotNull
        private DisAsmResult getDisAsmResult(final int[] buf) {
            final int[] bytes = new int[getRecordLength( buf)];
            System.arraycopy(buf, IND_CMD, bytes, 0, bytes.length);
            return getDisAsm().disAsm(buf[IND_ADR], bytes);
        }

        /**
         * Возвращает дизассемблер активного процессора.
         *
         * @return дизассемблер
         */
        @NotNull
        private DisAsm getDisAsm() {
            return fIsZ80 ? DISASM_Z80 : DISASM_I8080;
        }

        /**
         * Возвращает true, если по заданному адресу находится команда передачи управления (JMP/CALL).
         *
         * @param address адрес
         * @return результат
         */
        boolean isJmpAndCallCmd(final int address) {
            final int[] buf = getBuffer(  address);
            if (buf.length == fRecSize) {
                final DisAsmResult disAsmResult = getDisAsmResult(buf);
                return disAsmResult.isJmpCmd() || disAsmResult.isCallCmd();
            }
            return false;
        }

        /**
         * Возвращает адрес из команды CPU или отрицательное значение,
         * если в команде нет 16-битного адреса.
         * <p>
         * У I8080 байты команды идут в порядке младший-старший, а команды длиной три байта
         * содержат 16-битное значение в байтах 1 и 2, поэтому его можно взять напрямую.
         * У Z80 адрес может стоять в третьем и четвёртом байтах ({@code ED xx nn nn},
         * {@code LD IX,nn}), а у {@code JR}/{@code DJNZ} вместо адреса записано смещение,
         * поэтому байты для адреса выбирает дизассемблер.
         *
         * @param address адрес команды
         * @return адрес из команды или отрицательное значение
         */
        int getAddressFromCmd(final int address) {
            final int[] buf = getBuffer(address);
            if (buf.length == fRecSize) {
                if (fIsZ80) {
                    // У Z80 байты для адреса выбирает дизассемблер:
                    // адрес может стоять в байтах 2-3 (ED xx nn nn, LD IX,nn),
                    // а у JR/DJNZ вместо адреса записано смещение
                    final int[] bytes = new int[getRecordLength(buf)];
                    System.arraycopy(buf, IND_CMD, bytes, 0, bytes.length);
                    return DisAsmZ80.getCmdAddress(address, bytes);
                }
                // Байты в буфере хранятся в порядке памяти, как и в самой команде CPU
                return buf[IND_BT1] | (buf[IND_BT2] << 8);
            }
            return EMPTY_VALUE;
        }

        /**
         * Возвращает адрес следующей команды после команды вызова подпрограммы (CALL/RST) или
         * отрицательное значение, если по заданному адресу нет команды вызова подпрограммы.
         *
         * @param address адрес
         * @return адрес следующей команды или отрицательное значение
         */
        int getAddressAfterCallCmd(final int address) {
            final int[] buf  = getBuffer(    address);
            if ( buf.length == fRecSize) {
                final DisAsmResult result = getDisAsmResult(buf);
                if (result.isCallCmd()) {
                    return (address + result.getLength()) & 0xFFFF;
                }
            }
            return EMPTY_VALUE;
        }

        @Override
        public void fireTableDataChanged() {
            fStartBuffer[BUF_SIZE - 1][IND_ADR] = 0;
            fMovedBuffer[           0][IND_ADR] = 0;
            fMovedBuffer[BUF_SIZE - 1][IND_ADR] = 0;
            super.fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return DA_NUM_ROWS;
        }

        @Override
        public int getColumnCount() {
            return DA_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            final Object obj;
            if (   (columnIndex >= 0)
                && (columnIndex <= getColumnCount())
                && ((  obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(final int column) {
            switch (column) {
                case DA_COL_TRP:
                    return "Л.";
                case DA_COL_ADR:
                    return STR_ADDRESS;
                case DA_COL_CMD:
                    return "Команда";
                default:
                    // Колонки байтов команды: 0, 1, 2, 3
                    return ((column >= DA_COL_BT0) && (column <= DA_COL_BT3))
                           ? Integer.toString(column - DA_COL_BT0)
                           : super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final int[] buf = getBuffer(   rowIndex);
            if  (buf.length == fRecSize) {
                switch (columnIndex) {
                    case DA_COL_TRP:
                        return fEmulatorLayer.isTrap(fEmulatorLayer.getCodePage(), rowIndex);
                    case DA_COL_ADR:
                        return String.format("%04X:", buf[IND_ADR]);
                    case DA_COL_CMD:
                        return getDisAsmResult(buf).getText();
                    default:
                        if ((columnIndex >= DA_COL_BT0) && (columnIndex <= DA_COL_BT3)) {
                            final int bt = buf[IND_CMD   +  columnIndex  - DA_COL_BT0];
                            return   (bt >= 0) ? String.format("%02X", bt) : "";
                        }
                        return "";
                }
            } else {
                if (columnIndex == DA_COL_TRP) {
                    return Boolean.FALSE;
                } else {
                    return "";
                }
            }
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            switch (columnIndex) {
                case DA_COL_TRP:
                    return true ;
                case DA_COL_ADR:
                case DA_COL_CMD:
                    return false;
                default:
                    return (   (columnIndex >= DA_COL_BT0)
                            && (columnIndex <= DA_COL_BT3)
                            && isEditable(fEmulatorLayer.getCodePage(), () -> getBuffer(rowIndex)[IND_ADR]))
                           ? !((String) getValueAt(rowIndex, columnIndex)).isEmpty()
                           :  super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            final int page = fEmulatorLayer.getCodePage();
            switch (columnIndex) {
                case DA_COL_TRP:
                    if (Boolean.TRUE.equals(aValue)) {
                        fEmulatorLayer.addTrap(page, rowIndex, false);
                    } else {
                        fEmulatorLayer.remTrap(page, rowIndex);
                    }
                    return;
                //case DA_COL_ADR:
                //    break;~
                //case DA_COL_CMD:
                //    break;~
                default:
                    if ((columnIndex >= DA_COL_BT0) && (columnIndex <= DA_COL_BT3)) {
                        try {
                            fEmulatorLayer.writeByte(page,rowIndex + columnIndex - DA_COL_BT0, Integer.parseInt((String) aValue, 16));
                        } catch (NumberFormatException e) {
                            showMessageDialog(DebuggerCPU.this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
                        }
                    }
                    break;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с дизассемблированными данными (дизассемблер)".
     */
    private class DisAsmTable extends JTable implements Observer {
        private static final long serialVersionUID = 6082699292929964402L;

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик булевых полей дизассемблера".
         */
        private class DisAsmBooleanRenderer extends JCheckBox implements TableCellRenderer {
            private static final long serialVersionUID = -9114406285821023267L;

            /**
             * Конструктор.
             */
            DisAsmBooleanRenderer() {
                super();
                setBorderPainted(true);
                setHorizontalAlignment(CENTER);
            }

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {
                if (table == null) {
                    return this;
                }

                final boolean booleanValue =    (value != null) && (boolean) value;
                final boolean     equalsPC =    (fEmulatorLayer.getCodePage() == fEmulatorLayer.getCpuPage())
                                             && (table.convertRowIndexToModel(row) == fEmulatorLayer.getValRegPair(DebugRegPair.PC));

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    if (booleanValue) {
                        setBackground(YELLOW_RED  );
                    } else if (equalsPC) {
                        setBackground(YELLOW_GREEN);
                    } else {
                        setBackground(table.getSelectionBackground());
                    }
                } else {
                    setForeground(table.getForeground());
                    if (booleanValue) {
                        setBackground(Color.red  );
                    } else if (equalsPC) {
                        setBackground(Color.green);
                    } else {
                        setBackground(table.getBackground());
                    }
                }
                setSelected(booleanValue);

                if (hasFocus) {
                    if (fFocusedAddress >= 0) {
                        fFocusedAddress = -1;
                        EventQueue.invokeLater(table::repaint);
                    }
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }
                return this;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей дизассемблера".
         */
        private class DisAsmStringRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = 1263217420662494640L;

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {

                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == null) {
                    return this;
                }

                final DisAsmTableModel model = (DisAsmTableModel) table.getModel();

                final int address = table.convertRowIndexToModel   (row   );
                final int columnM = table.convertColumnIndexToModel(column);
                final int pc      = fEmulatorLayer.getValRegPair(DebugRegPair.PC  );

                if (columnM == DA_COL_CMD) {
                    setHorizontalAlignment(LEFT);
                } else {
                    setHorizontalAlignment(CENTER);
                }

                if (isSelected) {
                    if (   ((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD))
                        && (fFocusedAddress >= 0)
                        && ((columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) == fFocusedAddress)) {
                        setForeground(Color.red);
                    } else {
                        setForeground(table.getSelectionForeground());
                    }
                    if ((fEmulatorLayer.getCodePage() == fEmulatorLayer.getCpuPage()) && (address == pc)) {
                        setBackground(YELLOW_GREEN);
                    } else {
                        setBackground(table.getSelectionBackground());
                    }
                } else {
                    if (   ((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD))
                        && (fFocusedAddress >= 0)
                        && ((columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) == fFocusedAddress)) {
                        setForeground(Color.red);
                    } else {
                        setForeground(table.getForeground());
                    }
                    if ((fEmulatorLayer.getCodePage() == fEmulatorLayer.getCpuPage()) && (address == pc)) {
                        setBackground(Color.green);
                    } else {
                        setBackground(table.getBackground());
                    }
                }

                if (hasFocus) {
                    int addressCmd = -1;
                    if (   ((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD))
                        && ((addressCmd = columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) >= 0)) {
                        setForeground(Color.red);
                    }
                    if (fFocusedAddress != addressCmd) {
                        fFocusedAddress  = addressCmd;
                        EventQueue.invokeLater(table::repaint);
                    }
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }

                setValue(value);
                return this;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         *
         * @param dm модель данных
         */
        DisAsmTable(TableModel dm) {
            super(dm);
            // Настаиваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(DA_COL_TRP).setMaxWidth(22);
            getColumnModel().getColumn(DA_COL_ADR).setMaxWidth(44);
            for (int i = DA_COL_BT0; i <= DA_COL_BT3; i++) {
                getColumnModel().getColumn(i).setMaxWidth(22);
            }
            // Колонка "Команда" фиксированной ширины - по самой длинной мнемонике с операндами:
            // минимальная ширина = максимальной, чтобы JTable не отдавал остаток ширины таблицы
            // первым колонкам и текст не обрезался
            final TableColumn cmdColumn = getColumnModel().getColumn(DA_COL_CMD);
            cmdColumn.setMinWidth(DA_CMD_WIDTH);
            cmdColumn.setMaxWidth(DA_CMD_WIDTH);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            // Подключаем рисовальщиков колонок
            setDefaultRenderer(Boolean.class, new DisAsmBooleanRenderer());
            setDefaultRenderer( String.class, new DisAsmStringRenderer ());
            // Блокируем вывод пустых строк и отключаем сортировку колонок
            final TableRowSorter<TableModel> sorter = new TableRowSorter<>(dm);
            for (int column = 0; column < sorter.getModel().getColumnCount(); column++) {
                sorter.setSortable(column, false);
            }
            sorter.setRowFilter(RowFilter.regexFilter(".", DA_COL_CMD));
            setRowSorter(sorter);
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.MEMORY  , fEmulatorLayer.getCodePage())
                || fEmulatorLayer.eventCheck(event, EventType.PAGE    , MemoryPageType.CODE )
                || fEmulatorLayer.eventCheck(event, EventType.REG_PAIR,   DebugRegPair.PC   )
                || fEmulatorLayer.eventCheck(event, EventType.STEP    , null)) {
                final int rowM = getFocusedRowModel   (this);
                final int colM = getFocusedColumnModel(this);
                // Вызываем обновление данных
                ((AbstractTableModel) getModel()).fireTableDataChanged();
                // Восстанавливаем предыдущую позицию
                gotoAddress(rowM, colM);
            } else if (fEmulatorLayer.eventCheck(event, EventType.TRAPS, null)) {
                final Object detail = event.getDetail();
                if ((detail == null) || ((detail instanceof Trap) && (((Trap) detail).getPage() == fEmulatorLayer.getCodePage()))) {
                    repaint();
                }
            }
        }

        /**
         * Выполняет переход к заданному адресу в таблице дизассемблера.
         *
         * @param address адрес
         * @param colM    колонка (в формате табличной модели), которую необходимо выделить
         */
        void gotoAddress(final int address,  final int colM) {
            final int colV  = convertColumnIndexToView(colM);
            if (      colV != -1) {
                // Пытаемся найти видимую строку <= address
                int rowV;
                int rowM = address & 0xFFFF;
                while (((rowV = convertRowIndexToView(rowM)) == -1) && (rowM > 0)) {
                    rowM--;
                }
                // Если нашли строку, то выполняем позиционирование на неё
                if (rowV != -1) {
                    // Сбрасываем fFocusedAddress
                    fFocusedAddress = -1;
                    gotoTableCell(this, rowV, colV, true);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Метка для отображения содержимого регистра флагов".
     */
    private class FlagsRegLabel extends JLabel implements Observer {
        private static final long serialVersionUID = 2564883968431592392L;

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик для FlagsRegLabel".
         */
        private class FlagsRegLabelUI extends BasicLabelUI {
            @Override
            protected void paintEnabledText(final JLabel l, final Graphics g, final String s, int textX, final int textY) {
                if (!s.isEmpty()) {
                    final int y      = l.getY();
                    final int height = l.getHeight();
                    final int index  = l.getDisplayedMnemonicIndex();

                    final int pAF = fEmulatorLayer.getPrevValRegPair(DebugRegPair.AF);
                    final int cAF = fEmulatorLayer.getValRegPair    (DebugRegPair.AF);

                    char c;
                    int  width;
                    for (int i = 0, mask = 0b1000_0000; i < 8; i++, mask >>= 1) {
                         c     = s.charAt(i);
                         width = g.getFontMetrics().charWidth(c);

                        if (((pAF ^ cAF) & mask) == 0) {
                            g.setColor(l.getForeground());
                        } else {
                            g.setColor(Color.yellow);
                            g.fillRect(textX, y, width, height);
                            g.setColor(Color.red   );
                        }
                        drawStringUnderlineCharAt(g, String.valueOf(c), index, textX, textY);
                        textX += width;
                    }
                }
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         */
        FlagsRegLabel() {
            super(fEmulatorLayer.getVisViewsFlagsReg(fFlags));
            // Устанавливаем рисовальщик для метки
            setUI(new FlagsRegLabelUI());
            // Настаиваем параметры отображения метки
            setOpaque(true);
            setFont  (new Font(Font.MONOSPACED, Font.PLAIN, 13));
            setBorder(FOCUS_BORDER);
            setBackground(Color.white);
            setHorizontalAlignment(SwingConstants.CENTER);
            // Подключаемся к fWrapCPU для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.REG_PAIR, DebugRegPair.AF)
                || fEmulatorLayer.eventCheck(event, EventType.STEP    , null)) {
                setText(fEmulatorLayer.getVisViewsFlagsReg(fFlags));
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для отображения и редактирования регистровых пар CPU".
     */
    private class RegCpuTableModel extends AbstractTableModel {
        private static final long serialVersionUID = -7748443612425003819L;

        /**
         * Конструктор.
         */
        RegCpuTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return DebugRegPair.getSupportedCount(fIsZ80, fShowIndexRegisters);
        }

        @Override
        public int getColumnCount() {
            return CR_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            final Object obj;
            if (   (columnIndex >= 0)
                && (columnIndex <= getColumnCount())
                && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(final int column) {
            switch (column) {
                case CR_COL_REG:
                    return "РП";
                case CR_COL_EQU:
                    return  "=";
                case CR_COL_DAT:
                    return "Знач.";
                default:
                    return super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final DebugRegPair regPair = DebugRegPair.getByRowIndex(rowIndex, fIsZ80, fShowIndexRegisters);
            if (regPair == null) {
                return null;
            }
            switch (columnIndex) {
                case CR_COL_REG:
                    return regPair.getName();
                case CR_COL_EQU:
                    return "=";
                case CR_COL_DAT:
                    return String.format("%04X", fEmulatorLayer.getValRegPair(regPair));
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            switch (columnIndex) {
                case CR_COL_REG:
                case CR_COL_EQU:
                    return false;
                case CR_COL_DAT:
                    return DebugRegPair.getByRowIndex(rowIndex, fIsZ80, fShowIndexRegisters) != null;
                default:
                    return super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            if (columnIndex == CR_COL_DAT) {
                final DebugRegPair regPair = DebugRegPair.getByRowIndex(rowIndex, fIsZ80, fShowIndexRegisters);
                if (regPair == null) {
                    return;
                }
                try {
                    // Пишем данные в регистровые пары
                    fEmulatorLayer.setValRegPair(regPair, Integer.parseInt((String) aValue, 16));
                    if (DebugRegPair.PC.equals(regPair)) {
                        // Если была запись в PC, то показываем код из страницы CPU и переходим на address = PC
                        fEmulatorLayer.setCodePage(fEmulatorLayer.getCpuPage());
                          fDisAsmTable.gotoAddress(fEmulatorLayer.getValRegPair(DebugRegPair.PC), DA_COL_ADR);
                    }
                } catch (NumberFormatException e) {
                    showMessageDialog(DebuggerCPU.this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
                }
                return;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица для отображения и редактирования регистровых пар CPU".
     */
    private class RegCpuTable extends JTable implements Observer {
        private static final long serialVersionUID = 1256251256303710285L;

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы регистровых пар".
         */
        private class RegCpuStringRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = -3945328100541928359L;

            private int fPaintColumn  ;
            private int fCompareResult;
            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Класс "Рисовальщик ячеек (JLabel) с регистровыми парами".
             * Раскрашивает разными цветами название регистровой пары, а также старший и младший байты регистровой пары.
             */
            private class MultiColorLabelUI extends BasicLabelUI {
                @Override
                protected void paintEnabledText(final JLabel l, final Graphics g, String s, int textX, final int textY) {
                    if (!s.isEmpty()) {
                        final int index = l.getDisplayedMnemonicIndex();
                            Color color = (fCompareResult <= 0) ? l.getForeground() : Color.red;
                        if ((fCompareResult == 0) || (fCompareResult == 1)) {
                            final int endIndex = (fPaintColumn == CR_COL_REG) ? 1 : 2;
                            final String   str = s.substring(0, endIndex);

                            g.setColor(color);
                            drawStringUnderlineCharAt(g, str, index, textX, textY);
                            textX += g.getFontMetrics().stringWidth(str);

                                s = s.substring(endIndex);
                            color = (fCompareResult == 0) ? Color.red : l.getForeground();
                        }
                        g.setColor(color);
                        drawStringUnderlineCharAt(g, s, index, textX, textY);
                    }
                }
            }
            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Конструктор.
             */
            RegCpuStringRenderer() {
                super();
                setUI(new MultiColorLabelUI());
            }

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {

                super.setHorizontalAlignment(CENTER);
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == null) {
                    return this;
                }
                fPaintColumn   =  table.convertColumnIndexToModel(column);
                final DebugRegPair regPair = DebugRegPair.getByRowIndex(table.convertRowIndexToModel(row), fIsZ80, fShowIndexRegisters);
                fCompareResult =  (regPair != null) ? fEmulatorLayer.getChangesRegPair(regPair) : -1;

                if (fCompareResult >= 0) {
                    setBackground(table.getSelectionBackground());
                } else {
                    setBackground(table.getBackground());
                }

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                } else {
                    setForeground(table.getForeground());
                }

                if (hasFocus) {
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }

                if ((fCompareResult >=  0) && (fPaintColumn == CR_COL_EQU)) {
                     fCompareResult  = -1;
                }

                setValue(value);
                return   this  ;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         * @param dm модель данных для таблицы.
         */
        RegCpuTable(TableModel dm) {
            super(dm);
            // Настраиваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT );
            setSelectionBackground  (Color.yellow);
            getColumnModel().getColumn(CR_COL_REG).setMaxWidth(28);
            getColumnModel().getColumn(CR_COL_EQU).setMaxWidth(19);
            getColumnModel().getColumn(CR_COL_DAT).setMaxWidth(40);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new RegCpuStringRenderer());
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.REG_PAIR, null)
                || fEmulatorLayer.eventCheck(event, EventType.STEP    , null)) {
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных таблицы с данными стека".
     */
    private class StackTableModel extends AbstractTableModel {
        private static final long serialVersionUID = -8251032431926800440L;

        /**
         * Конструктор.
         */
        StackTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return SP_NUM_ROWS;
        }

        @Override
        public int getColumnCount() {
            return SP_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            final Object obj;
            if (   (columnIndex >= 0)
                && (columnIndex <= getColumnCount())
                && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(final int column) {
            if (column == SP_COL_DAT) {
                return "(SP)";
            }
            return super.getColumnName(column);
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            if (columnIndex == SP_COL_DAT) {
                final int page    = fEmulatorLayer.getCpuPage();
                final int address = fEmulatorLayer.getValRegPair(DebugRegPair.SP) + (rowIndex << 1);
                return String.format("%04X",
                        fEmulatorLayer.debugReadByte(page, address) | (fEmulatorLayer.debugReadByte(page, address + 1) << 8));
            }
            return null;
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return (columnIndex == SP_COL_DAT) || super.isCellEditable(rowIndex, columnIndex);
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            if (columnIndex == SP_COL_DAT) {
                final int page    = fEmulatorLayer.getCpuPage();
                final int address = fEmulatorLayer.getValRegPair(DebugRegPair.SP) + (rowIndex << 1);
                try {
                    final int value = Integer.parseInt((String) aValue, 16);
                    fEmulatorLayer.disableEvents();
                    fEmulatorLayer.writeByte(page, address, value & 0xFF);
                    fEmulatorLayer.writeByte(page, address + 1, value >> 8);
                } catch (NumberFormatException e) {
                    showMessageDialog(DebuggerCPU.this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
                } finally {
                    if (fEmulatorLayer.isEventsDisabled()) {
                        fEmulatorLayer.enableEvents();
                        fEmulatorLayer.sendEvent(EventType.MEMORY, page);
                    }
                }
                return;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с данными стека".
     */
    private class StackTable extends JTable implements Observer {
        private static final long serialVersionUID = -5587123808216823239L;

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными стека".
         */
        private class StackStringRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = 2680876900372610398L;

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {

                super.setHorizontalAlignment(CENTER);
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (table == null) {
                    return this;
                }

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    setBackground(table.getSelectionBackground());
                } else {
                    setForeground(table.getForeground());
                    setBackground(table.getBackground());
                }

                if (hasFocus) {
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }

                setValue(value);
                return this;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         * @param dm модель данных для таблицы.
         */
        StackTable(TableModel dm) {
            super(dm);
            // Настаиваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT );
            setSelectionBackground  (Color.yellow);
            getColumnModel().getColumn(SP_COL_DAT).setMaxWidth(40);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new StackStringRenderer());
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.REG_PAIR, DebugRegPair.SP)
                || fEmulatorLayer.eventCheck(event, EventType.MEMORY  , fEmulatorLayer.getCpuPage())
                || fEmulatorLayer.eventCheck(event, EventType.PAGE    , MemoryPageType.CPU)
                || fEmulatorLayer.eventCheck(event, EventType.STEP    , null)) {
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных таблицы с данными ловушек".
     */
    private class TrapsTableModel extends AbstractTableModel {
        private static final long serialVersionUID = -8308426780522745124L;

        /**
         * Конструктор.
         */
        TrapsTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return fEmulatorLayer.getTrapCount();
        }

        @Override
        public int getColumnCount() {
            return TP_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            final Object obj;
            if (   (columnIndex >= 0)
                && (columnIndex <= getColumnCount())
                && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(final int column) {
            switch (column) {
                case TP_COL_PAG:
                    return "Страница";
                case TP_COL_ADR:
                    return STR_ADDRESS;
                default:
                    return super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final Trap trap = fEmulatorLayer.getTrap(rowIndex);
            switch (columnIndex) {
                case TP_COL_PAG:
                    return fEmulatorLayer.getPageName(trap.getPage());
                case TP_COL_ADR:
                    return String.format("%04X",trap.getAddress());
                default:
                    return null;
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с данными ловушек".
     */
    private class TrapsTable extends JTable implements Observer {
        private static final long serialVersionUID = -2863867273218289186L;

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными ловушек".
         */
        private class TrapsStringRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = -7287183392747810798L;

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {

                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == null) {
                    return this;
                }

                if (table.convertColumnIndexToModel(column) == TP_COL_PAG) {
                    setHorizontalAlignment(LEFT);
                } else {
                    setHorizontalAlignment(CENTER);
                }

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    setBackground(table.getSelectionBackground());
                } else {
                    setForeground(table.getForeground());
                    setBackground(table.getBackground());
                }

                if (hasFocus) {
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }

                setValue(value);
                return this;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         * @param dm модель данных для таблицы.
         */
        TrapsTable(TableModel dm) {
            super(dm);
            // Настаиваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT );
            setSelectionBackground  (Color.yellow);
            getColumnModel().getColumn(TP_COL_PAG).setMaxWidth(94);
            getColumnModel().getColumn(TP_COL_ADR).setMaxWidth(46);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new TrapsStringRenderer());
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (fEmulatorLayer.eventCheck(event, EventType.TRAPS, null)) {
                // Обновляем данные в таблице
                ((AbstractTableModel) getModel()).fireTableDataChanged();
                // Позиционируемся на добавленную ловушку
                final int index;
                final Object detail = event.getDetail();
                if ((detail instanceof Trap) && ((index = fEmulatorLayer.getTrapIndex((Trap) detail)) != -1)) {
                    gotoTableCell(this, convertRowIndexToView(index), convertColumnIndexToView(TP_COL_PAG), false);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Метка для отображения информации о странице памяти CPU".
     */
    private class CpuMemPageLabel extends JLabel implements Observer {
        private static final long serialVersionUID = -3063697623043729485L;

        /**
         * Конструктор.
         */
        CpuMemPageLabel() {
            super();
            // Настаиваем параметры отображения метки
            setOpaque(true);
            setFont(DEFAULT_FONT);
            setBorder(FOCUS_BORDER);
            setText(getCpuPageInfo());
            setBackground(Color.white);
            setHorizontalAlignment(SwingConstants.CENTER);
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        /**
         * Возвращает информации о странице памяти CPU в виде строки.
         * @return строка с информацией
         */
        private String getCpuPageInfo() {
            return "CPU работает в: ".concat(fEmulatorLayer.getPageName(fEmulatorLayer.getCpuPage()));
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.PAGE, MemoryPageType.CPU)
                || fEmulatorLayer.eventCheck(event, EventType.STEP, null)) {
                setText(getCpuPageInfo());
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Раскрывающийся список страниц памяти для просмотра кода".
     */
    private class CodeMemPagesComboBox extends JComboBox<String> implements Observer {
        private static final long serialVersionUID = -3181859535069551623L;

        /**
         * Конструктор.
         */
        CodeMemPagesComboBox() {
            super();
            // Настаиваем параметры отображения метки
            setFont(DEFAULT_FONT);
            setMinimumSize(new Dimension(50, 17));
            // Добавляем элементы списка
            for (int i = 0, j = fEmulatorLayer.getNumPages(); i < j; i++) {
                addItem("← ".concat(fEmulatorLayer.getPageName(i)));
            }
            // Выделяем элемент, соответствующий странице памяти Data RAM
            setSelectedIndex((fEmulatorLayer.getCodePage() >= MainMemory.ROM_DISK) ?
                    (getItemCount() - 1) : fEmulatorLayer.getCodePage());
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.PAGE, MemoryPageType.CODE)
                || fEmulatorLayer.eventCheck(event, EventType.STEP, null)) {
                final int index = (fEmulatorLayer.getCodePage() >= MainMemory.ROM_DISK) ?
                        (getItemCount() - 1) : fEmulatorLayer.getCodePage();
                if (getSelectedIndex() != index) {
                    setSelectedIndex(index);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Раскрывающийся список страниц памяти для просмотра данных".
     */
    private class DataMemPagesComboBox extends JComboBox<String> implements Observer {
        private static final long serialVersionUID = 5333943988947208325L;

        /**
         * Конструктор.
         */
        DataMemPagesComboBox() {
            super();
            // Настаиваем параметры отображения списка
            setFont(DEFAULT_FONT);
            setMinimumSize(new Dimension(50, 17));
            // Добавляем элементы списка
            for (int i = 0, j = fEmulatorLayer.getNumPages(); i < j; i++) {
                addItem("↓ ".concat(fEmulatorLayer.getPageName(i)));
            }
            // Выделяем элемент, соответствующий странице памяти Data RAM
            setSelectedIndex((fEmulatorLayer.getDataPage() >= MainMemory.ROM_DISK) ?
                    (getItemCount() - 1) : fEmulatorLayer.getDataPage());
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (fEmulatorLayer.eventCheck(event, EventType.PAGE, MemoryPageType.DATA)) {
                final int index = (fEmulatorLayer.getDataPage() >= MainMemory.ROM_DISK) ?
                        (getItemCount() - 1) : fEmulatorLayer.getDataPage();
                if (getSelectedIndex() != index) {
                    setSelectedIndex(index);
                }
            }
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Очень специфичная (под JTable) симуляция выделения строк, по аналогии с текстовым редактором
     * (не все удалось реализовать, но результат скорее хороший).
     */
    private static class SelectionModelsContainer {
        private final int columnMinIndex;
        private final int columnMaxIndex;

        private final ListSelectionModel columnSelectionModelTop   ;
        private final ListSelectionModel columnSelectionModelMiddle;
        private final ListSelectionModel columnSelectionModelBottom;
        private final ListSelectionModel columnSelectionModel      ;
        private final ListSelectionModel    rowSelectionModel      ;

        private ListSelectionModel currentColumnSelectionModel;

        private int currentColumnSelectionIndex0 = -1;
        private int currentColumnSelectionIndex1 = -1;

        SelectionModelsContainer(final int columnMinIndex, final int columnMaxIndex) {
            this.columnMinIndex = columnMinIndex;
            this.columnMaxIndex = columnMaxIndex;

            this.columnSelectionModelTop    = new DefaultListSelectionModel();
            this.columnSelectionModelMiddle = new DefaultListSelectionModel();
            this.columnSelectionModelBottom = new DefaultListSelectionModel();
            this.columnSelectionModel       = new      ColumnSelectionModel();
            this.   rowSelectionModel       = new         RowSelectionModel();

            this.columnSelectionModelTop   .setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            this.columnSelectionModelMiddle.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            this.columnSelectionModelBottom.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            this.   rowSelectionModel      .setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

            this.columnSelectionModelMiddle.setSelectionInterval(columnMinIndex, columnMaxIndex);

            this.currentColumnSelectionModel = this.columnSelectionModelTop;
        }

        ListSelectionModel getRowSelectionModel() {
            return rowSelectionModel;
        }

        ListSelectionModel getColumnSelectionModel() {
            return columnSelectionModel;
        }

        /**
         * Получает начальный адрес выделения.
         *
         * @return начальный адрес выделения
         */
        int getStartSelectedAddress() {
            final int rowMinSelIndex = rowSelectionModel.getMinSelectionIndex();
            // Для чтения верхней строки
            rowSelectionModel.isSelectedIndex(rowMinSelIndex);
            final int columnMinSelIndex = currentColumnSelectionModel.getMinSelectionIndex();
            return (( columnMinSelIndex < columnMinIndex) || (columnMinSelIndex > columnMaxIndex)) ?
                    -1 : ((rowMinSelIndex << 4) + columnMinSelIndex - columnMinIndex);
        }

        /**
         * Получает конечный адрес выделения.
         *
         * @return конечный адрес выделения.
         */
        int getEndSelectedAddress() {
            final int rowMaxSelIndex = rowSelectionModel.getMaxSelectionIndex();
            // Для чтения нижней строки
            rowSelectionModel.isSelectedIndex(rowMaxSelIndex);
            final int columnMaxSelIndex = currentColumnSelectionModel.getMaxSelectionIndex();
            return (( columnMaxSelIndex < columnMinIndex) || (columnMaxSelIndex > columnMaxIndex)) ?
                    -1 : ((rowMaxSelIndex << 4) + columnMaxSelIndex - columnMinIndex);
        }

        /**
         * Выделяет диапазон адресов.
         *
         * @param startAddress адрес начала
         * @param endAddress   адрес конца
         */
        void selectAddressRange(int startAddress, int endAddress) {
            startAddress &= 0xFFFF;
              endAddress &= 0xFFFF;

            if (startAddress > endAddress) {
                return;
            }

               rowSelectionModel.clearSelection();
            columnSelectionModel.clearSelection();

            final int    rowMinSelIndex = startAddress >> 4;
            final int    rowMaxSelIndex =   endAddress >> 4;
            final int columnMinSelIndex = startAddress - (rowMinSelIndex << 4) + columnMinIndex;
            final int columnMaxSelIndex =   endAddress - (rowMaxSelIndex << 4) + columnMinIndex;

            if (rowMinSelIndex != rowMaxSelIndex) {
                // выделяем левую точку
                columnSelectionModel.setSelectionInterval(columnMinSelIndex, columnMinSelIndex);
                   rowSelectionModel.setSelectionInterval(   rowMinSelIndex,    rowMinSelIndex);
                // заполняем до правой точки
                columnSelectionModel.setSelectionInterval(columnMaxSelIndex, columnMaxSelIndex);
            } else {
                columnSelectionModel.setSelectionInterval(columnMinSelIndex, columnMaxSelIndex);
            }
            rowSelectionModel.setSelectionInterval(rowMinSelIndex, rowMaxSelIndex);
        }

        private class RowSelectionModel extends DefaultListSelectionModel {
            private static final long serialVersionUID = 3849612944826298882L;

            @Override
            public boolean isSelectedIndex(final int index) {
                boolean result = super.isSelectedIndex(index);
                if (result) {
                    int rowMinSelIndex = this.getMinSelectionIndex();
                    int rowMaxSelIndex = this.getMaxSelectionIndex();

                    if (index == rowMinSelIndex) {
                        currentColumnSelectionModel = columnSelectionModelTop;
                        if (rowMinSelIndex != rowMaxSelIndex) {
                            final int columnMinSelIndex = currentColumnSelectionModel.getMinSelectionIndex();
                            final int columnMaxSelIndex = currentColumnSelectionModel.getMaxSelectionIndex();
                            final int columnMinSetIndex = ((columnMinSelIndex >= columnMinIndex) && (columnMinSelIndex <= columnMaxIndex))
                                    ? columnMinSelIndex : columnMinIndex;

                            if ((columnMinSelIndex != columnMinSetIndex) || (columnMaxSelIndex != columnMaxIndex)) {
                                currentColumnSelectionModel.setSelectionInterval(columnMaxIndex, columnMinSetIndex);
                            }
                        }
                    } else if (index == rowMaxSelIndex) {
                        currentColumnSelectionModel = columnSelectionModelBottom;
                        final int columnMinSelIndex = currentColumnSelectionModel.getMinSelectionIndex();
                        final int columnMaxSelIndex = currentColumnSelectionModel.getMaxSelectionIndex();
                        final int columnMaxSetIndex = ((columnMaxSelIndex >= columnMinIndex) && (columnMaxSelIndex <= columnMaxIndex))
                                ? columnMaxSelIndex : columnMaxIndex;

                        if ((columnMinSelIndex != columnMinIndex)  ||  (columnMaxSelIndex != columnMaxSetIndex)) {
                            currentColumnSelectionModel.setSelectionInterval(columnMinIndex, columnMaxSetIndex);
                        }
                    } else {
                        currentColumnSelectionModel = columnSelectionModelMiddle;
                    }
                }
                return result;
            }

            @Override
            public void setSelectionInterval(final int index0, final int index1) {
                final boolean isManyRows = super.getMinSelectionIndex() != super.getMaxSelectionIndex();
                super.setSelectionInterval(index0, index1);

                if (   (index0 == -1) || (index1 == -1)
                    || (currentColumnSelectionIndex0 == -1) || (currentColumnSelectionIndex1 == -1)) {
                    return;
                }

                final int  rowMinSelIndex = Math.min(index0, index1);
                final int  rowMaxSelIndex = Math.max(index0, index1);
                final int rowLeadSelIndex = super.getLeadSelectionIndex();

                final int columnMinSelIndex = Math.min(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                final int columnMaxSelIndex = Math.max(currentColumnSelectionIndex0, currentColumnSelectionIndex1);

                if (rowMinSelIndex == rowMaxSelIndex) {
                    // При переходе из многих строк в одну
                    if (isManyRows && (columnMinSelIndex != columnMaxSelIndex)) {
                        final int    columnTopLeadSelIndex = columnSelectionModelTop   .getLeadSelectionIndex();
                        final int columnBottomLeadSelIndex = columnSelectionModelBottom.getLeadSelectionIndex();
                        columnSelectionModelTop   .setSelectionInterval(columnTopLeadSelIndex, columnBottomLeadSelIndex);
                        columnSelectionModelBottom.setSelectionInterval(columnTopLeadSelIndex, columnBottomLeadSelIndex);
                    // Выделение в пределах одной строки
                    } else if (   ( columnMinSelIndex == columnMaxSelIndex)
                               || ((columnMinSelIndex >= columnMinIndex) && (columnMaxSelIndex <= columnMaxIndex))) {
                        columnSelectionModelTop   .setSelectionInterval(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                        columnSelectionModelBottom.setSelectionInterval(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                    }
                // При выделении снизу вверх
                } else if (rowLeadSelIndex == rowMinSelIndex) {
                    columnSelectionModelTop   .setSelectionInterval(columnMaxIndex, currentColumnSelectionIndex1);
                // При выделении сверху вниз
                } else if (rowLeadSelIndex == rowMaxSelIndex) {
                    columnSelectionModelBottom.setSelectionInterval(columnMinIndex, currentColumnSelectionIndex1);
                }

                currentColumnSelectionIndex0 = -1;
                currentColumnSelectionIndex1 = -1;
            }
        }

        private class ColumnSelectionModel implements ListSelectionModel {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                // Обрабатываем предыдущие значения
                if ((currentColumnSelectionIndex0 != -1) || (currentColumnSelectionIndex1 != -1)) {
                    final int  rowMinSelIndex = rowSelectionModel.getMinSelectionIndex();
                    final int  rowMaxSelIndex = rowSelectionModel.getMaxSelectionIndex();
                    final int rowLeadSelIndex = rowSelectionModel.getLeadSelectionIndex();

                    final int columnMinSelIndex = Math.min(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                    final int columnMaxSelIndex = Math.max(currentColumnSelectionIndex0, currentColumnSelectionIndex1);

                    if (rowMinSelIndex == rowMaxSelIndex) {
                        if (   ( columnMinSelIndex == columnMaxSelIndex)
                            || ((columnMinSelIndex >= columnMinIndex) && (columnMaxSelIndex <= columnMaxIndex))) {
                            columnSelectionModelTop   .setSelectionInterval(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                            columnSelectionModelBottom.setSelectionInterval(currentColumnSelectionIndex0, currentColumnSelectionIndex1);
                        }
                        // При выделении снизу вверх
                    } else if (rowLeadSelIndex == rowMinSelIndex) {
                        columnSelectionModelTop   .setSelectionInterval(columnMaxIndex, currentColumnSelectionIndex1);
                        // При выделении сверху вниз
                    } else if (rowLeadSelIndex == rowMaxSelIndex) {
                        columnSelectionModelBottom.setSelectionInterval(columnMinIndex, currentColumnSelectionIndex1);
                    }

                    if ((currentColumnSelectionIndex0 == index0) && (currentColumnSelectionIndex1 == index1)) {
                        index0 = -1;
                        index1 = -1;
                    }
                }
                // Так приходится делать из-за того, что Swing при работе с таблицей,
                // сначала задает интервал для колонок, а потом только для строк
                currentColumnSelectionIndex0 = index0;
                currentColumnSelectionIndex1 = index1;
            }

            @Override
            public void setAnchorSelectionIndex(final int index) {
                if (!currentColumnSelectionModel.equals(columnSelectionModelMiddle)) {
                     currentColumnSelectionModel.setAnchorSelectionIndex(index);
                }
            }

            @Override
            public void setLeadSelectionIndex(final int index) {
                if (!currentColumnSelectionModel.equals(columnSelectionModelMiddle)) {
                     currentColumnSelectionModel.setLeadSelectionIndex(index);
                }
            }

            @Override
            public void addSelectionInterval(final int index0, final int index1) {
                // Тут так можно, для правильной работы gotoTableCell()
                currentColumnSelectionIndex0 = index0;
                currentColumnSelectionIndex1 = index1;
                setSelectionInterval( index0,  index1);
            }

            @Override
            public void removeSelectionInterval(final int index0, final int index1) {
                if (!currentColumnSelectionModel.equals(columnSelectionModelMiddle)) {
                     currentColumnSelectionModel.removeSelectionInterval(index0, index1);
                }
            }

            @Override
            public void insertIndexInterval(final int index, final int length, final boolean before) {
                if (!currentColumnSelectionModel.equals(columnSelectionModelMiddle)) {
                     currentColumnSelectionModel.insertIndexInterval(index, length, before);
                }
            }

            @Override
            public void removeIndexInterval(final int index0, final int index1) {
                if (!currentColumnSelectionModel.equals(columnSelectionModelMiddle)) {
                     currentColumnSelectionModel.removeIndexInterval(index0, index1);
                }
            }

            @Override
            public int getMinSelectionIndex() {
                return currentColumnSelectionModel.getMinSelectionIndex();
            }

            @Override
            public int getMaxSelectionIndex() {
                return currentColumnSelectionModel.getMaxSelectionIndex();
            }

            @Override
            public boolean isSelectedIndex(int index) {
                return currentColumnSelectionModel.isSelectedIndex(index);
            }

            @Override
            public int getAnchorSelectionIndex() {
                // Важно только для выбранной строки
                return (rowSelectionModel.getLeadSelectionIndex() <= rowSelectionModel.getAnchorSelectionIndex()) ?
                        columnSelectionModelTop   .getAnchorSelectionIndex() :
                        columnSelectionModelBottom.getAnchorSelectionIndex() ;
            }

            @Override
            public int getLeadSelectionIndex() {
                // Важно только для выбранной строки
                return (rowSelectionModel.getLeadSelectionIndex() <= rowSelectionModel.getAnchorSelectionIndex()) ?
                        columnSelectionModelTop   .getLeadSelectionIndex() :
                        columnSelectionModelBottom.getLeadSelectionIndex() ;
            }

            @Override
            public void clearSelection() {
                columnSelectionModelTop   .clearSelection();
                columnSelectionModelBottom.clearSelection();
            }

            @Override
            public boolean isSelectionEmpty() {
                return currentColumnSelectionModel.isSelectionEmpty();
            }

            @Override
            public void setValueIsAdjusting(final boolean valueIsAdjusting) {
                columnSelectionModelTop   .setValueIsAdjusting(valueIsAdjusting);
                columnSelectionModelMiddle.setValueIsAdjusting(valueIsAdjusting);
                columnSelectionModelBottom.setValueIsAdjusting(valueIsAdjusting);
            }

            @Override
            public boolean getValueIsAdjusting() {
                return currentColumnSelectionModel.getValueIsAdjusting();
            }

            @Override
            public void setSelectionMode(int selectionMode) {
                // нельзя менять режим
            }

            @Override
            public int getSelectionMode() {
                return ListSelectionModel.SINGLE_INTERVAL_SELECTION;
            }

            @Override
            public void addListSelectionListener(final ListSelectionListener x) {
                columnSelectionModelTop   .addListSelectionListener(x);
                columnSelectionModelBottom.addListSelectionListener(x);
            }

            @Override
            public void removeListSelectionListener(final ListSelectionListener x) {
                columnSelectionModelTop   .removeListSelectionListener(x);
                columnSelectionModelBottom.removeListSelectionListener(x);
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для данных оперативной памяти".
     */
    private class MemDatTableModel extends AbstractTableModel {
        private static final long serialVersionUID = -4521079628054252705L;

        /**
         * Конструктор.
         */
        MemDatTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return MD_NUM_ROWS;
        }

        @Override
        public int getColumnCount() {
            return MD_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            final Object obj;
            if (   (columnIndex >= 0)
                && (columnIndex <= getColumnCount())
                && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(final int column) {
            switch (column) {
                case MD_COL_ADR:
                    return STR_ADDRESS;
                case MD_COL_B00:
                case MD_COL_B01:
                case MD_COL_B02:
                case MD_COL_B03:
                case MD_COL_B04:
                case MD_COL_B05:
                case MD_COL_B06:
                case MD_COL_B07:
                case MD_COL_B08:
                case MD_COL_B09:
                case MD_COL_B10:
                case MD_COL_B11:
                case MD_COL_B12:
                case MD_COL_B13:
                case MD_COL_B14:
                case MD_COL_B15:
                    return String.format("%H", column - MD_COL_B00);
                case MD_COL_STR:
                    return "0123456789ABCDEF";
                default:
                    return super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final int page = fEmulatorLayer.getDataPage();
            switch (columnIndex) {
                case MD_COL_ADR:
                    return String.format("%04X:", rowIndex << 4);
                case MD_COL_B00:
                case MD_COL_B01:
                case MD_COL_B02:
                case MD_COL_B03:
                case MD_COL_B04:
                case MD_COL_B05:
                case MD_COL_B06:
                case MD_COL_B07:
                case MD_COL_B08:
                case MD_COL_B09:
                case MD_COL_B10:
                case MD_COL_B11:
                case MD_COL_B12:
                case MD_COL_B13:
                case MD_COL_B14:
                case MD_COL_B15:
                    return String.format("%02X", fEmulatorLayer.debugReadByte(page, (rowIndex << 4) + columnIndex - MD_COL_B00));
                case MD_COL_STR: {
                    final char[] str   = new char[16];
                    for (int i  = 0, j = rowIndex << 4; i < 16; i++, j++) {
                         str[i] = KOI8.charAt(fEmulatorLayer.debugReadByte(page, j));
                    }
                    return new String(str);
                }
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            switch (columnIndex) {
                case MD_COL_ADR:
                    return false;
                case MD_COL_B00:
                case MD_COL_B01:
                case MD_COL_B02:
                case MD_COL_B03:
                case MD_COL_B04:
                case MD_COL_B05:
                case MD_COL_B06:
                case MD_COL_B07:
                case MD_COL_B08:
                case MD_COL_B09:
                case MD_COL_B10:
                case MD_COL_B11:
                case MD_COL_B12:
                case MD_COL_B13:
                case MD_COL_B14:
                case MD_COL_B15:
                case MD_COL_STR:
                    return isEditable(fEmulatorLayer.getDataPage(), () -> rowIndex << 4);
                default:
                    return super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            final int page = fEmulatorLayer.getDataPage();
            switch (columnIndex) {
                //case MD_COL_ADR:
                //    break;~
                case MD_COL_B00:
                case MD_COL_B01:
                case MD_COL_B02:
                case MD_COL_B03:
                case MD_COL_B04:
                case MD_COL_B05:
                case MD_COL_B06:
                case MD_COL_B07:
                case MD_COL_B08:
                case MD_COL_B09:
                case MD_COL_B10:
                case MD_COL_B11:
                case MD_COL_B12:
                case MD_COL_B13:
                case MD_COL_B14:
                case MD_COL_B15:
                    try {
                        fEmulatorLayer.writeByte(page, (rowIndex << 4) + columnIndex - MD_COL_B00, Integer.parseInt((String) aValue, 16));
                    } catch (NumberFormatException e) {
                        showMessageDialog(DebuggerCPU.this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
                    }
                    return;
                case MD_COL_STR: {
                    final String str = (String) aValue;
                    final    int len = Math.min(str.length(), 16);

                    try {
                        fEmulatorLayer.disableEvents();
                        for (int index = 0, address = rowIndex << 4; index < len; index++, address++) {
                             int value;
                            if (   ((value  =   str.charAt(index)) != SKIP_CHAR)
                                && ((value  = KOI8.indexOf(value)) != -1)
                                && ( value != fEmulatorLayer.readByte(page, address))) {
                                fEmulatorLayer.writeByte(page, address, value);
                            }
                        }
                    } finally {
                        if (fEmulatorLayer.isEventsDisabled()) {
                            fEmulatorLayer.enableEvents();
                            fEmulatorLayer.sendEvent(EventType.MEMORY, page);
                        }
                    }
                    return;
                }
                default:
                    break;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с данными из оперативной памяти".
     */
    private class MemDatTable extends JTable implements Observer {
        private static final long serialVersionUID = 9010843821123994652L;

        private final transient SelectionModelsContainer selectionModelsContainer;
        // Выделения диапазонов адресов по страницам памяти: из пары элементов (2 * страница, 2 * страница + 1)
        // содержит адрес начала и конца выделения, оба 0 = выделения нет (курсор на адрес 0x0000)
        private final int[] fPageSelections = new int[2 * fEmulatorLayer.getNumPages()];

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными памяти".
         */
        private class MemDatStringRenderer extends DefaultTableCellRenderer {
            private static final long serialVersionUID = 6167947637905743660L;

            private int fPaintRow     ;
            private int fPaintColumn  ;
            private int fFocusedRow   ;
            private int fFocusedColumn;

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Класс "Рисовальщик ячеек (JLabel) с данными памяти".
             * Умеет подсвечивать символ, соответствующий выбранному коду.
             */
            private class MultiColorLabelUI extends BasicLabelUI {
                @Override
                protected void paintEnabledText(final JLabel l, final Graphics g, final String s, int textX, final int textY) {
                    if (!s.isEmpty()) {
                        final Color foreground = l.getForeground();
                        final int   index      = l.getDisplayedMnemonicIndex();

                        if (   (fPaintRow      == fFocusedRow)
                            && (fPaintColumn   == MD_COL_STR )
                            && (fFocusedColumn >= MD_COL_B00 )
                            && (fFocusedColumn <= MD_COL_B15 )) {

                            String  str;
                            boolean phase = false;
                            for (int beginIndex =        0, endIndex = fFocusedColumn - MD_COL_B00, len = s.length();
                                     beginIndex <      len;
                                     beginIndex = endIndex, endIndex = phase ? endIndex + 1 : len) {

                                if (endIndex > 0) {
                                    g.setColor(phase ? Color.red : foreground);
                                    str = s.substring(beginIndex, endIndex);
                                    drawStringUnderlineCharAt(g, str, index, textX, textY);
                                    textX += g.getFontMetrics().stringWidth(str);
                                }
                                phase = !phase;
                            }
                        } else {
                            g.setColor(foreground);
                            drawStringUnderlineCharAt(g, s, index, textX, textY);
                        }
                    }
                }
            }
            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Конструктор.
             */
            MemDatStringRenderer() {
                super();
                setUI(new MultiColorLabelUI());
            }

            @Override
            public Component getTableCellRendererComponent(final JTable  table,
                                                           final Object  value,
                                                           final boolean isSelected,
                                                           final boolean hasFocus  ,
                                                           final int     row       ,
                                                           final int     column) {

                super.setHorizontalAlignment(CENTER);
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (table == null) {
                    return this;
                }

                fPaintRow    = table.convertRowIndexToModel   (row   );
                fPaintColumn = table.convertColumnIndexToModel(column);

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    setBackground(table.getSelectionBackground());
                } else {
                    setForeground(table.getForeground());
                    setBackground(table.getBackground());
                }

                if (hasFocus) {
                    if ((fFocusedRow  != fPaintRow) || (fFocusedColumn != fPaintColumn)) {
                        fFocusedRow    = fPaintRow   ;
                        fFocusedColumn = fPaintColumn;
                        EventQueue.invokeLater(table::repaint);
                    }
                    setBorder(FOCUS_BORDER);
                } else {
                    setBorder(NO_FOCUS_BORDER);
                }

                setValue(value);
                return this;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Конструктор.
         * @param dm модель данных для таблицы.
         */
        MemDatTable(TableModel dm) {
            super(dm);
            // Создаваем экземпляр SelectionModelsContainer
            this.selectionModelsContainer = new SelectionModelsContainer(MD_COL_B00, MD_COL_B15);
            // Настаиваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(MD_COL_ADR).setMaxWidth(44);
            for (int i = MD_COL_B00; i <= MD_COL_B15; i++) {
                getColumnModel().getColumn(i).setMaxWidth(22);
            }
            getColumnModel().getColumn(MD_COL_STR).setMaxWidth(130);
            // Устанавливаем режим выделения для строк
            setSelectionModel(this.selectionModelsContainer.getRowSelectionModel());
            // Устанавливаем режим выделения для столбцов (с установкой разрешения выделения)
            final TableColumnModel columnModel = getColumnModel();
            columnModel.setColumnSelectionAllowed(true);
            columnModel.setSelectionModel(this.selectionModelsContainer.getColumnSelectionModel());
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new MemDatStringRenderer());
            // Диапазоны адресов на страницах памяти еще не выделялись - заполняем все элементы 0
            Arrays.fill(fPageSelections, 0);
            // Подключаемся к fEmulatorLayer для прослушивания
            fEmulatorLayer.addObserver(this);
        }

        @Override
        public void update(final Observable o, final Object arg) {
            final InnerEvent event = (InnerEvent) arg;
            if (   fEmulatorLayer.eventCheck(event, EventType.MEMORY, fEmulatorLayer.getDataPage())
                || fEmulatorLayer.eventCheck(event, EventType.PAGE  , MemoryPageType.DATA)
                || fEmulatorLayer.eventCheck(event, EventType.STEP  , null)) {
                repaint();
            }
        }

        /**
         * Возвращает текущий адрес в таблице с данными памяти.
         * @return текущий адрес
         */
        int getAddress() {
            int result = 0;
            int rowM   =    getFocusedRowModel(this);
            int colM   = getFocusedColumnModel(this);
            if ((rowM != -1) && (colM != -1)) {
                result = (rowM << 4) + (((colM >= MD_COL_B00) && (colM <= MD_COL_B15)) ? (colM - MD_COL_B00) : 0);
            }
            return result;
        }

        /**
         * Выполняет переход к заданному адресу в таблице с данными памяти.
         * @param address адрес
         */
        void gotoAddress(int address) {
            address &= 0xFFFF;
            gotoTableCell(this, convertRowIndexToView(address >> 4), convertColumnIndexToView(MD_COL_B00 + (address & 0xF)), true);
        }

        /**
         * Получает начальный адрес выделения.
         *
         * @return начальный адрес выделения
         */
        int getStartSelectedAddress() {
            return selectionModelsContainer.getStartSelectedAddress();
        }

        /**
         * Получает конечный адрес выделения.
         *
         * @return конечный адрес выделения.
         */
        int getEndSelectedAddress() {
            return selectionModelsContainer.getEndSelectedAddress();
        }

        /**
         * Выделяет диапазон адресов.
         *
         * @param startAddress адрес начала
         * @param endAddress   адрес конца
         */
        void selectAddressRange(final int startAddress, final int endAddress) {
            // Переходим к начальному адресу
            gotoAddress(startAddress);
            // Выделяем заданный диапазон адресов
            selectionModelsContainer.selectAddressRange(startAddress, endAddress);
        }

        /**
         * Сохраняет текущий выделенный диапазон адресов за указанной страницей памяти.
         *
         * @param page номер страницы памяти
         */
        void savePageSelection(final int page) {
            if ((page >= 0) && (page < fPageSelections.length / 2)) {
                final int startAddress = getStartSelectedAddress();
                final int   endAddress =   getEndSelectedAddress();
                if ((startAddress != -1) && (endAddress != -1)) {
                    fPageSelections[2 * page    ] = startAddress;
                    fPageSelections[2 * page + 1] =   endAddress;
                }
            }
        }

        /**
         * Сохраняет текущий выделенный диапазон адресов за текущей страницей памяти.
         */
        void syncSelectionToDataPage() {
            savePageSelection(fEmulatorLayer.getDataPage());
        }

        /**
         * Восстанавливает сохранённый диапазон адресов для указанной страницы памяти.
         *
         * @param page номер страницы памяти
         */
        void restorePageSelection(final int page) {
            int startAddress = 0;
            int   endAddress = 0;
            if ((page >= 0) && (page < fPageSelections.length / 2)) {
                startAddress = fPageSelections[2 * page    ];
                  endAddress = fPageSelections[2 * page + 1];
            }
            selectAddressRange(startAddress, endAddress);
        }

        /**
         * Сохраняет текущий выделенный диапазон адресов за страницей и показывает другую
         * страницу памяти, восстанавливая её собственный выделенный диапазон адресов.
         *
         * @param page номер страницы памяти
         */
        void updateDataPage(final int page) {
            // Сохраняем выделенный диапазон адресов за покидаемой страницей (до её смены в EmulatorLayer)
            syncSelectionToDataPage   (    );
            fEmulatorLayer.setDataPage(page);
            // Восстанавливаем выделенный диапазон адресов новой страницы
            restorePageSelection(fEmulatorLayer.getDataPage());
        }

        /**
         * Возвращает массив с выделенными диапазонами адресов по страницам памяти.
         * Пара элементов (2 * страница, 2 * страница + 1) содержит адрес начала
         * и конца выделения, оба 0 = выделения нет.
         *
         * @return копия массива с выделенными диапазонами адресов по страницам памяти
         */
        int[] getPageSelections() {
            return fPageSelections.clone();
        }

        /**
         * Устанавливает выделенные диапазоны адресов по страницам памяти.
         *
         * @param selections массив с выделенными диапазонами адресов (пары "начало/конец" по номерам страниц)
         */
        void setPageSelections(final int[] selections) {
            for (int i = 0; (selections != null) && (i < selections.length) && (i < fPageSelections.length); i++) {
                fPageSelections[i] = selections[i];
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

    /**
     * Возвращает выделенную строку в таблице.
     * @param table таблица
     * @return строка (в формате модели)
     */
    private int getFocusedRowModel(final JTable table) {
        return table.convertRowIndexToModel(table.getSelectionModel().getLeadSelectionIndex());
    }

    /**
     * Возвращает выделенную колонку в таблице.
     * @param table таблица
     * @return колонка (в формате модели)
     */
    private int getFocusedColumnModel(final JTable table) {
        return table.convertColumnIndexToModel(table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
    }

    /**
     * Осуществляет переход к заданной ячейке таблицы (координаты ячейки задаются параметрами row, column).
     * @param table таблица
     * @param row строка (в формате просмотра)
     * @param column колонка (в формате просмотра)
     * @param setFocus true = установить фокус на таблице
     */
    private void gotoTableCell(final JTable table, final int row, final int column, final boolean setFocus) {
        // Выделяем необходимую строку и колонку в таблице
        table.clearSelection();
        table.addRowSelectionInterval   (row   , row   );
        table.addColumnSelectionInterval(column, column);
        // Устанавливаем позицию в JScrollPane
        Container c = table.getParent().getParent();
        if (c instanceof JScrollPane) {
            if (!c.isValid()) {
                c.validate();
            }
            final Rectangle celRect = table.getCellRect(row, column, true);
            final Rectangle visRect = new Rectangle(celRect.x    , ((JScrollPane) c).getVerticalScrollBar().getValue        (),
                                                    celRect.width, ((JScrollPane) c).getVerticalScrollBar().getVisibleAmount());
            if (!visRect.contains(celRect)) {
                int val = celRect.y - (Math.round((float) visRect.height / (celRect.height << 1)) - 1) * celRect.height;
                ((JScrollPane) c).getVerticalScrollBar().setValue(Math.max(val, 0));
            }
        }
        // Устанавливаем фокус на таблице
        if (setFocus) {
            table.requestFocusInWindow();
        }
    }

    /**
     * Формирует диалог ввода адресов начала/конца выделяемого участка памяти,
     * затем выполняет само выделение заданного диапазона адресов.
     */
    private void selectAddressRange() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Класс "Панель для диалога ввода адреса".
        class InputAddressPanel extends JPanel {
            private static final long serialVersionUID = -1875626299292457015L;

            private final JFormattedTextFieldExt fStartAddress;
            private final JFormattedTextFieldExt   fEndAddress;

            // Конструктор
            private InputAddressPanel() {
                super(new GridLayout(2, 2));

                final JLabel codeLabel = new JLabel("Адрес начала:");
                final JLabel dataLabel = new JLabel("Адрес  конца:");
                codeLabel.setFont(HEADER_FONT);
                dataLabel.setFont(HEADER_FONT);

                final int startSelAddress = fMemDatTable.getStartSelectedAddress();
                final int   endSelAddress = fMemDatTable.  getEndSelectedAddress();

                fStartAddress = new JFormattedTextFieldExt(WORD_MASK, '0');
                fStartAddress.setValue(String.format("%04X", (startSelAddress == -1) ? 0 : startSelAddress));
                  fEndAddress = new JFormattedTextFieldExt(WORD_MASK, '0');
                  fEndAddress.setValue(String.format("%04X", (  endSelAddress == -1) ? 0 :   endSelAddress));

                add(codeLabel    );
                add(fStartAddress);
                add(dataLabel    );
                add(fEndAddress  );

                fStartAddress.addAncestorListener(new AncestorListener() {
                    // Используем для перевода фокуса на компонент ввода адреса
                    @Override
                    public void ancestorAdded(AncestorEvent event) {
                        if (!fStartAddress.isFocusOwner()) {
                             fStartAddress.requestFocusInWindow();
                        }
                    }

                    @Override
                    public void ancestorRemoved(AncestorEvent event) {
                        //
                    }

                    @Override
                    public void ancestorMoved(AncestorEvent event) {
                        //
                    }
                });

                // Используем для перевода фокуса на следующий компонент
                final ActionListener al = e -> ((JComponent)e.getSource()).transferFocus();
                fStartAddress.addActionListener(al);
                  fEndAddress.addActionListener(al);
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        final InputAddressPanel inputAddressPanel = new InputAddressPanel();
        int result =  showConfirmDialog(this, inputAddressPanel, "Select block ...", OK_CANCEL_OPTION, QUESTION_MESSAGE);
        if (result == OK_OPTION) {
            try {
                fMemDatTable.selectAddressRange(
                        Integer.parseInt((String) inputAddressPanel.fStartAddress.getValue(), 16),
                        Integer.parseInt((String) inputAddressPanel.  fEndAddress.getValue(), 16));
            } catch (NumberFormatException e) {
                showMessageDialog(this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
            }
        }
    }

    /**
     * Сохраняет выделенный в таблице просмотра данных диапазон адресов в файл.
     * Данные берутся из страницы, выбранной в списке страниц данных, а не из активной страницы машины.
     */
    private void saveSelectedBlock() {
        // Сохраняем выделенный диапазон адресов за текущей страницей, чтобы границы всегда относились к ней
        fMemDatTable.syncSelectionToDataPage();
        int startAddress = fMemDatTable.getStartSelectedAddress();
        int   endAddress = fMemDatTable.  getEndSelectedAddress();
        // Пустое выделение: ни одна строка с байтами не выделена; одна ячейка - тоже не диапазон
        if ((startAddress == -1) || (endAddress == -1) || (startAddress == endAddress)) {
            showMessageDialog(this,
                    "Не выделен диапазон адресов в таблице просмотра данных.\n" +
                             "Выделите его мышью или кнопкой \"Select\".",
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }
        if (startAddress > endAddress) {
            final int tmp = startAddress;
            startAddress  =   endAddress;
              endAddress  =          tmp;
        }

        final int page  = fEmulatorLayer.getDataPage() ;
        final int total = endAddress - startAddress + 1;
        final byte[] data = new byte[total];
        for (int i = 0; i < total; i++) {
            data[i] = (byte) fEmulatorLayer.debugReadByte(page, startAddress + i);
        }

        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle     (String.format("Сохранить в файл данные в диапазоне %04X-%04X из страницы \"%s\"",
                startAddress, endAddress, fEmulatorLayer.getPageName(page, startAddress)));
        chooser.setCurrentDirectory(new File(Constants.getCurPath()));
        chooser.setFileFilter      (new FileNameExtensionFilter("Файлы: *.bin", "bin"));
        if (chooser.showSaveDialog (this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        // Расширение по умолчанию дописывается, только если пользователь его не задал
        if (file.getName().lastIndexOf('.') == -1) {
            file = new File(file.getPath().concat(".bin"));
        }
        Constants.setCurPath(file.getParent());

        try {
            Files.write(file.toPath(), data);
        } catch (IOException ex) {
            showMessageDialog(this,
                    String.format("Ошибка записи файла: \"%s\"%n%s", file.getPath(), ex),
                    Constants.STR_ERROR, ERROR_MESSAGE);
        }
    }

    /**
     * Загружает выбранный файл в страницу просмотра данных, начиная с адреса под курсором в таблице данных.
     * Данные пишутся в страницу, выбранную в списке страниц данных, а не в активную страницу машины.
     */
    private void loadBlockFromFile() {
        final int page    = fEmulatorLayer.getDataPage();
        final int address =   fMemDatTable.getAddress ();

        // ROM-диск не перезаписывается: отказываем до показа файлового диалога
        if ((page >= (fEmulatorLayer.getNumPages() - 1)) && (address < MainMemory.ROM_DISK_SIZE)) {
            showMessageDialog(this,
                    String.format("Загрузка в страницу \"%s\" не поддерживается: ROM-диск не перезаписывается.",
                            fEmulatorLayer.getPageName(page)),
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }

        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(String.format(
                "Загрузка файла с адреса %04X в страницу \"%s\"", address, fEmulatorLayer.getPageName(page, address)));
        chooser.setCurrentDirectory(new File(Constants.getCurPath()));
        if (chooser.showOpenDialog (this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        final File file = chooser.getSelectedFile() ;
              Constants.setCurPath(file.getParent());

        // Размер берётся у файловой системы до чтения: большой файл отбрасывается без выделения памяти
        final long size;
        try {
            if (!Files.isRegularFile(file.toPath())) {
                showMessageDialog(this,
                        String.format("Выбран не файл: \"%s\".", file.getPath()), Constants.STR_ERROR, ERROR_MESSAGE);
                return;
            }
            size = Files.size(file.toPath());
        } catch (IOException | SecurityException ex) {
            showMessageDialog(this,
                    String.format("Ошибка чтения файла: \"%s\"%n%s", file.getPath(), ex),
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }

        final long endAddress = address + size - 1L;
        if ((size > 0L) && (endAddress >= MainMemory.STORAGE_SIZE)) {
            showMessageDialog(this,
                    String.format("Файл \"%s\" (%d байт) не помещается в страницу: "      +
                                  "загрузка с адреса %04X заканчивается на адресе %08X, " +
                                  "а последний допустимый адрес %04X.",
                            file.getName(), size, address, endAddress, MainMemory.STORAGE_SIZE - 1),
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }

        final byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException | OutOfMemoryError ex) {
            showMessageDialog(this,
                    String.format("Ошибка чтения файла: \"%s\"%n%s", file.getPath(), ex),
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }

        // Файл мог измениться между запросом размера и чтением
        if (data.length != size) {
            showMessageDialog(this,
                    String.format("Файл \"%s\" изменился во время чтения: ожидалось %d байт, прочитано %d.",
                            file.getName(), size, data.length),
                    Constants.STR_ERROR, ERROR_MESSAGE);
            return;
        }

        fEmulatorLayer.writeBytes(page, address, data, 0, data.length);
        // Обновляем таблицу данных, чтобы загруженные байты появились на экране
          fMemDatTable.repaint();
    }

    /**
     * Формирует диалог ввода адресов кода/данных и выполняет переход к этим адресам в таблицах просмотра кода/данных.
     */
    private void gotoAddress() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Класс "Панель для диалога ввода адреса".
        class InputAddressPanel extends JPanel {
            private static final long serialVersionUID = 3879988331395756986L;

            private final JFormattedTextFieldExt fCodeAddress;
            private final JFormattedTextFieldExt fDataAddress;

            // Конструктор
            private InputAddressPanel() {
                super(new GridLayout(2, 2));

                final JLabel codeLabel = new JLabel("Адрес (code):");
                final JLabel dataLabel = new JLabel("Адрес (data):");
                codeLabel.setFont(HEADER_FONT);
                dataLabel.setFont(HEADER_FONT);

                fCodeAddress = new JFormattedTextFieldExt(WORD_MASK, '0');
                fCodeAddress.setValue(String.format("%04X", getFocusedRowModel(fDisAsmTable)));
                fDataAddress = new JFormattedTextFieldExt(WORD_MASK, '0');
                fDataAddress.setValue(String.format("%04X", fMemDatTable.getAddress()));

                add(codeLabel   );
                add(fCodeAddress);
                add(dataLabel   );
                add(fDataAddress);

                fCodeAddress.addAncestorListener(new AncestorListener() {
                    // Используем для перевода фокуса на компонент ввода адреса fCodeAddress
                    @Override
                    public void ancestorAdded(AncestorEvent event) {
                        if (!fCodeAddress.isFocusOwner()) {
                             fCodeAddress.requestFocusInWindow();
                        }
                    }

                    @Override
                    public void ancestorRemoved(AncestorEvent event) {
                        //
                    }

                    @Override
                    public void ancestorMoved(AncestorEvent event) {
                        //
                    }
                });

                // Используем для перевода фокуса на следующий компонент
                final ActionListener al = e -> ((JComponent)e.getSource()).transferFocus();
                fCodeAddress.addActionListener(al);
                fDataAddress.addActionListener(al);
            }

            /**
             * Получает значение введенного адреса
             * @param type true = значение адреса в данных / false = значение адреса в коде
             * @return значение
             */
            private int getAddress(boolean type) {
                int result = 0;
                try {
                    result = Integer.parseInt((String) (type ? fDataAddress.getValue() : fCodeAddress.getValue()), 16);
                } catch (NumberFormatException e) {
                    showMessageDialog(DebuggerCPU.this, e.toString(), Constants.STR_ERROR, ERROR_MESSAGE);
                }
                return result;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        final InputAddressPanel inputAddressPanel = new InputAddressPanel();
        final int result =  showConfirmDialog(this, inputAddressPanel, "Go to ...", OK_CANCEL_OPTION, QUESTION_MESSAGE);
        if (result == OK_OPTION) {
            int codeAddress = inputAddressPanel.getAddress(false);
            int dataAddress = inputAddressPanel.getAddress(true );

            if (dataAddress != fMemDatTable.getAddress()) {
                fMemDatTable.gotoAddress(dataAddress);
            }
            if (codeAddress != getFocusedRowModel(fDisAsmTable)) {
                fDisAsmTable.gotoAddress(codeAddress, DA_COL_ADR);
            }
        }
    }

    /**
     * Формирует диалог поиска данных и осуществляет поиск.
     */
    private void findData() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Класс "Панель для диалога ввода данных".
        class InputDataPanel extends JPanel {
            private static final long serialVersionUID = 1767873090074086408L;

            private static final String REGEXP_STRING_BYTES = "^([\\dA-F]{1,2}( +|$))+";

            private final JTextField fBytes;
            private final JTextField fChars;

            // Функция - преобразует строку из байтов в строку из символов.
            private final transient UnaryOperator<String> bytesToChars = strBytes -> {
                    strBytes = strBytes.trim();
                if (strBytes.matches(REGEXP_STRING_BYTES)) {
                    String[] b = strBytes.split(" +"); // разбиваем строку из байтов на отдельные байты
                      char[] s = new char[b.length];

                    for (int i = b.length - 1; i >= 0; i--) {
                        try {
                            s[i] = KOI8.charAt(Integer.parseInt(b[i], 16));
                        } catch (NumberFormatException e) {
                            s[i] = SKIP_CHAR;
                        }
                    }
                    return new String(s);
                }
                return "";
            };

            // Конструктор
            private InputDataPanel() {
                super(new GridBagLayout());

                final JLabel bytesLabel = new JLabel("Байты :");
                final JLabel charsLabel = new JLabel("Строка:");
                fBytes = new JTextField(new PlainDocument() {
                    private static final long serialVersionUID = 3384345776522390223L;

                    @Override
                    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                        str = str.toUpperCase();
                        // Пропускаем только шестнадцатеричные цифры и пробел
                        if (str.matches("[\\dA-F ]+")) {
                            super.insertString(offs, str, a);
                        }
                    }
                }, PrevStaticData.getPrevStringBytes(), 0);
                fChars = new JTextField();

                bytesLabel.setFont( HEADER_FONT);
                charsLabel.setFont( HEADER_FONT);
                    fBytes.setFont(DEFAULT_FONT);
                    fBytes.setToolTipText("Строка из байт в шестнадцатеричном формате: \"HH HH HH...\"");
                    fChars.setFont(DEFAULT_FONT);
                    fChars.setText(bytesToChars.apply(fBytes.getText()));

                add(bytesLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST,
                        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 5), 0, 0));
                add(    fBytes, new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.WEST,
                        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
                add(charsLabel, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.WEST,
                        GridBagConstraints.HORIZONTAL, new Insets(5, 0, 0, 5), 0, 0));
                add(    fChars, new GridBagConstraints(1, 1, 1, 1, 1, 0, GridBagConstraints.WEST,
                        GridBagConstraints.HORIZONTAL, new Insets(5, 0, 0, 0), 0, 0));

                // Используем для перевода фокуса на компонент ввода байт fBytes
                fBytes.addAncestorListener(new AncestorListener() {
                    @Override
                    public void ancestorAdded(AncestorEvent event) {
                        if (!fBytes.isFocusOwner()) {
                             fBytes.requestFocusInWindow();
                        }
                    }

                    @Override
                    public void ancestorRemoved(AncestorEvent event) {
                        //
                    }

                    @Override
                    public void ancestorMoved(AncestorEvent event) {
                        //
                    }
                });

                // Используем для синхронного преобразования вводимых байт в текст
                fBytes.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate (final DocumentEvent e) {
                        if (fBytes.isFocusOwner()) {
                            final String strBytes = fBytes.getText().trim();
                            fBytes.setBackground((strBytes.isEmpty() || strBytes.matches(REGEXP_STRING_BYTES)) ? fChars.getBackground() : Color.pink);
                            fChars.setText(bytesToChars.apply(fBytes.getText()));
                        }
                    }

                    @Override
                    public void removeUpdate (final DocumentEvent e) {
                        insertUpdate(e);
                    }

                    @Override
                    public void changedUpdate(final DocumentEvent e) {
                        //
                    }
                });

                // Используем для синхронного преобразования вводимых символов в байты
                fChars.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate (final DocumentEvent e) {
                        if (fChars.isFocusOwner()) {
                                  String strBytes = "";
                            final String strChars = fChars.getText();
                            for (int i = 0, len = strChars.length(); i < len; i++) {
                                 final int  val = KOI8.indexOf(strChars.charAt(i));
                                strBytes = strBytes.concat(String.format("%02X ", (val == -1) ? 0 : val));
                            }
                            fBytes.setText(strBytes.trim());
                        }
                    }

                    @Override
                    public void removeUpdate (final DocumentEvent e) {
                        insertUpdate(e);
                    }

                    @Override
                    public void changedUpdate(final DocumentEvent e) {
                        //
                    }
                });

                // Используем для перевода фокуса на следующий компонент
                final ActionListener al = e -> ((JComponent)e.getSource()).transferFocus();
                fBytes.addActionListener(al);
                fChars.addActionListener(al);

                // Используем для проверки корректности строки из байтов
                fBytes.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        final String strBytes = fBytes.getText().trim();
                        if (!(strBytes.isEmpty() || strBytes.matches(REGEXP_STRING_BYTES))) {
                            showMessageDialog(DebuggerCPU.this,
                                    String.format("Некорректно заполнена строка из байт:%n[%s]%nПоиск невозможен!", strBytes),
                                    Constants.STR_ERROR, ERROR_MESSAGE);
                        }
                    }
                });
            }

            /**
             * Выполняет поиск данных из fBytes в странице памяти Data MEM.
             *
             * @param address начальный адрес для поиска
             * @return адрес начала найденных данных
             * или -1 = в fBytes некорректные данные
             * или -2 = данные не найдены
             */
            private int find(int address) {
                final String strBytes = fBytes.getText().trim();
                if (strBytes.matches(REGEXP_STRING_BYTES)) {
                    // Запоминаем поисковую строку
                    PrevStaticData.setPrevStringBytes(strBytes);

                    // Разбиваем строку из байт на отдельные байты
                    final int[] bytes = Arrays.stream(strBytes.split(" +"))
                            .mapToInt(strByte -> {
                                try {
                                    return Integer.parseInt(strByte, 16);
                                } catch (NumberFormatException e) {
                                    //
                                }
                                return 0;
                            })
                            .toArray();

                    final int length = bytes.length;

                    if (address + length >= 0x1_0000) {
                        address = 0;
                    }

                    // Выполняем поиск данных
                    for (; address < 0x1_0000; address++) {
                        int  i;
                        for (i = 0; i < length; i++) {
                            if (fEmulatorLayer.debugReadByte(fEmulatorLayer.getDataPage(), address + i) != bytes[i]) {
                                break;
                            }
                        }
                        if (i == length) {
                            return address;
                        }
                    }
                    return -2; // данные не найдены
                }
                return -1; // в fBytes некорректные данные
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        final InputDataPanel inputDataPanel = new InputDataPanel();
        final int result = showConfirmDialog(DebuggerCPU.this, inputDataPanel,
                String.format("Поиск в странице: [%s]", fEmulatorLayer.getPageName(fEmulatorLayer.getDataPage())),
                OK_CANCEL_OPTION, QUESTION_MESSAGE);

        if (result == OK_OPTION) {
            final int start   = (fMemDatTable.getAddress() + 1) & 0xFFFF;
                  int address = inputDataPanel.find(start);
            if ((     address == -2) && (start > 0)) {
                      address = inputDataPanel.find(0);
            }
                   if (address >=  0) {
                fMemDatTable.gotoAddress(address);
            } else if (address == -2) {
                showMessageDialog(this,
                        String.format("Заданные для поиска данные:%n[%s]%nНе найдены!", PrevStaticData.getPrevStringBytes()),
                        "Информация", INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Выполняет один шаг отладчика.
     */
    private void step() {
        // Показываем код из страницы, в которой работает CPU
        fEmulatorLayer.setCodePage(fEmulatorLayer.getCpuPage());
        // Сохраняем регистровые пары
        fEmulatorLayer.saveAllRegPairs();
        if (fEmulatorLayer.execOneCmdCPU()) {
            // Уведомляем слушателей об изменениях
            fEmulatorLayer.afterStep();
            // Устанавливаем курсор на позицию = PC
            fDisAsmTable  .gotoAddress(fEmulatorLayer.getValRegPair(DebugRegPair.PC), DA_COL_ADR);
        }
    }

    /**
     * Выполняет один шаг отладчика с выполнением подпрограмм.
     */
    private void stepOver() {
        // Показываем код из страницы, в которой работает CPU
        fEmulatorLayer.setCodePage(fEmulatorLayer.getCpuPage());
        // Получаем адрес следующей команды после команды вызова подпрограммы (CALL/RST)
        final int address = ((DisAsmTableModel) fDisAsmTable.getModel()).getAddressAfterCallCmd(fEmulatorLayer.getValRegPair(DebugRegPair.PC));
        if (      address < 0) {
            step();
        } else {
            // Сохраняем регистровые пары
            fEmulatorLayer.saveAllRegPairs();
            // Добавляем StepOver ловушку
            fEmulatorLayer.addTrap(fEmulatorLayer.getCodePage(), address, true);
            // Закрываем отладчик
            setVisible(false);
        }
    }

    /**
     * Выполняет запуск с места, на которое указывает курсор.
     */
    private void runToCursor() {
        fEmulatorLayer.setCpuPage(fEmulatorLayer.getCodePage());
        fEmulatorLayer.setValRegPair(DebugRegPair.PC, getFocusedRowModel(fDisAsmTable));
    }

    /**
     * Проверяет возможность редактирования данных (область ROM-диска не редактируется).
     *
     * @param page            страница
     * @param addressSupplier получатель адреса
     * @return true - можно редактировать
     */
    private boolean isEditable(final int page, final @NotNull IntSupplier addressSupplier) {
        return (page < fEmulatorLayer.getNumPages() - 1) || (addressSupplier.getAsInt() >= MainMemory.ROM_DISK_SIZE);
    }
}