package com.sbt.compliance.aml.mockup.helpers.object.loader;

import com.sbt.compliance.utils.JAXBHelper;

import java.util.Set;

/**
 * Загрузчик объектов из xml-файлов/ресурсов.
 *
 * @author -=AVSh=-
 */
public final class XmlObjectLoader {
    // JAVA OPTS в виде -Dxml.root.path=путь_к_корневому_каталогу_с_файлами_xml
    private static final String XML_ROOT_PATH_PROPERTY_KEY = "xml.root.path";
    private static final String XML_RESOURCES_ROOT_PATH = "xml";
    private static final String XML_FILE_EXT = "xml";

    private static final ObjectLoader OBJECT_LOADER = ObjectLoader.getObjectLoader(
            System.getProperty(XML_ROOT_PATH_PROPERTY_KEY),
            XML_RESOURCES_ROOT_PATH,
            XML_FILE_EXT,
            JAXBHelper::unmarshalFromInputStream);

    private XmlObjectLoader() {
        //
    }

    /**
     * Возвращает набор имен файлов/ресурсов (для использования в методе getObject)
     * с расширением xml по заданному классу и маске.
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
     * Получает объект из xml-файла.
     *
     * @param clazz класс объекта
     * @param name  имя для поиска объекта
     * @return объект или null, если были ошибки
     */
    public static <T> T getObject(final Class<T> clazz, final String name) {
        return OBJECT_LOADER.getObject(clazz, name);
    }
}
