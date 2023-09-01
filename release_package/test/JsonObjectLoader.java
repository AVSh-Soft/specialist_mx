package com.sbt.compliance.aml.mockup.helpers.object.loader;

import com.sbt.compliance.utils.JacksonHelper;

import java.util.Set;

/**
 * Загрузчик объектов из json-файлов/ресурсов.
 *
 * @author -=AVSh=-
 */
public final class JsonObjectLoader {
    // JAVA OPTS в виде -Djson.root.path=путь_к_корневому_каталогу_с_файлами_json
    private static final String JSON_ROOT_PATH_PROPERTY_KEY = "json.root.path";
    private static final String JSON_RESOURCES_ROOT_PATH = "json";
    private static final String JSON_FILE_EXT = "json";

    private static final ObjectLoader OBJECT_LOADER = ObjectLoader.getObjectLoader(
            System.getProperty(JSON_ROOT_PATH_PROPERTY_KEY),
            JSON_RESOURCES_ROOT_PATH,
            JSON_FILE_EXT,
            JacksonHelper::deserializeFromInputStream);

    private JsonObjectLoader() {
        //
    }

    /**
     * Возвращает набор имен файлов/ресурсов (для использования в методе getObject)
     * с расширением json по заданному классу и маске.
     * (Список никогда не возвращается = null)
     *
     * @param clazz класс объекта
     * @param mask  маска
     * @return набор файлов/ресурсов
     */
    public static Set<String> getNames(final Class<?> clazz, final String mask) {
        return OBJECT_LOADER.getNames(clazz, mask);
    }

    /**
     * Получает объект из json-файла.
     *
     * @param clazz класс объекта
     * @param name  имя для поиска объекта
     * @return объект или null, если были ошибки
     */
    public static <T> T getObject(final Class<T> clazz, final String name) {
        return OBJECT_LOADER.getObject(clazz, name);
    }
}
