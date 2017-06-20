package avsh.specialist_mx;

import avsh.lib.cExtFormattedTextField;
import avsh.specialist_mx.cI8080.DebugRegPairs;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicLabelUI;
import javax.swing.table.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.Observable;
import java.util.Observer;
import java.util.function.Function;

import static avsh.specialist_mx.cConsStat.NUMBER_PAGES_RAMDISK;
import static javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt;

/**
 * Класс "Отладчик для процессора i8080 (К580ВМ80А)".
 * @author -=AVSh=-
 */
final class cDebuggerI8080 extends JDialog {
    private static final String INI_OPTION_FRAME_WIDTH  = "DebugFrameWidth" ;
    private static final String INI_OPTION_FRAME_HEIGHT = "DebugFrameHeight";

    // Массив мнемоник процессора i8080 (К580ВМ80А)
    private static final String[] MNEMONICS = new String[] {
            "NOP"      , "LXI  B,"  , "STAX B"   , "INX  B"   , "INR  B"   , "DCR  B"   , "MVI  B,"  , "RLC"      , "?nop"     , "DAD  B"   , "LDAX B"   , "DCX  B"   , "INR  C"   , "DCR  C"   , "MVI  C,"  , "RRC"      ,
            "?nop"     , "LXI  D,"  , "STAX D"   , "INX  D"   , "INR  D"   , "DCR  D"   , "MVI  D,"  , "RAL"      , "?nop"     , "DAD  D"   , "LDAX D"   , "DCX  D"   , "INR  E"   , "DCR  E"   , "MVI  E,"  , "RAR"      ,
            "?nop"     , "LXI  H,"  , "SHLD "    , "INX  H"   , "INR  H"   , "DCR  H"   , "MVI  H,"  , "DAA"      , "?nop"     , "DAD  H"   , "LHLD "    , "DCX  H"   , "INR  L"   , "DCR  L"   , "MVI  L,"  , "CMA"      ,
            "?nop"     , "LXI  SP," , "STA  "    , "INX  SP"  , "INR  M"   , "DCR  M"   , "MVI  M,"  , "STC"      , "?nop"     , "DAD  SP"  , "LDA  "    , "DCX  SP"  , "INR  A"   , "DCR  A"   , "MVI  A,"  , "CMC"      ,
            "MOV  B, B", "MOV  B, C", "MOV  B, D", "MOV  B, E", "MOV  B, H", "MOV  B, L", "MOV  B, M", "MOV  B, A", "MOV  C, B", "MOV  C, C", "MOV  C, D", "MOV  C, E", "MOV  C, H", "MOV  C, L", "MOV  C, M", "MOV  C, A",
            "MOV  D, B", "MOV  D, C", "MOV  D, D", "MOV  D, E", "MOV  D, H", "MOV  D, L", "MOV  D, M", "MOV  D, A", "MOV  E, B", "MOV  E, C", "MOV  E, D", "MOV  E, E", "MOV  E, H", "MOV  E, L", "MOV  E, M", "MOV  E, A",
            "MOV  H, B", "MOV  H, C", "MOV  H, D", "MOV  H, E", "MOV  H, H", "MOV  H, L", "MOV  H, M", "MOV  H, A", "MOV  L, B", "MOV  L, C", "MOV  L, D", "MOV  L, E", "MOV  L, H", "MOV  L, L", "MOV  L, M", "MOV  L, A",
            "MOV  M, B", "MOV  M, C", "MOV  M, D", "MOV  M, E", "MOV  M, H", "MOV  M, L", "HLT"      , "MOV  M, A", "MOV  A, B", "MOV  A, C", "MOV  A, D", "MOV  A, E", "MOV  A, H", "MOV  A, L", "MOV  A, M", "MOV  A, A",
            "ADD  B"   , "ADD  C"   , "ADD  D"   , "ADD  E"   , "ADD  H"   , "ADD  L"   , "ADD  M"   , "ADD  A"   , "ADC  B"   , "ADC  C"   , "ADC  D"   , "ADC  E"   , "ADC  H"   , "ADC  L"   , "ADC  M"   , "ADC  A"   ,
            "SUB  B"   , "SUB  C"   , "SUB  D"   , "SUB  E"   , "SUB  H"   , "SUB  L"   , "SUB  M"   , "SUB  A"   , "SBB  B"   , "SBB  C"   , "SBB  D"   , "SBB  E"   , "SBB  H"   , "SBB  L"   , "SBB  M"   , "SBB  A"   ,
            "ANA  B"   , "ANA  C"   , "ANA  D"   , "ANA  E"   , "ANA  H"   , "ANA  L"   , "ANA  M"   , "ANA  A"   , "XRA  B"   , "XRA  C"   , "XRA  D"   , "XRA  E"   , "XRA  H"   , "XRA  L"   , "XRA  M"   , "XRA  A"   ,
            "ORA  B"   , "ORA  C"   , "ORA  D"   , "ORA  E"   , "ORA  H"   , "ORA  L"   , "ORA  M"   , "ORA  A"   , "CMP  B"   , "CMP  C"   , "CMP  D"   , "CMP  E"   , "CMP  H"   , "CMP  L"   , "CMP  M"   , "CMP  A"   ,
            "RNZ"      , "POP  B"   , "JNZ  "    , "JMP  "    , "CNZ  "    , "PUSH B"   , "ADI  "    , "RST  0"   , "RZ"       , "RET"      , "JZ   "    , "?jmp "    , "CZ   "    , "CALL "    , "ACI  "    , "RST  1"   ,
            "RNC"      , "POP  D"   , "JNC  "    , "OUT  "    , "CNC  "    , "PUSH D"   , "SUI  "    , "RST  2"   , "RC"       , "?ret"     , "JC   "    , "IN   "    , "CC   "    , "?call "   , "SBI  "    , "RST  3"   ,
            "RPO"      , "POP  H"   , "JPO  "    , "XTHL"     , "CPO  "    , "PUSH H"   , "ANI  "    , "RST  4"   , "RPE"      , "PCHL"     , "JPE  "    , "XCHG"     , "CPE  "    , "?call "   , "XRI  "    , "RST  5"   ,
            "RP"       , "POP  PSW" , "JP   "    , "DI"       , "CP   "    , "PUSH PSW" , "ORI  "    , "RST  6"   , "RM"       , "SPHL"     , "JM   "    , "EI"       , "CM   "    , "?call "   , "CPI  "    , "RST  7"
    };

    // Массив длин команд процессора i8080 (К580ВМ80А)
    private static final int[] CMD_LEN = {
            1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,
            1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,
            1, 3, 3, 1, 1, 1, 2, 1, 1, 1, 3, 1, 1, 1, 2, 1,
            1, 3, 3, 1, 1, 1, 2, 1, 1, 1, 3, 1, 1, 1, 2, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 3, 3, 3, 1, 2, 1, 1, 1, 3, 1, 3, 3, 2, 1,
            1, 1, 3, 2, 3, 1, 2, 1, 1, 1, 3, 2, 3, 1, 2, 1,
            1, 1, 3, 1, 3, 1, 2, 1, 1, 1, 3, 1, 3, 1, 2, 1,
            1, 1, 3, 1, 3, 1, 2, 1, 1, 1, 3, 1, 3, 1, 2, 1
    };

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

    // Имена флагов SZ0A0P1C
    private static final String FLAGS = "SZ?A?P?C"; // ? - заполняются значениями битов регистра флагов

    // Коды команд передачи управления (JMP/CALL)
    private static final int[] JMP_CMD  = {0xC2, 0xD2, 0xE2, 0xF2, 0xC3, 0xCA, 0xDA, 0xEA, 0xFA, 0xC4, 0xD4, 0xE4, 0xF4, 0xCC, 0xDC, 0xEC, 0xFC, 0xCD};
    // Коды команд вызова подпрограммы (CALL/RST)
    private static final int[] CALL_CMD = {0xC4, 0xD4, 0xE4, 0xF4, 0xCC, 0xDC, 0xEC, 0xFC, 0xCD, 0xC7, 0xD7, 0xE7, 0xF7, 0xCF, 0xDF, 0xEF, 0xFF};

    // Типы страниц памяти в отладчике:
    // CPU  - страница, в которой работает CPU;
    // CODE - страница, в которой просматривается код;
    // DATA - страница, в которой просматриваются данные.
    private enum TypesMemoryPages {
        CPU, CODE, DATA
    }

    // Типы событий:
    // REG_PAIR - изменилась регистровая пара;
    // TRAPS    - изменились ловушки;
    // MEMORY   - изменилась память;
    // PAGE     - изменилась страница памяти;
    // STEP     - выполнен шаг CPU (изменилось всё).
    private enum TypesEvents {
        REG_PAIR, TRAPS, MEMORY, PAGE, STEP;
        // Для сохранения деталей события
        private Object fDetail;
        // Возвращает детали события
        public Object getDetail(){
            return this.fDetail;
        }
        // Сохраняет детали события
        public void setDetail(Object detail){
            this.fDetail = detail;
        }
    }

    // Константы дизассемблера
    private static final int FOR_ALIGNMENT = 16; // Длина участка для выравнивания кода
    private static final int BUF_SIZE      = 64; // Размер буфера
    private static final int THREE_QUARTER = BUF_SIZE - (BUF_SIZE >> 2);

    // Количество строк и колонок таблицы с дизассемблированными данными (просмотр кода)
    private static final int DA_NUM_ROWS    = 0x1_0000;
    private static final int DA_NUM_COLUMNS = 6;
    // Колонки таблицы с дизассемблированными данными (просмотр кода)
    private static final int DA_COL_TRP = 0;
    private static final int DA_COL_ADR = 1;
    private static final int DA_COL_BT0 = 2;
    private static final int DA_COL_BT1 = 3;
    private static final int DA_COL_BT2 = 4;
    private static final int DA_COL_CMD = 5;

    // Количество строк и колонок таблицы с регистровыми парами CPU
    private static final int CR_NUM_ROWS    = DebugRegPairs.values().length;
    private static final int CR_NUM_COLUMNS = 3;
    // Колонки таблицы с регистровыми парами CPU
    private static final int CR_COL_REG = 0;
    private static final int CR_COL_EQU = 1;
    private static final int CR_COL_DAT = 2;

    // Количество строк и колонок таблицы с данными стека
    private static final int SP_NUM_ROWS    = 9;
    private static final int SP_NUM_COLUMNS = 1;
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
    private static final String BYTE_MASK             = "HH";
    private static final String WORD_MASK             = "HHHH";
    private static final String STR16_MASK            = "****************";
    private static final String EDITING_OR_NAVIGATING = "EditingOrNavigating";

