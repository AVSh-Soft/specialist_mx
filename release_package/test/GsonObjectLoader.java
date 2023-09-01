package com.sbt.compliance.aml.mockup.helpers.object.loader;

import com.google.gson.*;
import com.sbt.compliance.utils.DateHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

/**
 * Загрузчик объектов из json-файлов/ресурсов, с использованием библиотеки Gson.
 *
 * @author -= AVSh =-
 * Created: 11.09.2020 15:00
 */
public final class GsonObjectLoader {
    // JAVA OPTS в виде -Djson.root.path=путь_к_корневому_каталогу_с_файлами_json
    private static final String JSON_ROOT_PATH_PROPERTY_KEY = "json.root.path";
    private static final String JSON_RESOURCES_ROOT_PATH = "json";
    private static final String JSON_FILE_EXT = "json";

    private static final ObjectLoader OBJECT_LOADER = ObjectLoader.getObjectLoader(
            System.getProperty(JSON_ROOT_PATH_PROPERTY_KEY),
            JSON_RESOURCES_ROOT_PATH,
            JSON_FILE_EXT,
            GsonObjectLoader::deserializeFromInputStream);

    // Возможные форматы дат
    private static final String[] DATE_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"};

    private GsonObjectLoader() {
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

    public static String toJson(final Object object) {
        return createGson().toJson(object);
    }

    public static <T> T fromJson(final String json, final Class<T> clazz) {
        return createGson().fromJson(json, clazz);
    }

    /**
     * Десериализует JSON-данные из потока.
     *
     * @param is        поток с JSON-данными
     * @param valueType класс десериализуемого объекта
     * @param <T>       тип объекта
     * @return объект
     * @throws IOException исключение
     */
    public static <T> T deserializeFromInputStream(final InputStream is, final Class<T> valueType) throws IOException {
        return (is != null) && (valueType != null) ?
                createGson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), valueType) : null;
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .setDateFormat(DateHelper.DATE_FORMAT_STRING)
                // Для типа Date (вместо штатного парсера)
                .registerTypeHierarchyAdapter(Date.class,
                        (JsonDeserializer<Date>) (json, typeOfT, context) ->
                                Optional.ofNullable(json)
                                        .map(JsonElement::getAsString)
                                        .map(stringDate -> DateHelper.parseDate(stringDate, DATE_PATTERNS))
                                        .orElse(null))
                // Для типа Calendar
                .registerTypeHierarchyAdapter(Calendar.class,
                        (JsonDeserializer<Calendar>) (json, typeOfT, context) ->
                                Optional.ofNullable(json)
                                        .map(JsonElement::getAsString)
                                        .map(stringDate -> DateHelper.parseDate(stringDate, DATE_PATTERNS))
                                        .map(date -> {
                                            final Calendar result = Calendar.getInstance();
                                            result.setTime(date);
                                            return result;
                                        })
                                        .orElse(null))
                // Игнорируем поля класса, если они есть в суперклассе
                .addSerializationExclusionStrategy(new GsonObjectLoader.SuperclassExclusionStrategy())
                .addDeserializationExclusionStrategy(new GsonObjectLoader.SuperclassExclusionStrategy())
                // Формируем json красиво
                .setPrettyPrinting()
                .create();
    }

    public static class SuperclassExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            return isFieldInSuperclass(fieldAttributes.getDeclaringClass(), fieldAttributes.getName());
        }

        private boolean isFieldInSuperclass(@NotNull Class<?> subclass, final String fieldName) {
            while ((subclass = subclass.getSuperclass()) != null) {
                if (checkField(subclass, fieldName)) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkField(@NotNull final Class<?> clazz, final String fieldName) {
            try {
                clazz.getDeclaredField(fieldName);
                return true;
            } catch (Exception e) {
                //
            }
            return false;
        }
    }
}
