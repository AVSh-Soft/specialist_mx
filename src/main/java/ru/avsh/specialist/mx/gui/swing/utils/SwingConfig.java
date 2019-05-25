package ru.avsh.specialist.mx.gui.swing.utils;

import javax.swing.*;

/**
 * Конфигуратор параметров Swing.
 */
public final class SwingConfig {

    private SwingConfig() {
        //
    }

    public static void init() {
        // Украшаем окна Swing
         JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        // Русифицируем JFileChooser
        UIManager.put("FileChooser.openButtonText"         , "Открыть"  );
        UIManager.put("FileChooser.saveButtonText"         , "Сохранить");
        UIManager.put("FileChooser.cancelButtonText"       , "Отменить" );
        UIManager.put("FileChooser.directoryOpenButtonText", "Открыть"  );

        UIManager.put("FileChooser.lookInLabelText"     , "Смотреть в:" );
        UIManager.put("FileChooser.saveInLabelText"     , "Сохранить в:");
        UIManager.put("FileChooser.fileNameLabelText"   , "Имя файла:"  );
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип файла:"  );

        UIManager.put("FileChooser.openButtonToolTipText"  , "Открыть выделенный файл"     );
        UIManager.put("FileChooser.saveButtonToolTipText"  , "Сохранить выбранный файл"    );
        UIManager.put("FileChooser.cancelButtonToolTipText", "Прервать диалог выбора файла");

        UIManager.put("FileChooser.listViewButtonToolTipText"     , "Список"       );
        UIManager.put("FileChooser.detailsViewButtonToolTipText"  , "Детально"     );
        UIManager.put("FileChooser.directoryOpenButtonToolTipText", "Открыть папку");

        UIManager.put("FileChooser.upFolderToolTipText"  , "На уровень выше");
        UIManager.put("FileChooser.homeFolderToolTipText", "В начало"       );
        UIManager.put("FileChooser.newFolderToolTipText" , "Создать папку"  );

        UIManager.put("FileChooser.fileNameHeaderText", "Имя"     );
        UIManager.put("FileChooser.fileSizeHeaderText", "Размер"  );
        UIManager.put("FileChooser.fileTypeHeaderText", "Тип"     );
        UIManager.put("FileChooser.fileDateHeaderText", "Изменен" );
        UIManager.put("FileChooser.fileAttrHeaderText", "Атрибуты");

        UIManager.put("FileChooser.acceptAllFileFilterText", "Все файлы");

        // Русифицируем JOptionPane
        UIManager.put("OptionPane.yesButtonText"   , "Да"      );
        UIManager.put("OptionPane.noButtonText"    , "Нет"     );
        UIManager.put("OptionPane.cancelButtonText", "Отменить");
    }
}