    // Статические структуры для сохранения положения окна отладчика и значений регистровых пар
    private static final Point fPrevLocation = new Point();
    private static final int[] fPrevRegPairs = new int[DebugRegPairs.values().length];
    // Статические переменные для сохранения станицы и адреса памяти таблицы данных памяти
    private static int fPrevDataPage   ;
    private static int fPrevDataAddress;
    // Статическая переменная для сохранения строки поиска (строка из байт)
    private static String fPrevStringBytes = "";

    private final transient cSpMX  fSpMX ;
    private final transient сLayer fLayer;
    private final cDisAsmTable fDisAsmTable;
    private final cMemDatTable fMemDatTable;

    private int fFocusedAddress;

    /**
     * Конструктор.
     * @param spMX ссылка на главный класс эмулятора.
     */
    cDebuggerI8080(@NotNull cSpMX spMX) {
        super(spMX.getMainFrame(), true);

        // Запоминаем ссылку на главный класс эмулятора
        fSpMX = spMX;
        // Инициализируем слой для взаимодействия с CPU и памятью
        fLayer = new сLayer(spMX.getCPU());
        // Инициализируем модели и таблицы для просмотра кода и данных
        fDisAsmTable = new cDisAsmTable(new cDisAsmTableModel());
        fMemDatTable = new cMemDatTable(new cMemDatTableModel());

        fFocusedAddress = -1;
        // Инициализируем остальные компоненты и обработчики событий
        initComponents();
    }

