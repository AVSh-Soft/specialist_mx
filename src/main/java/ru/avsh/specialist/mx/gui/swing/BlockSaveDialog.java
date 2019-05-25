package ru.avsh.specialist.mx.gui.swing;

import ru.avsh.specialist.mx.gui.swing.utils.JFormattedTextFieldExt;
import ru.avsh.specialist.mx.helpers.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.nio.file.Paths;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Класс "Диалог сохранения блока".
 * @author -=AVSh=-
 */
public final class BlockSaveDialog extends JDialog {
    private static final long serialVersionUID = -8762963068896802973L;

    public  static final String TITLE     = "Сохранить блок";
    private static final String WORD_MASK = "HHHH";

    private File    fFile  ;
    private boolean fResult;

    private int fBeginAddress;
    private int   fEndAddress;
    private int fStartAddress;

    /**
     * Instantiates a new Save block dialog.
     * @param owner the owner
     */
    public BlockSaveDialog(Frame owner) {
        super(owner, true);
        initComponents();
    }

    /**
     * Инициализация компонентов и обработчиков событий.
     */
    private void initComponents() {
        setTitle(TITLE);

        final JPanel dialogPane  = new JPanel();
        final JPanel mainPane    = new JPanel();
        final JPanel filePane    = new JPanel();
        final JPanel addressPane = new JPanel();
        final JPanel buttonBar   = new JPanel();

        final JLabel fileNameLabel = new JLabel("Имя файла:");
        final JLabel fileTypeLabel = new JLabel("Тип файла:");

        final JTextField fileNameTextField = new JTextField(new PlainDocument() {
            private static final long serialVersionUID = -2956459963542774236L;

            @Override
            public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                if (str.matches("^[^*?\"<>|]+$")) {
                    super.insertString(offs, str, a);
                }
            }
        }, "", 0);

        final JButton             fileNameButton = new JButton("...");
        final JComboBox<String> fileTypeComboBox = new JComboBox<>(new String[]{"cpu(i80)", "rks"}); // Индекс: "cpu(i80)" = 0, "rks" = 1
        final JSeparator           fileSeparator = new JSeparator();

        final JLabel addressLabel      = new JLabel("Адреса:");
        final JLabel beginAddressLabel = new JLabel("Начало:");
        final JFormattedTextFieldExt beginAddressTextField = new JFormattedTextFieldExt(WORD_MASK, '0');
        final JLabel endAddressLabel   = new JLabel("Конец:" );
        final JFormattedTextFieldExt endAddressTextField   = new JFormattedTextFieldExt(WORD_MASK, '0');
        final JLabel startAddressLabel = new JLabel("Старт:" );
        final JFormattedTextFieldExt startAddressTextField = new JFormattedTextFieldExt(WORD_MASK, '0');
        final JSeparator addressSeparator = new JSeparator();

        final JButton okButton     = new JButton("OK"    );
        final JButton cancelButton = new JButton("Отмена");

        beginAddressTextField.setValue("0000");
          endAddressTextField.setValue("0000");
        startAddressTextField.setValue("0000");

