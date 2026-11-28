package ru.avsh.specialist.mx.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class FileUtils {

    private FileUtils() {
        //
    }

    /**
     * Возвращает имя файла без расширения.
     *
     * @param file файл
     * @return имя файла без расширения
     */
    public static String getNameWithoutExtension(File file) {
        if (file == null) {
            return "";
        }
        final String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        // Если точки нет или она в самом начале (скрытый файл .gitignore), возвращаем имя целиком
        return (lastDotIndex > 0) ? name.substring(0, lastDotIndex) : name;
    }

    /**
     * Возвращает расширение файла в нижнем регистре.
     *
     * @param file файл
     * @return расширение или пустая строка
     */
    @NotNull
    public static String getFileExtension(final File file) {
        if (file == null) {
            return "";
        }
        final String name = file.getName();
        final int lastDotIndex = name.lastIndexOf('.');
        // Учитываем скрытые файлы вроде ".gitignore", где точка стоит в начале имени
        return (lastDotIndex > 0 && lastDotIndex < name.length() - 1)
                ? name.substring(lastDotIndex + 1).toLowerCase()
                : "";
    }

    public static boolean canWrite(final File file) {
        return (file != null) && (!file.exists() || !file.isDirectory());
    }
}