    private void initComponents() {
        setTitle("Отладчик для процессора i8080 (К580ВМ80А)");

        JPanel dialogPane   = new JPanel();
        JPanel contentPanel = new JPanel();
        JPanel buttonBar    = new JPanel();

        JScrollPane disAsmScrollPane = new JScrollPane();
        JScrollPane regCpuScrollPane = new JScrollPane();
        JScrollPane memDatScrollPane = new JScrollPane();
        JScrollPane  stackScrollPane = new JScrollPane();
        JScrollPane  trapsScrollPane = new JScrollPane();

        // Байтовые поля для редактора ячеек таблицы fDisAsmTable
        final cExtFormattedTextField byteFieldDAT = new cExtFormattedTextField(BYTE_MASK, '0');
        final DefaultCellEditor byteCellEditorDAT = new      DefaultCellEditor(byteFieldDAT);

        // Двухбайтовые поля для редактора ячеек таблицы regCpuTable
        final cExtFormattedTextField wordFieldRCT = new cExtFormattedTextField(WORD_MASK, '0');
        final DefaultCellEditor wordCellEditorRCT = new      DefaultCellEditor(wordFieldRCT);

        // Двухбайтовые поля для редактора ячеек таблицы stackTable
        final cExtFormattedTextField wordFieldST  = new cExtFormattedTextField(WORD_MASK, '0');
        final DefaultCellEditor wordCellEditorST  = new      DefaultCellEditor(wordFieldST);

        // Байтовые поля для редактора ячеек таблицы таблицы fMemDatTable
        final cExtFormattedTextField byteFieldMDT = new cExtFormattedTextField(BYTE_MASK, '0');
        final DefaultCellEditor byteCellEditorMDT = new      DefaultCellEditor(byteFieldMDT);

        // Строковое поле из 16 символов для редактора ячеек таблицы таблицы fMemDatTable
        final cExtFormattedTextField strFieldMDT  = new cExtFormattedTextField(STR16_MASK, ' ');
        final DefaultCellEditor strCellEditorMDT  = new      DefaultCellEditor(strFieldMDT);

        // Параметры таблицы fDisAsmTable с дизассемблированными данными
        fDisAsmTable.getColumnModel().getColumn(DA_COL_BT0).setCellEditor(byteCellEditorDAT);
        fDisAsmTable.getColumnModel().getColumn(DA_COL_BT1).setCellEditor(byteCellEditorDAT);
        fDisAsmTable.getColumnModel().getColumn(DA_COL_BT2).setCellEditor(byteCellEditorDAT);

        // Параметры таблицы fMemDatTable с данными памяти
        for (int i = MD_COL_B00; i <= MD_COL_B15; i++) {
            fMemDatTable.getColumnModel().getColumn(i).setCellEditor(byteCellEditorMDT);
        }
        fMemDatTable.getColumnModel().getColumn(MD_COL_STR).setCellEditor(strCellEditorMDT);

        // Метка для вывода информации из регистра флагов CPU
        cFlagsRegLabel flagsRegLabel = new cFlagsRegLabel();

        // Кнопки установки/сброса флагов CPU (FLAGS = SZ?A?P?C)
        JButton flagSButton = new JButton(new String(new char[]{FLAGS.charAt(0)}));
        JButton flagZButton = new JButton(new String(new char[]{FLAGS.charAt(1)}));
        JButton flagAButton = new JButton(new String(new char[]{FLAGS.charAt(3)}));
        JButton flagPButton = new JButton(new String(new char[]{FLAGS.charAt(5)}));
        JButton flagCButton = new JButton(new String(new char[]{FLAGS.charAt(7)}));
        {
            final Dimension dim = new Dimension(17, 17);
            final Insets    ins = new Insets   ( 1, 1, 1, 1);
            flagSButton.setMinimumSize(dim);
            flagSButton.setMargin     (ins);
            flagSButton.setMnemonic   (FLAGS.charAt(0));
            flagZButton.setMinimumSize(dim);
            flagZButton.setMargin     (ins);
            flagZButton.setMnemonic   (FLAGS.charAt(1));
            flagAButton.setMinimumSize(dim);
            flagAButton.setMargin     (ins);
            flagAButton.setMnemonic   (FLAGS.charAt(3));
            flagPButton.setMinimumSize(dim);
            flagPButton.setMargin     (ins);
            flagPButton.setMnemonic   (FLAGS.charAt(5));
            flagCButton.setMinimumSize(dim);
            flagCButton.setMargin     (ins);
            flagCButton.setMnemonic   (FLAGS.charAt(7));
        }

        // Модель и таблица для вывода информации из регистровых пар CPU
        cRegCpuTable regCpuTable = new cRegCpuTable(new cRegCpuTableModel());
         regCpuTable.getColumnModel().getColumn(CR_COL_DAT).setCellEditor(wordCellEditorRCT);

        // Модель и таблица для вывода информации из стека CPU
        cStackTable stackTable = new cStackTable(new cStackTableModel());
         stackTable.getColumnModel().getColumn(SP_COL_DAT).setCellEditor(wordCellEditorST);

        // Модель и таблица для вывода информации о ловушках
        cTrapsTable trapsTable = new cTrapsTable(new cTrapsTableModel());

        // Кнопки для работы с ловушками
        JButton deleteTrapButton = new JButton("Delete");
        JButton clearTrapsButton = new JButton("Clear" );
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

        // Метка для вывода информации о текущей странице CPU
        cCpuMemPageLabel cpuMemPageLabel = new cCpuMemPageLabel();

        // Переключатель страниц памяти для просмотра кода
        cCodeMemPagesComboBox codeMemPagesComboBox = new cCodeMemPagesComboBox();

        // Переключатель страниц памяти для просмотра данных
        cDataMemPagesComboBox dataMemPagesComboBox = new cDataMemPagesComboBox();

        JButton findButton = new JButton("Find");
        JButton    button2 = new JButton("---" );
        JButton    button3 = new JButton("---" );
        JButton    button4 = new JButton("---" );

        // Основные кнопки управления отладчиком
        Action performStep = new AbstractAction("F6 Step") {
            @Override
            public void actionPerformed(ActionEvent e) {
                step();
            }
        };

        Action performStepOver = new AbstractAction("F7 Step Over") {
            @Override
            public void actionPerformed(ActionEvent e) {
                stepOver();
            }
        };

        JButton     gotoButton = new JButton("Go to");
        JButton     toPCButton = new JButton("To PC");
        JButton      runButton = new JButton("Run to cursor");
        JButton     stepButton = new JButton(performStep    );
        JButton stepOverButton = new JButton(performStepOver);
        JButton       okButton = new JButton("OK");

            stepButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "performStep"    );
            stepButton.getActionMap().put("performStep"    , performStep    );
        stepOverButton.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "performStepOver");
        stepOverButton.getActionMap().put("performStepOver", performStepOver);

            findButton.setMnemonic('F');
            findButton.setToolTipText("Осуществляет поиск данных");
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
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        // -=- dialogPane -=-
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            // -=- contentPanel -=-
            {
                contentPanel.setPreferredSize(new Dimension(540, 412));
                contentPanel.setLayout(new GridBagLayout());
                ((GridBagLayout) contentPanel.getLayout()).columnWidths  = new    int[] {249,  18,  18,  18,  18,  18,  43,  79,  79, 0};
                ((GridBagLayout) contentPanel.getLayout()).rowHeights    = new    int[] { 22,  22, 104,  20,  22,  50,  22, 150,  0};
                ((GridBagLayout) contentPanel.getLayout()).columnWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0 ,0.0, 0.0, 0.0, 0.0, 1.0E-4};
                ((GridBagLayout) contentPanel.getLayout()).rowWeights    = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0E-4};

                // -=- disAsmScrollPane -=-
                disAsmScrollPane.setViewportView(fDisAsmTable);
                contentPanel.add(disAsmScrollPane,
                                 new GridBagConstraints(0, 0, 1, 7, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 5), 0, 0));

                // -=- memDatScrollPane -=-
                memDatScrollPane.setViewportView(fMemDatTable);
                contentPanel.add(memDatScrollPane,
                                 new GridBagConstraints(0, 7, 9, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 0, 0), 0, 0));

                // -=- flagsRegLabel -=-
                contentPanel.add(flagsRegLabel,
                                 new GridBagConstraints(1, 0, 5, 1, 0.0, 0.0,
                                                        GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, 3, 5), 0, 0));

                // -=- flagSButton -=-
                contentPanel.add(flagSButton,
                                 new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 8, 1), 0, 0));

                // -=- regCpuScrollPane -=-
                regCpuScrollPane.setViewportView(regCpuTable);
                contentPanel.add(regCpuScrollPane,
                                 new GridBagConstraints(1, 2, 5, 2, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 5), 0, 0));

                // -=- cpuMemPageLabel -=-
                contentPanel.add(cpuMemPageLabel,
                                 new GridBagConstraints(1, 4, 8, 1, 0.0, 0.0,
                                                        GridBagConstraints.BASELINE, GridBagConstraints.HORIZONTAL,
                                                        new Insets(0, 0, 5, 0), 0, 0));

                // -=- codeMemPagesComboBox -=-
                contentPanel.add(codeMemPagesComboBox,
                                 new GridBagConstraints(1, 6, 6, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 5), 0, 0));

                // -=- flagZButton -=-
                contentPanel.add(flagZButton,
                                 new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 8, 1), 0, 0));

                // -=- flagAButton -=-
                contentPanel.add(flagAButton,
                                 new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 8, 1), 0, 0));

                // -=- flagPButton -=-
                contentPanel.add(flagPButton,
                                 new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 8, 1), 0, 0));

                // -=- flagCButton -=-
                contentPanel.add(flagCButton,
                                 new GridBagConstraints(5, 1, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 8, 5), 0, 0));

                // -=- stackScrollPane -=-
                stackScrollPane.setViewportView(stackTable);
                contentPanel.add(stackScrollPane,
                                 new GridBagConstraints(6, 0, 1, 4, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 5), 0, 0));

                // -=- trapsScrollPane -=-
                trapsScrollPane.setViewportView(trapsTable);
                contentPanel.add(trapsScrollPane,
                                 new GridBagConstraints(7, 0, 2, 3, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 1, 0), 0, 0));

                // -=- deleteTrapButton -=-
                contentPanel.add(deleteTrapButton,
                                 new GridBagConstraints(7, 3, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 6, 1), 0, 0));

                // -=- dataMemPagesComboBox -=-
                contentPanel.add(dataMemPagesComboBox,
                                 new GridBagConstraints(7, 6, 2, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 5, 0), 0, 0));

                // -=- clearTrapsButton -=-
                contentPanel.add(clearTrapsButton,
                                 new GridBagConstraints(8, 3, 1, 1, 0.0, 0.0,
                                                        GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 1, 6, 0), 0, 0));
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

                //---- button2 ----
                buttonBar.add(button2, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- button3 ----
                buttonBar.add(button3, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 5, 5), 0, 0));

                //---- button4 ----
                buttonBar.add(button4, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0,
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
            // Срабатывает в начале и конце редактирования - корректируем позицию курсора, устанавливаем значение и фокус компонена JFormattedTextField
            final HierarchyListener hl = e -> {
                if ((e.getChangedParent() instanceof JTable) && (e.getComponent() instanceof JFormattedTextField)) {
                    final JTable              tbl = (JTable)              e.getChangedParent();
                    final JFormattedTextField ftf = (JFormattedTextField) e.getComponent    ();

                    if (!tbl.isEditing() && !ftf.isFocusOwner() && (ftf.getCaretPosition() > 0)) {
                        ftf.setValue(tbl.getModel().getValueAt(getFocusedRowModel(tbl), getFocusedColumnModel(tbl)));
                        ftf.requestFocusInWindow();
                        ftf.setCaretPosition(0);
                    }
                }
            };
            // Добавляем в компоненты
            byteFieldDAT.addHierarchyListener(hl);
            wordFieldRCT.addHierarchyListener(hl);
             wordFieldST.addHierarchyListener(hl);
            byteFieldMDT.addHierarchyListener(hl);
             strFieldMDT.addHierarchyListener(hl);
        }

        {
            // При потере фокуса на редактируемомом байте/слове - отменяем редактирование
            final FocusAdapter fa = new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    if (((Component) e.getSource()).getParent() instanceof JTable) {
                        final JTable tbl = (JTable) ((Component) e.getSource()).getParent();
                        if (tbl.isEditing()) {
                            tbl.getCellEditor().cancelCellEditing();
                        }
                    }
                }
            };
            // Добавляем в компоненты
            byteFieldDAT.addFocusListener(fa);
            wordFieldRCT.addFocusListener(fa);
             wordFieldST.addFocusListener(fa);
            byteFieldMDT.addFocusListener(fa);
             strFieldMDT.addFocusListener(fa);
        }

        // При потере фокуса на таблице fDisAsmTable - убираем пометку адресов
        fDisAsmTable.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fFocusedAddress  =  -1;
                fDisAsmTable.repaint();
            }
        });

        // Изменяем реакцию на клавишу Enter в таблице fDisAsmTable
        fDisAsmTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
        fDisAsmTable.getActionMap().put(EDITING_OR_NAVIGATING, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final TableModel model   =  fDisAsmTable.getModel();
                int rowM =    getFocusedRowModel(fDisAsmTable);
                int colM = getFocusedColumnModel(fDisAsmTable);
                switch (colM) {
                    case DA_COL_BT0:
                    case DA_COL_BT1:
                    case DA_COL_BT2: // Редактирование ячейки
                        if (model.isCellEditable(rowM, colM)) {
                            fDisAsmTable.editCellAt(fDisAsmTable.convertRowIndexToView(rowM), fDisAsmTable.convertColumnIndexToView(colM));
                        }
                        break;
                    case DA_COL_CMD: // Переход по адресу в команде
                        if ((fFocusedAddress >= 0) && ((cDisAsmTableModel) model).isJmpCmd(rowM)) {
                            fDisAsmTable.gotoAddress(fFocusedAddress, DA_COL_ADR);
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        {
            final AbstractAction aa = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final JTable table = (JTable) e.getSource();
                    int rowM =    getFocusedRowModel(table);
                    int colM = getFocusedColumnModel(table);
                    // Редактирование ячейки
                    if ((   ((table instanceof cRegCpuTable) && (colM == CR_COL_DAT))
                         || ((table instanceof cStackTable ) && (colM == SP_COL_DAT))
                         || ((table instanceof cMemDatTable) && (colM >= MD_COL_B00))) && table.getModel().isCellEditable(rowM, colM)) {
                        table.editCellAt(table.convertRowIndexToView(rowM), table.convertColumnIndexToView(colM));
                    }
                }
            };

            // Изменяем реакцию на клавишу Enter в таблице regCpuTable
            regCpuTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            regCpuTable.getActionMap().put(EDITING_OR_NAVIGATING, aa);

            // Изменяем реакцию на клавишу Enter в таблице stackTable
            stackTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            stackTable.getActionMap().put(EDITING_OR_NAVIGATING, aa);

            // Изменяем реакцию на клавишу Enter в таблице fMemDatTable
            fMemDatTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
            fMemDatTable.getActionMap().put(EDITING_OR_NAVIGATING, aa);
        }

        // Изменяем реакцию на клавишу Enter в таблице trapsTable
        trapsTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), EDITING_OR_NAVIGATING);
        trapsTable.getActionMap().put(EDITING_OR_NAVIGATING, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int  rowM  = getFocusedRowModel((JTable) e.getSource());
                if ((rowM != -1) && (rowM < fLayer.getTrapCount())) {
                    cTrap trap = fLayer.getTrap(rowM);
                    // Переходим на ловушку в таблице с кодом
                          fLayer.setCodePage(trap.getPage());
                    fDisAsmTable.gotoAddress(trap.getAddress(), DA_COL_ADR);
                }
            }
        });

        // Изменяем реакцию на двойной клик мышью в колонке DA_COL_CMD таблицы fDisAsmTable
        fDisAsmTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ((   e.getClickCount() == 2)
                     && (getFocusedColumnModel(fDisAsmTable) == DA_COL_CMD)
                     && (fFocusedAddress  >= 0)
                     && ((cDisAsmTableModel) fDisAsmTable.getModel()).isJmpCmd(getFocusedRowModel(fDisAsmTable))) {
                    fDisAsmTable.gotoAddress(fFocusedAddress, DA_COL_ADR); // Переход по адресу в команде
                }
            }
        });

        // Изменяем реакцию на двойной клик мышью в колонках таблицы с ловушками trapsTable
        trapsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int  rowM  = getFocusedRowModel((JTable) e.getComponent());
                    if ((rowM != -1) && (rowM < fLayer.getTrapCount())) {
                        cTrap trap = fLayer.getTrap(rowM);
                        // Переходим на ловушку в таблице с кодом
                              fLayer.setCodePage(trap.getPage());
                        fDisAsmTable.gotoAddress(trap.getAddress(), DA_COL_ADR);
                    }
                }
            }
        });

        {
            // Обработчик событий для кнопок установки/сброса флагов
            final ActionListener al = e -> {
                int index = FLAGS.indexOf(e.getActionCommand());
                if ((index != -1) && (index <= 7)) {
                    // Инвертируем бит, соответствующий названию кнопки
                    fLayer.setValRegPair(DebugRegPairs.AF, fLayer.getValRegPair(DebugRegPairs.AF) ^ (1 << (7 - index)));
                }
            };
            // Добавляем в компоненты
            flagSButton.addActionListener(al);
            flagZButton.addActionListener(al);
            flagAButton.addActionListener(al);
            flagPButton.addActionListener(al);
            flagCButton.addActionListener(al);
        }

        // Обработчик событий для кнопки удаления ловушки
        deleteTrapButton.addActionListener(e -> {
            int  rowM  = getFocusedRowModel(trapsTable);
            if ((rowM != -1) && (rowM < fLayer.getTrapCount())) {
                cTrap trap = fLayer.getTrap(rowM);
                fLayer.remTrap(trap.getPage(), trap.getAddress());
            }
        });

        // Обработчик событий для кнопки очистки ловушек
        clearTrapsButton.addActionListener(e -> fLayer.clearTraps());

        // Обработчик выбора страницы памяти Code RAM
        codeMemPagesComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                int index = ((JComboBox) e.getSource()).getSelectedIndex();
                fLayer.setCodePage((index == fLayer.getNumPages() - 1) ? cMD_SpMX_RAM.ROM_DISK : index); // Учитываются особенности класса cMD_SpMX_RAM
            }
        });

        // Обработчик выбора страницы памяти Data RAM
        dataMemPagesComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                int index = ((JComboBox) e.getSource()).getSelectedIndex();
                fLayer.setDataPage((index == fLayer.getNumPages() - 1) ? cMD_SpMX_RAM.ROM_DISK : index); // Учитываются особенности класса cMD_SpMX_RAM
            }
        });

        // Определяем обработчики кнопок
        gotoButton.addActionListener(e -> gotoAddress());
        toPCButton.addActionListener(e -> {
                  fLayer.setCodePage(fLayer.getCpuPage());
            fDisAsmTable.gotoAddress(fLayer.getValRegPair(DebugRegPairs.PC), DA_COL_ADR);

        });
         runButton.addActionListener(e -> runToCursor());
          okButton.addActionListener(e -> {
            fLayer.saveAllRegPairs();
            setVisible(false);
        });
        findButton.addActionListener(e -> findData());

        // Обрабатываем события закрытия окна отладчика
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Срабатывает при нажатии по [X] (срабатывает раньше, чем получит управление вызвавшая отладчик программа)
                fLayer.saveAllRegPairs();
            }

            // Срабатывает всегда после закрытия окна отладчика (срабатывает позже, чем получит управление вызвавшая отладчик программа)
            @Override
            public void windowClosed(WindowEvent e) {
                // Запоминаем номер страницы Data RAM
                fPrevDataPage    = fLayer.getDataPage();
                // Запоминаем адрес, выбранный в таблице данных памяти
                fPrevDataAddress = fMemDatTable.getAddress();
                // Запоминаем положение окна отладчика
                fPrevLocation.setLocation(getLocation());
                // Запоминаем размеры фрейма в ini-файл
                fSpMX.putIni(cConsStat.INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , getWidth ());
                fSpMX.putIni(cConsStat.INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, getHeight());
            }
        });

        // -=-=-=-=- Завершение формирования окна отладчика -=-=-=-=-
        setSize(578, 546);
        setMinimumSize(getSize());
        pack();

        // Восстанавливаем размеры фрейма из ini-файла
        {
            Integer width  = fSpMX.getIni(cConsStat.INI_SECTION_CONFIG, INI_OPTION_FRAME_WIDTH , Integer.class);
            Integer height = fSpMX.getIni(cConsStat.INI_SECTION_CONFIG, INI_OPTION_FRAME_HEIGHT, Integer.class);
            if ((width != null) && (height != null)) {
                setSize(width, height);
            }
        }

        // Устанавливаем курсор на адрес = fPrevDataAddress
        fMemDatTable.gotoAddress(fPrevDataAddress);
        // Устанавливаем курсор на адрес = PC в таблице fDisAsmTable
        fDisAsmTable.gotoAddress(fLayer.getValRegPair(DebugRegPairs.PC), DA_COL_ADR);

        if ((fPrevLocation.getX() == 0) && (fPrevLocation.getY() == 0)) {
            // Выводим окно отладчика в центре родительского окна
            setLocationRelativeTo(getOwner());
        } else {
            // Иначе выводим окно на предыдущую позицию
            setLocation(fPrevLocation);
        }

        // Показываем окно отладчика
        setVisible(true);
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Слой для взаимодействия отладчика с CPU и памятью".
     */
    private class сLayer extends Observable {
        private final cI8080 fCPU;

        private boolean fDisableEvents;
        private int     fCodePage;
        private int     fDataPage;

        /**
         * Конструктор.
         */
        сLayer(cI8080 cpu) {
            fCPU = cpu;
            // Заполняем пустой массив предыдущих значений регистровых пар
            if ((getPrevValRegPair(DebugRegPairs.AF) & 0xFF) == 0) {
                saveAllRegPairs();
            }
            // Установим страницу памяти для просмотра кода = странице, в которой работает CPU
            fCodePage = fSpMX.getPage();
            // Восстановим номер страницы для просмотра данных
            fDataPage = fPrevDataPage  ;
        }

        /**
         * Показывает отключены события или нет.
         * @return true = события отключены.
         */
        boolean isEventsDisabled() {
            return fDisableEvents;
        }

        /**
         * Отключает события.
         */
        void disableEvents() {
            fDisableEvents = true;
        }

        /**
         * Включает события.
         */
        void enableEvents() {
            fDisableEvents = false;
        }

        /**
         * Отправляет событие наблюдателям.
         * @param event событие
         * @param detail детали события
         */
        void sendEvent(TypesEvents event, Object detail) {
            if (fDisableEvents) {
                return;
            }
            // Установим детали события
            event.setDetail(detail);
            // Отправляем оповещение наблюдателю
                 setChanged(      );
            notifyObservers(event );
        }

        /**
         * Выполняет проверку события.
         * @param event событие
         * @param check_event проверочное событие
         * @param check_detail проверочные детали события (для null детали не важны)
         * @return true = событие необходимо обработать
         */
        private boolean eventCheck(Object event, @NotNull TypesEvents check_event, Object check_detail) {
            if (check_event.equals(event)) {
                Object  detail = ((TypesEvents) event).getDetail();
                return (detail == null) || (check_detail == null) || check_detail.equals(detail);
            }
            return false;
        }

        /**
         * Выполняет одну команду CPU в режиме "Пауза" тактового генератора.
         * @return true = команда выполнена успешно
         */
        boolean execOneCmdCPU() {
            // Если CPU находится в режиме "HOLD" (в результате обращения в порт FFF0), то отменяем этот режим
            if (fCPU.isHoldAcknowledge()) {
                fCPU.hold(false);
            }
            // Отключаем режим "Пауза" для устройств памяти
            fCPU.pauseMemoryDevices(false);
            // Выполнем одну команду CPU
            boolean result = fSpMX.getGen().execOneCmdCPU();
            // Включаем режим "Пауза" для устройств памяти
            fCPU.pauseMemoryDevices(true );
            return result;
        }

        /**
         * Получает значение регистровой пары CPU.
         * @param regPair регистровая пара
         * @return значение
         */
        int getValRegPair(DebugRegPairs regPair) {
            return fCPU.debugGetValRegPair(regPair);
        }

        /**
         * Устанавливает значение регистровой пары CPU.
         * @param regPair регистровая пара
         * @param value значение
         */
        synchronized void setValRegPair(DebugRegPairs regPair, int value) {
            // Сохраняем предыдущее значение регистровой пары
            fPrevRegPairs[regPair.ordinal()] = fCPU.debugGetValRegPair(regPair);
            // Устанавливаем новое значение
            fCPU.debugSetValRegPair(regPair, value);
            // Отправляем событие наблюдателям
            sendEvent(TypesEvents.REG_PAIR, regPair);
        }

        /**
         * Получает предыдущее значение регистровой пары CPU.
         * @param regPair регистровая пара
         * @return значение
         */
        synchronized int getPrevValRegPair(DebugRegPairs regPair) {
            return fPrevRegPairs[regPair.ordinal()];
        }

        /**
         * Сохраняет значения всех регистровых пар в массиве.
         */
        synchronized void saveAllRegPairs() {
            for (DebugRegPairs regPair : DebugRegPairs.values()) {
                fPrevRegPairs[regPair.ordinal()] = getValRegPair(regPair);
            }
        }

        /**
         * Получает визуальное представление регистра флагов.
         * @return значение регистра флагов.
         */
        String getVisViewsFlagsReg() {
            char   c;
            char[] r = new char[8];
            int    f = getValRegPair(DebugRegPairs.AF);
            for (int i = 7; i >= 0; i--, f >>= 1) {
                c    = FLAGS.charAt(i);
                r[i] = (c == '?') ? Character.forDigit(f & 1, 2) : ((f & 1) == 1 ? c : '.');
            }
            return new String(r);
        }

        /**
         * Показывает как изменилась регистровая пара (сравнивает предыдущее и текущее значение регистровой пары).
         * @param regPair регистровая пара
         * @return -1 = изменений нет, 0 = изменился младший байт, 1 = изменился старший байт, 2 = изменились оба байта
         * (Для SP и PC всегда 2 = оба байта, если были изменения)
         */
        int getChangesRegPair(DebugRegPairs regPair) {
            int xor = getPrevValRegPair(regPair) ^ getValRegPair(regPair);
            if (xor > 0) {
                if        (( xor < 0x100)       && (regPair != DebugRegPairs.SP) && (regPair != DebugRegPairs.PC)) {
                    return 0;
                } else if (((xor % 0x100) == 0) && (regPair != DebugRegPairs.SP) && (regPair != DebugRegPairs.PC)) {
                    return 1;
                } else {
                    return 2;
                }
            }
            return -1;
        }

        /**
         * Добавляет ловушку.
         * @param page номер страницы памяти
         * @param address адрес ловушки
         * @param stepOver true = StepOver ловушка
         */
        void addTrap(int page,  int address, boolean stepOver) {
            fCPU.debugAddTrap(page, address, stepOver);
            // Отправляем событие наблюдателям
            if (!stepOver) {
                sendEvent(TypesEvents.TRAPS, new cTrap(page, address));
            }
        }

        /**
         * Удаляет ловушку.
         * @param page номер страницы памяти
         * @param address адрес ловушки
         */
        void remTrap(int page,  int address) {
            fCPU.debugRemTrap(page, address);
            // Отправляем событие наблюдателям
            sendEvent(TypesEvents.TRAPS, new cTrap(page, address));
        }

        /**
         * Удаляет все ловушки.
         */
        void clearTraps() {
            fCPU.debugClearTraps();
            // Отправляем событие наблюдателям
            sendEvent(TypesEvents.TRAPS, null);
        }

        /**
         * Проверяет, установлена ли по заданному адресу ловушка.
         * @param page номер страницы памяти
         * @param address адрес
         * @return true = ловушка установлена
         */
        boolean isTrap(int page, int address) {
            return fCPU.debugIsTrap(page, address);
        }

        /**
         * Возвращает количество установленных ловушек.
         * @return количество установленных ловушек
         */
        int getTrapCount() {
            return fCPU.debugGetTrapCount();
        }

        /**
         * Возвращает ловушку по индексу в списке ловушек.
         * @param index индекс ловушки
         * @return ловушка
         */
        cTrap getTrap(int index) {
            return fCPU.debugGetTrap(index);
        }

        /**
         * Возвращает индекс ловушки.
         * @param trap ловушка
         * @return индекс
         */
        int getTrapIndex(cTrap trap) {
            for (int index = getTrapCount() - 1; index >= 0; index--) {
                if (trap.equals(getTrap(index))) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * Возвращает номер страницы памяти, в которой просматривается код.
         * @return номер страницы
         */
        synchronized int getCodePage() {
            return fCodePage;
        }

        /**
         * Возвращает номер страницы памяти, в которой просматриваются данные.
         * @return номер страницы
         */
        synchronized int getDataPage() {
            return fDataPage;
        }

        /**
         * Возвращает номер страницы памяти, в которой работает CPU.
         * @return номер страницы
         */
        int getCpuPage() {
            return fSpMX.getPage();
        }

        /**
         * Устанавливает номер страницы памяти для просмотра кода.
         * @param page номер страницы
         */
        synchronized void setCodePage(int page) {
            if (fCodePage != page) {
                fCodePage  = page;
                sendEvent(TypesEvents.PAGE, TypesMemoryPages.CODE);
            }
        }

        /**
         * Устанавливает номер страницы памяти для просмотра данных.
         * @param page номер страницы
         */
        synchronized void setDataPage(int page) {
            if (fDataPage != page) {
                fDataPage  = page;
                sendEvent(TypesEvents.PAGE, TypesMemoryPages.DATA);
            }
        }

        /**
         * Устанавливает номер страницы памяти для CPU.
         * @param page номер страницы
         */
        void setCpuPage(int page) {
            if (fSpMX.getPage() != page) {
                fSpMX.setPage(page);
                sendEvent(TypesEvents.PAGE, TypesMemoryPages.CPU);
            }
        }

        /**
         * Возвращает количество страниц памяти (учитываются все страницы памяти - основная, RAM и ROM диски).
         * @return количество страниц памяти
         */
        int getNumPages() {
            return NUMBER_PAGES_RAMDISK + 2; // Основная память и ROM-диск не учтены в параметре NUMBER_PAGES_RAMDISK
        }

        /**
         * Возвращает имя страницы памяти.
         * @param page номер страницы
         * @return имя страницы
         */
        String getNamePage(int page) {
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
         * @param page страница памяти
         * @param address адрес
         * @return считанный байт
         */
        synchronized int readByte(int page, int address) {
            int curr_page  = fSpMX.getPage();
            if (curr_page !=  page) {
                fSpMX.setPage(page);
            }
            int result = fSpMX.readByte(address);
            if (page  != curr_page) {
                fSpMX.setPage(curr_page);
            }
            return result;
        }

        /**
         * Читает байт из памяти (безопасное чтение).
         * @param page страница памяти
         * @param address адрес
         * @return считанный байт
         */
        synchronized int debugReadByte(int page, int address) {
            int curr_page  = fSpMX.getPage();
            if (curr_page !=  page) {
                fSpMX.setPage(page);
            }
            int result = fSpMX.debugReadByte(address);
            if (page  != curr_page) {
                fSpMX.setPage(curr_page);
            }
            return result;
        }

        /**
         * Записывает байт в память.
         * @param page страница памяти
         * @param address адрес
         * @param value байт
         */
        synchronized void writeByte(int page, int address, int value) {
            int curr_page  = fSpMX.getPage();
            if (curr_page !=  page) {
                fSpMX.setPage(page);
            }
            fSpMX.writeByte(address, value);
            if (page != curr_page) {
                fSpMX.setPage(curr_page);
            }
            sendEvent(TypesEvents.MEMORY, page);
        }

        /**
         * Метод должен вызываться после выполнения каждого шага CPU.
         */
        synchronized void afterStep() {
            // Устанавливаем Code Page = CPU Page
            fCodePage = fSpMX.getPage();
            // Отправляем событие наблюдателям
            sendEvent(TypesEvents.STEP, null);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для дизассемблера".
     */
    private class cDisAsmTableModel extends AbstractTableModel {
        private final int[][] fStartBuffer;
        private final int[][] fMovedBuffer;

        /**
         * Конструктор.
         */
        cDisAsmTableModel() {
            super();
            fStartBuffer = new int[BUF_SIZE][4]; // [x][0] - под адрес, [x][1..3] - под данные команды CPU
            fMovedBuffer = new int[BUF_SIZE][4]; // [x][0] - под адрес, [x][1..3] - под данные команды CPU
        }

        /**
         * Заполняет буфер адресами и данными команд CPU.
         * @param buf буфер
         * @param address адрес начала (адрес точного начала кода)
         */
        private void fillBuffer(int[][] buf, int address) {
            int page =  fLayer.getCodePage();
            int pc   = (fLayer.getCpuPage () == page) ? fLayer.getValRegPair(DebugRegPairs.PC) : 0;

            for (int i = 0, length; i < BUF_SIZE; i++) {
                                 buf[i][0] = address;
                length = CMD_LEN[buf[i][1] = fLayer.debugReadByte(page, address)];
                if ((pc <= address) || (pc >= address + length)) {
                    buf[i][2] = (--length > 0) ? fLayer.debugReadByte(page, ++address) : -1;
                    buf[i][3] = (--length > 0) ? fLayer.debugReadByte(page, ++address) : -1;
                } else { // Устраняем коллизии
                    length = pc - address;
                    buf[i][2] = (--length > 0) ? fLayer.debugReadByte(page, ++address) : -2;
                    buf[i][3] = (--length > 0) ? fLayer.debugReadByte(page, ++address) : -2;
                }
                address++;
            }
        }

        /**
         * Возвращает массив с данными команды CPU по заданному адресу.
         * @param address адрес
         * @return массив с данными команды CPU
         */
        private int[] getData(int address) {
            // Заполняем буфер данных из начальных адресов (таблица постоянно читает данные из начальной позиции)
            if (fStartBuffer[BUF_SIZE - 1][0] == 0) {
                fillBuffer(fStartBuffer, 0);
            }

            int[][] curBuf;
            int movedStart = fStartBuffer[BUF_SIZE - 1][0] + CMD_LEN[fStartBuffer[BUF_SIZE - 1][1]]; // Начальный адрес данных после fStartBuffer
            // Определяем из какого буфера брать данные
            if (address < movedStart) {
                curBuf  = fStartBuffer;
            } else {
                curBuf = fMovedBuffer;
                // Если в буфере данных из текущих адресов нет необходимых данных - заполняем буфер
                if ((address < fMovedBuffer[0][0]) || (address > fMovedBuffer[BUF_SIZE - 1][0])) {
                    if (address >= movedStart    + THREE_QUARTER + FOR_ALIGNMENT) {
                        if (address    >  0xFFFF - THREE_QUARTER) {
                            movedStart =  0xFFFF - BUF_SIZE      - FOR_ALIGNMENT;
                        } else {
                            movedStart = address - THREE_QUARTER - FOR_ALIGNMENT;
                        }
                        // Пытаемся выполнить выравнивание кода (в надежде, что повезет :-)
                        //noinspection StatementWithEmptyBody
                        for (int end_adr = movedStart + FOR_ALIGNMENT, page = fLayer.getCodePage(), len; movedStart + (len = CMD_LEN[fLayer.debugReadByte(page, end_adr)]) < end_adr; movedStart += len) {
                        }
                    }
                    fillBuffer(fMovedBuffer, movedStart);
                }
            }
            // Ищем в выбранном буфере данные по заданному адресу
            for (int i = 0; i < BUF_SIZE; i++) {
                if (curBuf[i][0] == address) {
                    return curBuf[i];
                }
            }
            return null;
        }

        /**
         * Возвращает true, если по заданному адресу находится команда передачи управления (JMP/CALL).
         * @param address адрес
         * @return результат
         */
        boolean isJmpCmd(int address) {
            int[] data  = getData(address);
            if (  data != null) {
                int codeCmd = data[1];
                for (int codeJmp :  JMP_CMD) {
                    if ( codeJmp == codeCmd) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Возвращает адрес/16-битные данные из команды CPU или -1, если в команде нет адреса/16-битных данных.
         * @param address адрес команды
         * @return адрес из команды
         */
        int getAddressFromCmd(int address) {
            int[] data  = getData(address);
            if (  data != null) {
                return data[2] | data[3] << 8;
            }
            return -1;
        }

        /**
         * Возвращает адрес следующей команды после команды вызова подпрограммы (CALL/RST).
         * @param address адрес
         * @return результат = адрес следующей команды или -1, если по заданному адресу нет команды вызова подпрограммы
         */
        int getAddressAfterCallCmd(int address) {
            int[] data  = getData(address);
            if (  data != null) {
                int codeCmd = data[1];
                for (int codeCall : CALL_CMD) {
                    if ( codeCall == codeCmd) {
                        return address + CMD_LEN[codeCmd];
                    }
                }
            }
            return -1;
        }

        @Override
        public void fireTableDataChanged() {
            fStartBuffer[BUF_SIZE - 1][0] = 0;
            fMovedBuffer[           0][0] = 0;
            fMovedBuffer[BUF_SIZE - 1][0] = 0;
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
        public Class<?> getColumnClass(int columnIndex) {
            Object obj;
            if ((columnIndex >= 0) && (columnIndex <= getColumnCount()) && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case DA_COL_TRP:
                    return "Л.";
                case DA_COL_ADR:
                    return "Адрес";
                case DA_COL_BT0:
                    return "0";
                case DA_COL_BT1:
                    return "1";
                case DA_COL_BT2:
                    return "2";
                case DA_COL_CMD:
                    return "Команда";
                default:
                    return super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            int[] data  = getData(rowIndex);
            if (  data != null) {
                switch (columnIndex) {
                    case DA_COL_TRP:
                        return fLayer.isTrap(fLayer.getCodePage(), rowIndex);
                    case DA_COL_ADR:
                        return String.format("%04X:", data[0]);
                    case DA_COL_BT0:
                    case DA_COL_BT1:
                    case DA_COL_BT2: {
                        int     bt  =  data[columnIndex - DA_COL_BT0 + 1];
                        return (bt >= 0) ? String.format("%02X", bt) : "";
                    }
                    case DA_COL_CMD:
                        // Вызываем рекурсивно
                        if (data[3] == -2) {
                            // Выводим только байты в случае коллизий
                            return "DB   ".concat((String) getValueAt(rowIndex, DA_COL_BT0)).concat((data[2] >= 0 ? ", ".concat((String) getValueAt(rowIndex, DA_COL_BT1)) : ""));
                        } else {
                            // Выводим мнемоники
                            return MNEMONICS[data[1]].concat((String) getValueAt(rowIndex, DA_COL_BT2)).concat((String) getValueAt(rowIndex, DA_COL_BT1));
                        }
                    default:
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
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case DA_COL_TRP:
                    return true;
                case DA_COL_ADR:
                    return false;
                case DA_COL_BT0:
                case DA_COL_BT1:
                case DA_COL_BT2:
                    return !getValueAt(rowIndex, columnIndex).equals("");
                case DA_COL_CMD:
                    return false;
                default:
                    return super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            int page = fLayer.getCodePage();
            switch (columnIndex) {
                case DA_COL_TRP:
                    if ((Boolean) aValue) {
                        fLayer.addTrap(page, rowIndex, false);
                    } else {
                        fLayer.remTrap(page, rowIndex);
                    }
                    return;
                case DA_COL_ADR:
                    break;
                case DA_COL_BT0:
                case DA_COL_BT1:
                case DA_COL_BT2:
                    try {
                        fLayer.writeByte(page,rowIndex + columnIndex - DA_COL_BT0, Integer.parseInt((String) aValue, 16));
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(cDebuggerI8080.this, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                case DA_COL_CMD:
                    break;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с дизассемблированными данными (дизассемблер)".
     */
    private class cDisAsmTable extends JTable implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик булевых полей дизассемблера".
         */
        private class cDisAsmBooleanRenderer extends JCheckBox implements TableCellRenderer {
            /**
             * Конструктор.
             */
            cDisAsmBooleanRenderer() {
                super();
                setBorderPainted(true);
                setHorizontalAlignment(CENTER);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                if (table == null) {
                    return this;
                }

                           value = (value == null) ? Boolean.FALSE : (Boolean) value;
                boolean equalsPC = (fLayer.getCodePage() == fLayer.getCpuPage()) && (table.convertRowIndexToModel(row) == fLayer.getValRegPair(DebugRegPairs.PC));

                if (isSelected) {
                    setForeground(table.getSelectionForeground());
                    if ((Boolean) value) {
                        setBackground(YELLOW_RED  );
                    } else if (equalsPC) {
                        setBackground(YELLOW_GREEN);
                    } else {
                        setBackground(table.getSelectionBackground());
                    }
                } else {
                    setForeground(table.getForeground());
                    if ((Boolean) value) {
                        setBackground(Color.red  );
                    } else if (equalsPC) {
                        setBackground(Color.green);
                    } else {
                        setBackground(table.getBackground());
                    }
                }
                setSelected((Boolean) value);

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
        private class cDisAsmStringRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == null) {
                    return this;
                }

                final cDisAsmTableModel model = (cDisAsmTableModel) table.getModel();

                int address = table.convertRowIndexToModel   (row   );
                int columnM = table.convertColumnIndexToModel(column);
                int pc      = fLayer.getValRegPair(DebugRegPairs.PC );

                if (columnM == DA_COL_CMD) {
                    setHorizontalAlignment(LEFT);
                } else {
                    setHorizontalAlignment(CENTER);
                }

                if (isSelected) {
                    if (((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD)) && (fFocusedAddress >= 0) && ((columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) == fFocusedAddress)) {
                        setForeground(Color.red);
                    } else {
                        setForeground(table.getSelectionForeground());
                    }
                    if ((fLayer.getCodePage() == fLayer.getCpuPage()) && (address == pc)) {
                        setBackground(YELLOW_GREEN);
                    } else {
                        setBackground(table.getSelectionBackground());
                    }
                } else {
                    if (((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD)) && (fFocusedAddress >= 0) && ((columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) == fFocusedAddress)) {
                        setForeground(Color.red);
                    } else {
                        setForeground(table.getForeground());
                    }
                    if ((fLayer.getCodePage() == fLayer.getCpuPage()) && (address == pc)) {
                        setBackground(Color.green);
                    } else {
                        setBackground(table.getBackground());
                    }
                }

                if (hasFocus) {
                    int addressCmd = -1;
                    if (((columnM == DA_COL_ADR) || (columnM == DA_COL_CMD)) && ((addressCmd = columnM == DA_COL_ADR ? address : model.getAddressFromCmd(address)) >= 0)) {
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
         * @param dm модель данных
         */
        cDisAsmTable(TableModel dm) {
            super(dm);
            // Настариваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(DA_COL_TRP).setMaxWidth(22);
            getColumnModel().getColumn(DA_COL_ADR).setMaxWidth(44);
            getColumnModel().getColumn(DA_COL_BT0).setMaxWidth(22);
            getColumnModel().getColumn(DA_COL_BT1).setMaxWidth(22);
            getColumnModel().getColumn(DA_COL_BT2).setMaxWidth(22);
            getColumnModel().getColumn(DA_COL_CMD).setMaxWidth(94);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщиков колонок
            setDefaultRenderer(Boolean.class, new cDisAsmBooleanRenderer());
            setDefaultRenderer( String.class, new cDisAsmStringRenderer ());
            // Блокируем вывод пустых строк и отключаем сортировку колонок
            TableRowSorter<TableModel> sorter = new TableRowSorter<>(dm);
            for (int column = 0; column < sorter.getModel().getColumnCount(); column++) {
                sorter.setSortable(column, false);
            }
            sorter.setRowFilter(RowFilter.regexFilter(".", DA_COL_CMD));
            setRowSorter(sorter);
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.MEMORY  , fLayer.getCodePage())
                || fLayer.eventCheck(arg, TypesEvents.PAGE    , TypesMemoryPages.CODE)
                || fLayer.eventCheck(arg, TypesEvents.REG_PAIR, DebugRegPairs.PC)
                || fLayer.eventCheck(arg, TypesEvents.STEP    , null)) {
                int rowM =    getFocusedRowModel(this);
                int colM = getFocusedColumnModel(this);
                // Вызываем обновление данных
                ((AbstractTableModel) getModel()).fireTableDataChanged();
                // Восстанавливаем предыдущую позицию
                gotoAddress(rowM, colM);
            } else if (fLayer.eventCheck(arg, TypesEvents.TRAPS, null)) {
                Object detail = TypesEvents.TRAPS.getDetail();
                if ((detail == null) || ((detail instanceof cTrap) && (((cTrap) detail).getPage() == fLayer.getCodePage()))) {
                    repaint();
                }
            }
        }

        /**
         * Выполняет переход к заданному адресу в таблице дизассемблера.
         * @param address адрес
         * @param column колонка (в формате табличной модели), которую необходимо выделить
         */
        void gotoAddress(int address, int column) {
            int rowV, colV = convertColumnIndexToView(column);
            if (colV != -1) {
                // Пытаемся найти видимую строку <= address
                address &= 0xFFFF;
                while (((rowV = convertRowIndexToView(address)) == -1) && (address > 0)) {
                    address--;
                }
                // Если нашли строку, то выполняем позиционирование на неё
                if (rowV != -1) {
                    fFocusedAddress = -1; //сбрасываем fFocusedAddress
                    gotoTableCell(this, rowV, colV, true);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Метка для отображения содержимого регистра флагов".
     */
    private class cFlagsRegLabel extends JLabel implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик для cFlagsRegLabel".
         */
        private class cFlagsRegLabelUI extends BasicLabelUI {
            @Override
            protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
                if (!s.isEmpty()) {
                    int y      = l.getY();
                    int height = l.getHeight();
                    int index  = l.getDisplayedMnemonicIndex();

                    int pAF = fLayer.getPrevValRegPair(DebugRegPairs.AF);
                    int cAF = fLayer.getValRegPair    (DebugRegPairs.AF);

                    char c; int width;
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
        cFlagsRegLabel() {
            super(fLayer.getVisViewsFlagsReg());
            // Устанавливаем рисовальщик для метки
            setUI(new cFlagsRegLabelUI());
            // Настариваем параметры отображения метки
            setOpaque(true);
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            setBorder(FOCUS_BORDER);
            setBackground(Color.white);
            setHorizontalAlignment(SwingConstants.CENTER);
            // Подключаемся к fWrapCPU для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.REG_PAIR, DebugRegPairs.AF)
                || fLayer.eventCheck(arg, TypesEvents.STEP    , null)) {
                setText(fLayer.getVisViewsFlagsReg());
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для отображения и редактирования регистровых пар CPU".
     */
    private class cRegCpuTableModel extends AbstractTableModel {
        /**
         * Конструктор.
         */
        cRegCpuTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return CR_NUM_ROWS;
        }

        @Override
        public int getColumnCount() {
            return CR_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            Object obj;
            if ((columnIndex >= 0) && (columnIndex <= getColumnCount()) && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case CR_COL_REG:
                    return DebugRegPairs.values()[rowIndex].name();
                case CR_COL_EQU:
                    return "=";
                case CR_COL_DAT:
                    return String.format("%04X", fLayer.getValRegPair(DebugRegPairs.values()[rowIndex]));
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case CR_COL_REG:
                case CR_COL_EQU:
                    return false;
                case CR_COL_DAT:
                    return (rowIndex >= 0) && (rowIndex < CR_NUM_ROWS);
                default:
                    return super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case CR_COL_REG:
                case CR_COL_EQU:
                    break;
                case CR_COL_DAT:
                    try {
                        DebugRegPairs regPair = DebugRegPairs.values()[rowIndex];
                        // Пишем данные в регистровые пары
                        fLayer.setValRegPair(regPair, Integer.parseInt((String) aValue, 16));
                        if (DebugRegPairs.PC.equals(regPair)) {
                            // Если была запись в PC, то показываем код из страницы CPU и переходим на address = PC
                                  fLayer.setCodePage(fLayer.getCpuPage());
                            fDisAsmTable.gotoAddress(fLayer.getValRegPair(DebugRegPairs.PC), DA_COL_ADR);
                        }
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(cDebuggerI8080.this, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                default:
                    break;
            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица для отображения и редактирования регистровых пар CPU".
     */
    private class cRegCpuTable extends JTable implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы регистровых пар".
         */
        private class cRegCpuStringRenderer extends DefaultTableCellRenderer {
            private int fPaintColumn, fCompareResult;
            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Класс "Рисовальщик ячеек (JLabel) с регистровыми парами".
             * Раскрашивает разными цветами название регистровой пары, а также старший и младший байты регистровой пары.
             */
            private class cMultiColorLabelUI extends BasicLabelUI {
                @Override
                protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
                    if (!s.isEmpty()) {
                        int   index = l.getDisplayedMnemonicIndex();
                        Color color = (fCompareResult <= 0) ? l.getForeground() : Color.red;

                        if ((fCompareResult == 0) || (fCompareResult == 1)) {
                            int endIndex = (fPaintColumn == CR_COL_REG) ? 1 : 2;
                            String str = s.substring(0, endIndex);

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
            cRegCpuStringRenderer() {
                super();
                setUI(new cMultiColorLabelUI());
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.setHorizontalAlignment(CENTER);
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (table == null) {
                    return this;
                }
                fPaintColumn   = table.convertColumnIndexToModel(column);
                fCompareResult = fLayer.getChangesRegPair(DebugRegPairs.values()[table.convertRowIndexToModel(row)]);

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

                if ((fCompareResult >= 0) && (fPaintColumn == CR_COL_EQU)) {
                    fCompareResult = -1;
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
        cRegCpuTable(TableModel dm) {
            super(dm);
            // Настариваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(CR_COL_REG).setMaxWidth(28);
            getColumnModel().getColumn(CR_COL_EQU).setMaxWidth(19);
            getColumnModel().getColumn(CR_COL_DAT).setMaxWidth(40);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new cRegCpuStringRenderer());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.REG_PAIR, null)
                || fLayer.eventCheck(arg, TypesEvents.STEP    , null)) {
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных таблицы с данными стека".
     */
    private class cStackTableModel extends AbstractTableModel {
        /**
         * Конструктор.
         */
        cStackTableModel() {
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
        public Class<?> getColumnClass(int columnIndex) {
            Object obj;
            if ((columnIndex >= 0) && (columnIndex <= getColumnCount()) && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            if (column == SP_COL_DAT) {
                return "(SP)";
            }
            return super.getColumnName(column);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == SP_COL_DAT) {
                int page    = fLayer.getCpuPage();
                int address = fLayer.getValRegPair(DebugRegPairs.SP) + (rowIndex << 1);
                return String.format("%04X", fLayer.debugReadByte(page, address) | (fLayer.debugReadByte(page, address + 1) << 8));
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == SP_COL_DAT || super.isCellEditable(rowIndex, columnIndex);
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == SP_COL_DAT) {
                int page    = fLayer.getCpuPage();
                int address = fLayer.getValRegPair(DebugRegPairs.SP) + (rowIndex << 1);
                try {
                    int value = Integer.parseInt((String) aValue, 16);
                    fLayer.disableEvents();
                    fLayer.writeByte(page, address, value & 0xFF);
                    fLayer.writeByte(page, address + 1, value >> 8);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(cDebuggerI8080.this, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                } finally {
                    if (fLayer.isEventsDisabled()) {
                        fLayer.enableEvents();
                        fLayer.sendEvent(TypesEvents.MEMORY, page);
                    }
                }

            }
            super.setValueAt(aValue, rowIndex, columnIndex);
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Таблица с данными стека".
     */
    private class cStackTable extends JTable implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными стека".
         */
        private class cStackStringRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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
        cStackTable(TableModel dm) {
            super(dm);
            // Настариваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(SP_COL_DAT).setMaxWidth(40);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new cStackStringRenderer());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.REG_PAIR, DebugRegPairs.SP)
                || fLayer.eventCheck(arg, TypesEvents.MEMORY  , fLayer.getCpuPage())
                || fLayer.eventCheck(arg, TypesEvents.PAGE    , TypesMemoryPages.CPU)
                || fLayer.eventCheck(arg, TypesEvents.STEP    , null)) {
                repaint();
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных таблицы с данными ловушек".
     */
    private class cTrapsTableModel extends AbstractTableModel {
        /**
         * Конструктор.
         */
        cTrapsTableModel() {
            super();
        }

        @Override
        public int getRowCount() {
            return fLayer.getTrapCount();
        }

        @Override
        public int getColumnCount() {
            return TP_NUM_COLUMNS;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            Object obj;
            if ((columnIndex >= 0) && (columnIndex <= getColumnCount()) && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case TP_COL_PAG:
                    return "Страница";
                case TP_COL_ADR:
                    return "Адрес";
                default:
                    return super.getColumnName(column);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            cTrap trap = fLayer.getTrap(rowIndex);
            switch (columnIndex) {
                case TP_COL_PAG:
                    return fLayer.getNamePage(trap.getPage());
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
    private class cTrapsTable extends JTable implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными ловушек".
         */
        private class cTrapsStringRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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
        cTrapsTable(TableModel dm) {
            super(dm);
            // Настариваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(TP_COL_PAG).setMaxWidth(94);
            getColumnModel().getColumn(TP_COL_ADR).setMaxWidth(46);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new cTrapsStringRenderer());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (fLayer.eventCheck(arg, TypesEvents.TRAPS, null)) {
                // Обновляем данные в таблице
                ((AbstractTableModel) getModel()).fireTableDataChanged();
                // Позиционируемся на добавленную ловушку
                int index; Object detail = TypesEvents.TRAPS.getDetail();
                if ((detail instanceof cTrap) && ((index = fLayer.getTrapIndex((cTrap) detail)) != -1)) {
                    gotoTableCell(this, convertRowIndexToView(index), convertColumnIndexToView(TP_COL_PAG), false);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Метка для отображения информации о странице памяти CPU".
     */
    private class cCpuMemPageLabel extends JLabel implements Observer {
        /**
         * Конструктор.
         */
        cCpuMemPageLabel() {
            super();
            // Настариваем параметры отображения метки
            setOpaque(true);
            setFont(DEFAULT_FONT);
            setBorder(FOCUS_BORDER);
            setText(getCpuPageInfo());
            setBackground(Color.white);
            setHorizontalAlignment(SwingConstants.CENTER);
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        /**
         * Возвращает информации о странице памяти CPU в виде строки.
         * @return строка с информацией
         */
        private String getCpuPageInfo() {
            return "CPU работает в: ".concat(fLayer.getNamePage(fLayer.getCpuPage()));
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.PAGE, TypesMemoryPages.CPU)
                || fLayer.eventCheck(arg, TypesEvents.STEP, null)) {
                setText(getCpuPageInfo());
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Раскрывающийся список страниц памяти для просмотра кода".
     */
    private class cCodeMemPagesComboBox extends JComboBox<String> implements Observer {
        /**
         * Конструктор.
         */
        cCodeMemPagesComboBox() {
            super();
            // Настариваем параметры отображения метки
            setFont(DEFAULT_FONT);
            setMinimumSize(new Dimension(50, 17));
            // Добавляем элементы списка
            for (int i = 0, j = fLayer.getNumPages(); i < j; i++) {
                addItem("← ".concat(fLayer.getNamePage(i)));
            }
            // Выделяем элемент, соответствующий странице памяти Data RAM
            setSelectedIndex((fLayer.getCodePage() >= cMD_SpMX_RAM.ROM_DISK) ? (getItemCount() - 1) : fLayer.getCodePage());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.PAGE, TypesMemoryPages.CODE)
                || fLayer.eventCheck(arg, TypesEvents.STEP, null)) {
                int index = (fLayer.getCodePage() >= cMD_SpMX_RAM.ROM_DISK) ? (getItemCount() - 1) : fLayer.getCodePage();
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
    private class cDataMemPagesComboBox extends JComboBox<String> implements Observer {
        /**
         * Конструктор.
         */
        cDataMemPagesComboBox() {
            super();
            // Настариваем параметры отображения списка
            setFont(DEFAULT_FONT);
            setMinimumSize(new Dimension(50, 17));
            // Добавляем элементы списка
            for (int i = 0, j = fLayer.getNumPages(); i < j; i++) {
                addItem("↓ ".concat(fLayer.getNamePage(i)));
            }
            // Выделяем элемент, соответствующий странице памяти Data RAM
            setSelectedIndex((fLayer.getDataPage() >= cMD_SpMX_RAM.ROM_DISK) ? (getItemCount() - 1) : fLayer.getDataPage());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (fLayer.eventCheck(arg, TypesEvents.PAGE, TypesMemoryPages.DATA)) {
                int index = (fLayer.getDataPage() >= cMD_SpMX_RAM.ROM_DISK) ? (getItemCount() - 1) : fLayer.getDataPage();
                if (getSelectedIndex() != index) {
                    setSelectedIndex(index);
                }
            }
        }
    }
    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Класс "Модель данных для данных оперативной памяти".
     */
    private class cMemDatTableModel extends AbstractTableModel {
        /**
         * Конструктор.
         */
        cMemDatTableModel() {
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
        public Class<?> getColumnClass(int columnIndex) {
            Object obj;
            if ((columnIndex >= 0) && (columnIndex <= getColumnCount()) && ((obj = getValueAt(0, columnIndex)) != null)) {
                return obj.getClass();
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case MD_COL_ADR:
                    return "Адрес";
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            int page = fLayer.getDataPage();
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
                    return String.format("%02X", fLayer.debugReadByte(page, (rowIndex << 4) + columnIndex - MD_COL_B00));
                case MD_COL_STR: {
                    char[] str  = new char[16];
                    for (int i  = 0, j = rowIndex << 4; i < 16; i++, j++) {
                         str[i] = KOI8.charAt(fLayer.debugReadByte(page, j));
                    }
                    return new String(str);
                }
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
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
                    return true;
                default:
                    return super.isCellEditable(rowIndex, columnIndex);
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            int page = fLayer.getDataPage();
            switch (columnIndex) {
                case MD_COL_ADR:
                    break;
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
                        fLayer.writeByte(page, (rowIndex << 4) + columnIndex - MD_COL_B00, Integer.parseInt((String) aValue, 16));
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(cDebuggerI8080.this, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                case MD_COL_STR: {
                    String str = (String) aValue;
                       int len = Math.min(str.length(), 16);

                    try {
                        fLayer.disableEvents();
                        for (int i = 0, adr = rowIndex << 4, val; i < len; i++, adr++) {
                            if ((      ( val  =        str.charAt(i)) != SKIP_CHAR)
                                    && ((val  =    KOI8.indexOf(val)) != -1)
                                    && ( val != fLayer.readByte(page, adr))) {
                                fLayer.writeByte(page, adr, val);
                            }
                        }
                    } finally {
                        if (fLayer.isEventsDisabled()) {
                            fLayer.enableEvents();
                            fLayer.sendEvent(TypesEvents.MEMORY, page);
                        }
                    }
                    break;
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
    private class cMemDatTable extends JTable implements Observer {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        /**
         * Класс "Рисовальщик строковых полей таблицы с данными памяти".
         */
        private class cMemDatStringRenderer extends DefaultTableCellRenderer {
            private int fPaintRow, fPaintColumn, fFocusedRow, fFocusedColumn;

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            /**
             * Класс "Рисовальщик ячеек (JLabel) с данными памяти".
             * Умеет подсвечивать символ, соответствующий выбранному коду.
             */
            private class cMultiColorLabelUI extends BasicLabelUI {
                @Override
                protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
                    if (!s.isEmpty()) {
                        Color foreground = l.getForeground();
                        int   index      = l.getDisplayedMnemonicIndex();

                        if (   (fPaintRow      == fFocusedRow)
                            && (fPaintColumn   == MD_COL_STR )
                            && (fFocusedColumn >= MD_COL_B00 )
                            && (fFocusedColumn <= MD_COL_B15 )) {

                            String  str;
                            boolean phase = false;
                            for (int beginIndex = 0, endIndex = fFocusedColumn - MD_COL_B00, len = s.length(); beginIndex < len; ) {
                                if (endIndex > 0) {
                                    g.setColor(phase ? Color.red : foreground);
                                    str = s.substring(beginIndex, endIndex);
                                    drawStringUnderlineCharAt(g, str, index, textX, textY);
                                    textX += g.getFontMetrics().stringWidth(str);
                                }
                                phase      = !phase;
                                beginIndex = endIndex;
                                endIndex   = phase ? endIndex + 1 : len;
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
            cMemDatStringRenderer() {
                super();
                setUI(new cMultiColorLabelUI());
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.setHorizontalAlignment(CENTER);
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (table == null) {
                    return this;
                }

                fPaintRow    = table.convertRowIndexToModel(row);
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
        cMemDatTable(TableModel dm) {
            super(dm);
            // Настариваем параметры отображения таблицы
            setFont(DEFAULT_FONT);
            getTableHeader().setFont(HEADER_FONT);
            setSelectionBackground (Color.yellow);
            getColumnModel().getColumn(MD_COL_ADR).setMaxWidth(44);
            for (int i = MD_COL_B00; i <= MD_COL_B15; i++) {
                getColumnModel().getColumn(i).setMaxWidth(22);
            }
            getColumnModel().getColumn(MD_COL_STR).setMaxWidth(130);
            setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            // Подключаем рисовальщика полей
            setDefaultRenderer(String.class, new cMemDatStringRenderer());
            // Подключаемся к fLayer для прослушивания
            fLayer.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            if (   fLayer.eventCheck(arg, TypesEvents.MEMORY, fLayer.getDataPage())
                || fLayer.eventCheck(arg, TypesEvents.PAGE  , TypesMemoryPages.DATA)
                || fLayer.eventCheck(arg, TypesEvents.STEP  , null)) {
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
    private void gotoTableCell(final JTable table, int row, int column, boolean setFocus) {
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
            final Rectangle visRect = new Rectangle(celRect.x, ((JScrollPane) c).getVerticalScrollBar().getValue(), celRect.width, ((JScrollPane) c).getVerticalScrollBar().getVisibleAmount());
            if (!visRect.contains(celRect)) {
                int val = celRect.y - (Math.round((float) visRect.height / (celRect.height << 1)) - 1) * celRect.height;
                ((JScrollPane) c).getVerticalScrollBar().setValue((val < 0) ? 0 : val);
            }
        }
        // Устанавливаем фокус на таблице
        if (setFocus) {
            table.requestFocusInWindow();
        }
    }

    /**
     * Формирует диалог ввода адресов кода/данных и выполняет переход к этим адресам в таблицах просмотра кода/данных.
     */
    private void gotoAddress() {
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Класс "Панель для диалога ввода адреса".
        class cInputAddressPanel extends JPanel {
            private final cExtFormattedTextField fCodeAddress;
            private final cExtFormattedTextField fDataAddress;

            // Конструктор
            private cInputAddressPanel() {
                super(new GridLayout(2, 2));

                JLabel codeLabel = new JLabel("Адрес (code):");
                JLabel dataLabel = new JLabel("Адрес (data):");
                codeLabel.setFont(HEADER_FONT);
                dataLabel.setFont(HEADER_FONT);

                fCodeAddress = new cExtFormattedTextField(WORD_MASK, '0');
                fCodeAddress.setValue(String.format("%04X", getFocusedRowModel(fDisAsmTable)));
                fDataAddress = new cExtFormattedTextField(WORD_MASK, '0');
                fDataAddress.setValue(String.format("%04X", fMemDatTable.getAddress()));

                add(codeLabel);
                add(fCodeAddress);
                add(dataLabel);
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
                        // не нужен
                    }

                    @Override
                    public void ancestorMoved(AncestorEvent event) {
                        // не нужен
                    }
                });

                // Используем для перевода фокуса на следующий компонент
                ActionListener al = e -> ((JComponent)e.getSource()).transferFocus();
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
                    JOptionPane.showMessageDialog(cDebuggerI8080.this, e.toString(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
                return result;
            }
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        cInputAddressPanel inputAddressPanel = new cInputAddressPanel();
        int result =  JOptionPane.showConfirmDialog(cDebuggerI8080.this, inputAddressPanel,
                "Go to ...", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
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
        class cInputDataPanel extends JPanel {
            private final static String REGEXP_STRING_BYTES = "^([\\dA-F]{1,2}( +|$))+";

            private final JTextField fBytes;
            private final JTextField fChars;

            // Функция - преобразует строку из байт в строку из символов.
            private final Function<String, String> bytesToChars = strBytes -> {
                    strBytes = strBytes.trim();
                if (strBytes.matches(REGEXP_STRING_BYTES)) {
                    String[] b = strBytes.split(" +"); // разбиваем строку из байт на отдельные байты
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
            private cInputDataPanel() {
                super(new GridBagLayout());

                JLabel bytesLabel = new JLabel("Байты :");
                JLabel charsLabel = new JLabel("Строка:");
                fBytes = new JTextField(new PlainDocument() {
                    @Override
                    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                        str = str.toUpperCase();
                        // Пропускаем только шестнадцатеричные цифры и пробел
                        if (str.matches("[\\dA-F ]+")) {
                            super.insertString(offs, str, a);
                        }
                    }
                }, fPrevStringBytes, 0);
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
                    }

                    @Override
                    public void ancestorMoved(AncestorEvent event) {
                    }
                });

                // Используем для синхронного преобразования вводимых байт в текст
                fBytes.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        if (fBytes.isFocusOwner()) {
                            String strBytes = fBytes.getText().trim();
                            fBytes.setBackground((strBytes.equals("") || strBytes.matches(REGEXP_STRING_BYTES)) ? fChars.getBackground() : Color.pink);
                            fChars.setText(bytesToChars.apply(fBytes.getText()));
                        }
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        insertUpdate(e);
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        // не нужен
                    }
                });

                // Используем для синхронного преобразования вводимых символов в байты
                fChars.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        if (fChars.isFocusOwner()) {
                            String strBytes = "";
                            String strChars = fChars.getText();
                            for (int i = 0, len =  strChars.length(), val; i < len; i++) {
                                val = KOI8.indexOf(strChars.charAt(i));
                                strBytes = strBytes.concat(String.format("%02X ", (val == -1) ? 0 : val));
                            }
                            fBytes.setText(strBytes.trim());
                        }
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        insertUpdate(e);
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        // не нужен
                    }
                });

                // Используем для перевода фокуса на следующий компонент
                ActionListener al = e -> ((JComponent)e.getSource()).transferFocus();
                fBytes.addActionListener(al);
                fChars.addActionListener(al);

                // Используем для проверки корректности строки из байт
                fBytes.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        String strBytes = fBytes.getText().trim();
                        if (!( strBytes.equals("") || strBytes.matches(REGEXP_STRING_BYTES))) {
                            JOptionPane.showMessageDialog(cDebuggerI8080.this,
                                    String.format("Некорректно заполнена строка из байт:%n[%s]%nПоиск невозможен!", strBytes),
                                    "Ошибка", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            }

            /**
             * Выполняет поиск данных из fBytes в странице памяти Data MEM.
             * @param address начальный адрес для поиска
             * @return адрес начала найденных данных
             * или -1 = в fBytes некорректные данные
             * или -2 = данные не найдены
             */
            private int find(int address) {
                String strBytes = fBytes.getText().trim();
                if (strBytes.matches(REGEXP_STRING_BYTES)) {
                    // Запоминаем поисковую строку
                    fPrevStringBytes = strBytes;

                    // Разбиваем строку из байт на отдельные байты
                    String[] s = strBytes.split(" +");
                    int length = s.length;
                      byte[] b = new byte[length];
                    for (int i = 0; i < length; i++) {
                        try {
                            b[i] = (byte) Integer.parseInt(s[i], 16);
                        } catch (NumberFormatException e) {
                            b[i] = 0;
                        }
                    }

                    if (address + length >= 0x1_0000) {
                        address = 0;
                    }

                    // Выполняем поиск данных
                    for (int i; address < 0x1_0000; address++) {
                        for (i = 0; i < length; i++) {
                            if (fLayer.debugReadByte(fLayer.getDataPage(), address + i) != (b[i] & 0xFF)) {
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
        cInputDataPanel inputDataPanel = new cInputDataPanel();
        int result = JOptionPane.showConfirmDialog(cDebuggerI8080.this, inputDataPanel,
                String.format("Поиск в странице: [%s]", fLayer.getNamePage(fLayer.getDataPage())),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int  start   = (fMemDatTable.getAddress() + 1) & 0xFFFF;
            int  address = inputDataPanel.find(start);
            if ((address == -2) && (start > 0)) {
                 address = inputDataPanel.find(0);
            }
                   if (address >=  0) {
                fMemDatTable.gotoAddress(address);
            } else if (address == -2) {
                JOptionPane.showMessageDialog(cDebuggerI8080.this,
                        String.format("Заданные для поиска данные:%n[%s]%nНе найдены!", fPrevStringBytes),
                        "Информация", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Выполняет один шаг отладчика.
     */
    private void step() {
        // Показываем код из страницы, в которой работает CPU
        fLayer.setCodePage(fLayer.getCpuPage());
        // Сохраняем регистровые пары
        fLayer.saveAllRegPairs();
        if (fLayer.execOneCmdCPU()) {
            // Уведомляем слушателей об изменениях
            fLayer.afterStep();
            // Устанавливаем курсор на позицию = PC
            fDisAsmTable.gotoAddress(fLayer.getValRegPair(DebugRegPairs.PC), DA_COL_ADR);
        }
    }

    /**
     * Выполняет один шаг отладчика с выполнением подпрограмм.
     */
    private void stepOver() {
        // Показываем код из страницы, в которой работает CPU
        fLayer.setCodePage(fLayer.getCpuPage());
        // Получаем адрес следующей команды после команды вызова подпрограммы (CALL/RST)
        int address = ((cDisAsmTableModel) fDisAsmTable.getModel()).getAddressAfterCallCmd(fLayer.getValRegPair(DebugRegPairs.PC));
        if (address == -1) {
            step();
        } else {
            // Сохраняем регистровые пары
            fLayer.saveAllRegPairs();
            // Добавляем StepOver ловушку
            fLayer.addTrap(fLayer.getCodePage(), address, true);
            // Закрываем отладчик
            setVisible(false);
        }
    }

    /**
     * Выполняет запуск с места, на которое указывает курсор.
     */
    private void runToCursor() {
        fLayer.setCpuPage(fLayer.getCodePage());
        fLayer.setValRegPair(DebugRegPairs.PC, getFocusedRowModel(fDisAsmTable));
    }
}