        // this
        final Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        // dialogPane
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());
            // mainPane
            {
                mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));
                // filePane
                {
                    filePane.setLayout(new GridBagLayout());
                    ((GridBagLayout) filePane.getLayout()).columnWidths  = new int[]{0, 0, 0, 5, 0, 0};
                    ((GridBagLayout) filePane.getLayout()).rowHeights    = new int[]{0, 5, 0, 5, 3, 0};
                    ((GridBagLayout) filePane.getLayout()).columnWeights = new double[]{0.0, 1.0, 0.0, 0.0, 0.0, 1.0E-4};
                    ((GridBagLayout) filePane.getLayout()).rowWeights    = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};

                    filePane.add(fileNameLabel,
                            new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    filePane.add(fileTypeLabel,
                            new GridBagConstraints(4, 0, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    filePane.add(fileNameTextField,
                            new GridBagConstraints(0, 2, 2, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    filePane.add(fileNameButton,
                            new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    filePane.add(fileTypeComboBox,
                            new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    filePane.add(fileSeparator,
                            new GridBagConstraints(0, 4, 5, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                }
                mainPane.add(filePane);
                // addressPane
                {
                    addressPane.setLayout(new GridBagLayout());
                    ((GridBagLayout) addressPane.getLayout()).columnWidths  = new int[]{0, 5, 40, 10, 0, 5, 40, 10, 0, 5, 40, 0, 0};
                    ((GridBagLayout) addressPane.getLayout()).rowHeights    = new int[]{0, 5, 0, 5, 3, 0};
                    ((GridBagLayout) addressPane.getLayout()).columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0E-4};
                    ((GridBagLayout) addressPane.getLayout()).rowWeights    = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};

                    addressPane.add(addressLabel,
                            new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(beginAddressLabel,
                            new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(beginAddressTextField,
                            new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(endAddressLabel,
                            new GridBagConstraints(4, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(endAddressTextField,
                            new GridBagConstraints(6, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(startAddressLabel,
                            new GridBagConstraints(8, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(startAddressTextField,
                            new GridBagConstraints(10, 2, 1, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    addressPane.add(addressSeparator,
                            new GridBagConstraints(0, 4, 12, 1, 0.0, 0.0,
                                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                }
                mainPane.add(addressPane);
            }
            dialogPane.add(mainPane, BorderLayout.CENTER);
            // buttonBar
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridBagLayout());
                ((GridBagLayout) buttonBar.getLayout()).columnWidths  = new    int[]{0  , 85  , 80  };
                ((GridBagLayout) buttonBar.getLayout()).columnWeights = new double[]{1.0,  0.0,  0.0};

                buttonBar.add(okButton,
                        new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0,
                                GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 5), 0, 0));
                buttonBar.add(cancelButton,
                        new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
                                GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
            }
            dialogPane.add(buttonBar, BorderLayout.PAGE_END);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);

        // -=-=-=-=- Обработчики событий -=-=-=-=-
        // Потеря фокуса на поле с именем файла
        fileNameTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                String s = fileNameTextField.getText();
                if (!s.isEmpty()) {
                    final String ext = String.valueOf(fileTypeComboBox.getSelectedItem()).substring(0, 3);
                    int ind = s.lastIndexOf('.');
                    if (ind > 0) {
                        s = s.substring(0, ind + 1).concat(ext);
                    } else {
                        s = s.concat(".").concat(ext);
                    }
                    fileNameTextField.setText(s);
                }
            }
        });

        // Нажатие на кнопку около имени файла
        fileNameButton.addActionListener(actionEvent -> {
            File file;
            String fileName = fileNameTextField.getText().trim();
            String fileType = String.valueOf(fileTypeComboBox.getSelectedItem()).substring(0, 3);

            final JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Сохранить файл");
            if (fileName.isEmpty()) {
                chooser.setCurrentDirectory(new File(Constants.getCurPath()));
            } else if (fileName.matches("^[^:/\\\\]+$")) {
                chooser.setCurrentDirectory(new File(Constants.getCurPath()));
                chooser.setSelectedFile(new File(fileName));
            } else {
                file = new File(fileName);
                if (Paths.get(file.getParent()).toFile().exists()) {
                    chooser.setSelectedFile(file);
                } else {
                    chooser.setCurrentDirectory(new File(Constants.getCurPath()));
                    chooser.setSelectedFile(new File(file.getName()));
                }
            }
            chooser.setFileFilter(new FileNameExtensionFilter("Файлы: *.".concat(fileType), fileType));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                file     = chooser.getSelectedFile();
                fileName =    file.getPath        ();
                final int ind = fileName.lastIndexOf('.');
                if (ind != -1) {
                    fileName = fileName.substring(0, ind + 1).concat(fileType);
                } else {
                    fileName = fileName.concat(".").concat(fileType);
                }
                fileNameTextField.setText(fileName);
                Constants.setCurPath(file.getParent());
            }
        });

        // Изменение типа файла
        fileTypeComboBox.addItemListener(itemEvent -> {
            if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                String s = fileNameTextField.getText();
                if (!s.isEmpty()) {
                    final String ext = ((String) itemEvent.getItem()).substring(0, 3);
                    final    int ind = s.lastIndexOf('.');
                    if (ind > 0) {
                        s = s.substring(0, ind + 1).concat(ext);
                    } else {
                        s = s.concat(".").concat(ext);
                    }
                    fileNameTextField.setText(s);
                }

                // true если выбран тип файла "cpu(i80)"
                final boolean isCPU = ((JComboBox) itemEvent.getSource()).getSelectedIndex() == 0;
                    startAddressLabel.setEnabled(isCPU);
                startAddressTextField.setEnabled(isCPU);
                if (!isCPU) {
                    startAddressTextField.setValue(beginAddressTextField.getValue());
                }
            }
        });

        // Потеря фокуса на поле с адресом начала
        beginAddressTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                // Если выбран тип файла "rks"
                if (fileTypeComboBox.getSelectedIndex() == 1) {
                    startAddressTextField.setValue(beginAddressTextField.getValue());
                }
            }
        });

        // Нажатие на кнопку "OK"
        okButton.addActionListener(actionEvent -> {
            String s = fileNameTextField.getText().trim();
            if (s.isEmpty()) {
                showMessageDialog(this, "Не задано имя файла для сохранения",
                        Constants.STR_ERROR, ERROR_MESSAGE);
                fileNameTextField.requestFocusInWindow();
                return;
            }

            if (s.matches("^[^:/\\\\]+$")) {
                s = Constants.getCurPath().concat(File.separator).concat(s);
            }
            fFile = new File(s);

            s = fFile.getParent();
            if (!Paths.get(s).toFile().exists()) {
                showMessageDialog(this,
                        String.format("Не найден путь: \"%s\" для сохранения файла: \"%s\"", s, fFile.getName()),
                        Constants.STR_ERROR, ERROR_MESSAGE);
                fileNameTextField.requestFocusInWindow();
                return;
            }

            // Здесь проверка сделана излишне (так, на всякий случай)
            JFormattedTextField curTextField = beginAddressTextField;
            try {
                s = (String) curTextField.getValue();
                fBeginAddress = Integer.parseInt(s, 16);

                curTextField  = endAddressTextField;
                s = (String) curTextField.getValue();
                  fEndAddress = Integer.parseInt(s, 16);

                curTextField  = startAddressTextField;
                s = (String) curTextField.getValue();
                fStartAddress = Integer.parseInt(s, 16);
            } catch (NumberFormatException ex) {
                showMessageDialog(this,
                        String.format("Ошибка задания адреса: [%s]%n%s", s, ex.toString()),
                        Constants.STR_ERROR, ERROR_MESSAGE);
                curTextField.requestFocusInWindow();
                return;
            }

            if (fBeginAddress > fEndAddress) {
                showMessageDialog(this,
                        String.format("Начальный адрес: [%04X] больше конечного: [%04X]", fBeginAddress, fEndAddress),
                        Constants.STR_ERROR, ERROR_MESSAGE);
                beginAddressTextField.requestFocusInWindow();
                return;
            }

            setVisible(false);
            fResult = true;
        });

        // Нажатие на кнопку "Cancel"
        cancelButton.addActionListener(actionEvent -> setVisible(false));

        // -=-=-=-=- Завершение формирования окна диалога -=-=-=-=-
        // Устанавливаем размеры окна
        setSize(350, 210);
        setMinimumSize(getSize());
        pack();
        // Выводим окно в центре главного окна программы
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /**
     * Gets result.
     * @return the result
     */
    public boolean getResult() {
        return fResult;
    }

    /**
     * Gets file.
     * @return the file
     */
    public File getFile() {
        return fFile;
    }

    /**
     * Gets begin address.
     * @return the begin address
     */
    public int getBeginAddress() {
        return fBeginAddress;
    }

    /**
     * Gets end address.
     * @return the end address
     */
    public int getEndAddress() {
        return fEndAddress;
    }

    /**
     * Gets start address.
     * @return the start address
     */
    public int getStartAddress() {
        return fStartAddress;
    }
}