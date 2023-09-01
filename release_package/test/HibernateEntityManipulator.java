package com.sbt.compliance.aml.mockup.helpers.object.loader.special;

import com.sbt.compliance.aml.mockup.helpers.object.loader.ObjectLoader;
import com.sbt.compliance.exceptions.RuntimeExceptionExt;
import com.sbt.compliance.storage.oracle.utils.EntityKey;
import com.sbt.compliance.utils.JacksonHelper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sbt.compliance.storage.oracle.utils.HibernateEntityHelper.*;
import static com.sbt.compliance.utils.StringHelper.firstCharToUpperCase;

/**
 * -= Манипулятор сущностей Hibernate =-
 * <p>
 * (Загружает сущности Hibernate из json-файлов/ресурсов,
 * сохраняет сущности Hibernate в json-файлы, а также
 * кэширует сохранение сущностей для использования в JUnit тестах.
 * Удаление сущностей поддерживается только в режиме кэширования!).
 *
 * @author -= AVSh =-
 * Created: 31.10.2019 9:45
 */
@Slf4j
public final class HibernateEntityManipulator {
    // JAVA OPTS в виде -Dhibernate.root.path=путь_к_корневому_каталогу_с_файлами_hibernate
    public static final String HIBERNATE_ROOT_PATH_PROPERTY_KEY = "hibernate.root.path";
    public static final String HIBERNATE_RESOURCES_ROOT_PATH = "hibernate";
    public static final String HIBERNATE_FILE_EXT = "json";

    // Маркер для пометки удаленной сущности
    public static final String REMOVED = "removed";
    // Загрузчик фрагментов сущности
    public static final ObjectLoader OBJECT_LOADER = ObjectLoader.getObjectLoader(
            System.getProperty(HIBERNATE_ROOT_PATH_PROPERTY_KEY),
            HIBERNATE_RESOURCES_ROOT_PATH,
            HIBERNATE_FILE_EXT,
            JacksonHelper::deserializeFromInputStream);

    private static final String ERROR_MESSAGE0 = "Внешний корневой путь [%s] для %s-файлов задан некорректно!%n%s";
    private static final String ERROR_MESSAGE1 = "Внешний корневой путь [%s] для %s-файлов не задан!";

    // Для обеспечения кэширования
    private static final AtomicBoolean CACHE_SWITCH = new AtomicBoolean(false);
    private static final Set<EntityKey> ENTITIES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, ConcurrentMap<String, Object>> ENTITIES_CACHE = new ConcurrentHashMap<>();

    private static final Object MUTEX = new Object();

    private HibernateEntityManipulator() {
        //
    }

    /**
     * По заданному классу сущности Hibernate и маске возвращает набор имен сущностей для
     * использования в методе {@link HibernateEntityManipulator#getEntity(Class, String)}
     * (фактически возвращает список имен файлов/ресурсов с расширением json).
     * Список никогда не возвращается = null.
     *
     * @param entityClass класс сущности
     * @param mask        маска
     * @return набор имен Hibernate файлов/ресурсов
     */
    @NotNull
    public static synchronized Set<String> getNames(final Class<?> entityClass, final String mask) {
        final Pattern pattern = OBJECT_LOADER.getPattern(mask, "");
        final Set<String> result = OBJECT_LOADER.getNames(entityClass, mask);
        final Map<String, Object> classEntities = ENTITIES_CACHE.get((entityClass != null) ? entityClass.getName() : null);
        if (MapUtils.isNotEmpty(classEntities)) {
            classEntities.forEach((name, entity) -> {
                if (!REMOVED.equals(entity) && pattern.matcher(name).matches()) {
                    result.add(name);
                }
            });
        }
        return result;
    }

    /**
     * Получает сущность Hibernate из json-файлов (каждая вложенная сущность в отдельном файле).
     *
     * @param entityClass класс сущности Hibernate
     * @param name        имя для поиска сущности Hibernate
     * @return сущность или null, если были ошибки
     */
    public static synchronized <T> T getEntity(final Class<T> entityClass, final String name) {
        checkEntityClass(entityClass);
        return (T) getEntity(entityClass, name, null, new EntityContainer());
    }

    private static Object getEntity(@NotNull final Class<?> entityClass,
                                    final String name,
                                    final Object parentEntity,
                                    @NotNull final EntityContainer container) {

        final String entityIdFromName = OBJECT_LOADER.getNameWithoutExt(name);
        final String nameWithExt = OBJECT_LOADER.getNameWithExt(name);
        final EntityKey entityKey = new EntityKey(entityClass, entityIdFromName);
        final Object prevEntity = container.get(entityKey);
        if (prevEntity != null) {
            return prevEntity;
        }

        // Восстанавливаем сущность
        final Object result = Optional.ofNullable(ENTITIES_CACHE.get(entityClass.getName()))
                .map(innerMap -> innerMap.get(nameWithExt))
                // Обязательно клонируем сущность, полученную из кэша (чтобы не влиять на кэш)
                .map(fragment -> REMOVED.equals(fragment) ? fragment : cloneEntity(fragment, null))
                .orElseGet(() -> OBJECT_LOADER.getObject(entityClass, nameWithExt));

        // Если сущность пуста или удалена
        if ((result == null) || REMOVED.equals(result)) {
            return null;
        }

        // Если имя сущности не соответствует её id
        val entityStrId = getEntityStrId(result);
        if (!Objects.equals(entityStrId, entityIdFromName)) {
            throw new RuntimeExceptionExt(String.format(
                    "У сущности класса [%s] имя [%s] не соответствует id [%s]", entityClass, nameWithExt, entityStrId));
        }

        container.put(entityKey, result);
        // Проходим по полям сущности
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                .forEach(fieldRec -> {
                    final Class<?> fieldClass = fieldRec.getType();
                    final Object fieldValue = fieldRec.getGetter().apply(result);

                    if (fieldRec.isEntity()) {

                        if (fieldValue != null) {
                            // Рекурсия ->>
                            fieldRec.getSetter().accept(
                                    result, getEntity(fieldClass, getEntityStrId(fieldValue), result, container));

                        } else if ((parentEntity != null) && parentEntity.getClass().equals(fieldClass)) {
                            // Заполняем обратную ссылку, если она не была заполнена
                            fieldRec.getSetter().accept(result, parentEntity);
                        }

                    } else if ((fieldValue != null) && !((Collection<?>) fieldValue).isEmpty()) {

                        final Collection<Object> collection = (Collection<Object>) fieldValue;
                        final List<Object> items = collection.stream()
                                // Рекурсия ->>
                                .map(item -> (item == null) ? null :
                                        getEntity(item.getClass(), getEntityStrId(item), result, container))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        collection.clear();
                        collection.addAll(items);
                    }
                });
        return result;
    }

    /**
     * Эмулирует удаление сущности в режиме кэширования
     * (реальное удаление не поддерживается).
     *
     * @param entity сущность Hibernate
     */
    public static synchronized void removeEntity(final Object entity) {
        // Удаление поддерживается только в режиме кэширования
        if ((entity != null) && CACHE_SWITCH.get()) {
            checkEntityClass(Hibernate.getClass(entity));
            removeEntity(entity, new HashContainer());
        }
    }

    private static void removeEntity(final Object entity, @NotNull final HashContainer container) {
        if ((entity == null) || container.contains(entity)) {
            return;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        final String entityStrId = getEntityStrId(entity);
        final String name = OBJECT_LOADER.getNameWithExt(entityStrId);
        if (container.isEmpty()) {
            // Несуществующие сущности не удаляем
            if (getEntity(entityClass, name) == null) {
                return;
            }
            ENTITIES.remove(new EntityKey(entityClass, entityStrId));
        }

        container.add(entity);
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                // Только поля с CascadeType.ALL или CascadeType.REMOVE
                .filter(fieldRec -> (fieldRec.getGetter().apply(entity) != null) &&
                        isCascadeRemovalNeeded(entityClass, fieldRec.getName()))
                .forEach(fieldRec -> {
                    final Object fieldValue = fieldRec.getGetter().apply(entity);
                    if (fieldRec.isEntity()) {
                        // Рекурсия ->>
                        removeEntity(fieldValue, container);
                    } else if (fieldRec.isEntityCollection()) {
                        ((Collection<Object>) fieldValue)
                                // Рекурсия ->>
                                .forEach(item -> removeEntity(item, container));
                    }
                });
        // Запись удаления сущности
        ENTITIES_CACHE.computeIfAbsent(entityClass.getName(), key -> new ConcurrentHashMap<>()).put(name, REMOVED);
    }

    /**
     * Показывает, необходимо ли каскадно удалять подсущность.
     *
     * @param entityClass класс сущности Hibernate
     * @param fieldName   имя поля, в котором находится подсущность
     * @return true - необходимо каскадное удаление подсущности
     */
    public static boolean isCascadeRemovalNeeded(final Class<?> entityClass, final String fieldName) {
        return isCascadeTypeRequired(entityClass, fieldName, CascadeType.REMOVE);
    }

    /**
     * Показывает, имеется ли необходимый каскадный тип в заданном поле сущности.
     *
     * @param entityClass класс сущности
     * @param fieldName   имя поля сущности
     * @param cascadeType {@link CascadeType}
     * @return true - если каскадный тип имеется
     */
    public static boolean isCascadeTypeRequired(final Class<?> entityClass,
                                                final String fieldName,
                                                @NotNull final CascadeType cascadeType) {

        if ((entityClass != null) && (fieldName != null)) {
            try {
                // Если аннотации расставлены над полями
                final Field field = entityClass.getDeclaredField(fieldName);
                CascadeType[] cascadeTypes = getCascadeTypes(field);
                // Если аннотации расставлены над геттерами
                if (cascadeTypes == null) {
                    final Method method = entityClass.getDeclaredMethod("get".concat(firstCharToUpperCase(fieldName)));
                    cascadeTypes = getCascadeTypes(method);
                }
                return (cascadeTypes != null) &&
                        (ArrayUtils.contains(cascadeTypes, CascadeType.ALL) || ArrayUtils.contains(cascadeTypes, cascadeType));

            } catch (ReflectiveOperationException e) {
                //
            }
        }
        return false;
    }

    @Nullable
    private static CascadeType[] getCascadeTypes(@NotNull final AccessibleObject accessibleObject) {
        return Optional.ofNullable(accessibleObject.getDeclaredAnnotation(OneToOne.class))
                .map(OneToOne::cascade)
                .orElseGet(() -> Optional.ofNullable(accessibleObject.getDeclaredAnnotation(OneToMany.class))
                        .map(OneToMany::cascade)
                        .orElseGet(() -> Optional.ofNullable(accessibleObject.getDeclaredAnnotation(ManyToOne.class))
                                .map(ManyToOne::cascade)
                                .orElseGet(() -> Optional.ofNullable(accessibleObject.getDeclaredAnnotation(ManyToMany.class))
                                        .map(ManyToMany::cascade)
                                        // By default no operations are cascaded.
                                        .orElse(null))));
    }

    /**
     * Сохраняет сериализованное представление сущности Hibernate на диск или в кэш
     * (с разбивкой на составляющие сущности и без обратных ссылок).
     *
     * @param entity сущность Hibernate
     */
    public static synchronized void saveEntity(final Object entity) {
        if (entity != null) {
            checkEntityClass(Hibernate.getClass(entity));
            saveEntity(entity, new HashContainer());
        }
    }

    private static void saveEntity(final Object entity, @NotNull final HashContainer container) {
        if ((entity == null) || container.contains(entity)) {
            return;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        final String entityStrId = getEntityStrId(entity);
        final String name = OBJECT_LOADER.getNameWithExt(entityStrId);
        if (container.isEmpty() && CACHE_SWITCH.get()) {
            ENTITIES.add(new EntityKey(entityClass, entityStrId));
        }

        container.add(entity);
        final Object newEntity = createEntity(entityClass);
        getFieldRecs(entityClass).forEach(fieldRec -> {
            final Object fieldValue = fieldRec.getGetter().apply(entity);

            Object fieldNewValue = null;
            if (fieldRec.isEntity()) {

                if (fieldValue != null) {
                    // Рекурсия ->>
                    saveEntity(fieldValue, container);
                    fieldNewValue = createEntityWithId(fieldRec.getType(), getEntityId(fieldValue));
                }

            } else if (fieldRec.isEntityCollection()) {

                final Stream<Object> resultStream;
                if (fieldValue == null) {
                    resultStream = Stream.empty();
                } else {
                    final Class<?> itemClass = getParameterizedType(fieldRec.getGenericType());
                    resultStream = ((Collection<Object>) fieldValue).stream()
                            .filter(Objects::nonNull)
                            // Рекурсия ->>
                            .peek(item -> saveEntity(item, container))
                            .map(item -> createEntityWithId(itemClass, getEntityId(item)));
                }

                final Class<?> collectionClass = fieldRec.getType();
                if (Set.class.isAssignableFrom(collectionClass)) {
                    fieldNewValue = resultStream.collect(Collectors.toSet());
                } else if (List.class.isAssignableFrom(collectionClass)) {
                    fieldNewValue = resultStream.collect(Collectors.toList());
                } else {
                    throw new RuntimeExceptionExt("Неизвестный тип коллекции: ".concat(collectionClass.getName()));
                }

            } else {
                fieldNewValue = fieldValue;
            }

            if (fieldNewValue != null) {
                fieldRec.getSetter().accept(newEntity, fieldNewValue);
            }
        });
        // Запись сущности
        save(entityClass, newEntity, name);
    }

    private static void save(@NotNull final Class<?> entityClass, final Object entity, @NotNull final String name) {
        if ((entity == null) || name.startsWith(ObjectLoader.EXTENSION_SEPARATOR)) {
            return;
        }

        if (CACHE_SWITCH.get()) {
            ENTITIES_CACHE.computeIfAbsent(entityClass.getName(), key -> new ConcurrentHashMap<>()).put(name, entity);

        } else if (StringUtils.isNotBlank(System.getProperty(HIBERNATE_ROOT_PATH_PROPERTY_KEY))) {

            final String rootPath = readRootPath();
            try {
                JacksonHelper.serializeToFileWithDateFormat(entity, getFile(entity, rootPath, name));
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error(ExceptionUtils.getStackTrace(e));
                }
                throw new RuntimeExceptionExt(e);
            }
        }
    }

    @NotNull
    private static File getFile(@NotNull final Object entity,
                                @NotNull final String rootPath,
                                @NotNull final String name) throws IOException {

        final String classPath = rootPath.concat(ObjectLoader.FILE_SEPARATOR).concat(entity.getClass().getSimpleName());

        // Проверяем существование директории для класса - ???.root.path/ClassName
        final Path path = Paths.get(classPath);
        if (!Files.exists(path)) {
            // Создаем директорию для класса, если её нет
            Files.createDirectories(path);
        }

        // ???.root.path/ClassName/name.json
        return new File(classPath.concat(ObjectLoader.FILE_SEPARATOR).concat(name));
    }

    private static String readRootPath() {
        final String filesRootPath = System.getProperty(HIBERNATE_ROOT_PATH_PROPERTY_KEY);

        String result = null;
        try {
            if (StringUtils.isNotBlank(filesRootPath)) {
                final Path path = Paths.get(filesRootPath.trim());
                if (path.toFile().exists()) {
                    result = path.toString();
                }
            }
        } catch (RuntimeException e) {
            final String errorMessage =
                    String.format(ERROR_MESSAGE0, filesRootPath, HIBERNATE_FILE_EXT, ExceptionUtils.getStackTrace(e));

            if (log.isErrorEnabled()) {
                log.error(errorMessage);
            }
            throw new RuntimeExceptionExt(errorMessage, e);
        }

        if (result == null) {
            final String errorMessage = String.format(ERROR_MESSAGE1, filesRootPath, HIBERNATE_FILE_EXT);

            if (log.isErrorEnabled()) {
                log.error(errorMessage);
            }
            throw new RuntimeExceptionExt(errorMessage);
        }
        return result;
    }

    /**
     * Включает кэширование записи/обновления сущностей.
     */
    public static synchronized void enableCaching() {
        // Дополнительная синхронизация с методом executeAndGetCachedEntities.
        synchronized (MUTEX) {
            if (log.isInfoEnabled()) {
                log.info(">>> Включение кэширования сущностей из потока: {} <<<", Thread.currentThread().getName());
            }
            if (StringUtils.isBlank(System.getProperty(HIBERNATE_ROOT_PATH_PROPERTY_KEY))) {
                CACHE_SWITCH.getAndSet(true);
            }
        }
    }

    /**
     * Отключает кэширование записи/обновления сущностей.
     */
    public static synchronized void disableCaching() {
        // Дополнительная синхронизация с методом executeAndGetCachedEntities.
        synchronized (MUTEX) {
            if (CACHE_SWITCH.get()) {
                ENTITIES.clear();
                ENTITIES_CACHE.forEach((entityClassName, innerMap) -> innerMap.clear());
                ENTITIES_CACHE.clear();
                CACHE_SWITCH.getAndSet(false);
            }
            if (log.isInfoEnabled()) {
                log.info(">>> Выключение кэширования сущностей из потока: {} <<<", Thread.currentThread().getName());
            }
        }
    }

    /**
     * Возвращает набор сущностей из кэша.
     *
     * @return набор сущностей
     */
    @NotNull
    public static synchronized Set<Object> getCachedEntities() {
        return ENTITIES.stream()
                .map(entityKey -> getEntity(entityKey.findClassByName(), (String) entityKey.getId()))
                .collect(Collectors.toSet());
    }

    /**
     * Исполняет что-то (например, некоторый набор методов для тестирования) и возвращает закэшированные сущности.
     *
     * @param activity исполняемая активность
     * @return набор сущностей
     */
    @NotNull
    public static Set<Object> executeAndGetCachedEntities(final Executable activity) {
        if (activity == null) {
            return Collections.emptySet();
        }

        // Блокируем одновременный запуск данного метода из разных потоков, при этом не задействуем монитор
        // самого класса HibernateEntityManipulator, т.к. он используется для синхронизации чтения/записи.
        synchronized (MUTEX) {
            if (log.isInfoEnabled()) {
                log.info(">>> Запуск executeAndGetCachedEntities из потока: {} <<<", Thread.currentThread().getName());
            }
            try (final CacheWrapper cacheWrapper = new CacheWrapper()) {
                activity.execute();
                return cacheWrapper.getCachedEntities();

            } catch (Throwable e) {
                if (log.isErrorEnabled()) {
                    log.error("Сбой в исполняемой активности: {}", ExceptionUtils.getStackTrace(e));
                }
                // Возвращаем исключение, как RuntimeException
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Класс-обёртка для кэша сущностей - служит для автоматического отключения кэширования.
     */
    public static class CacheWrapper implements AutoCloseable {
        public CacheWrapper() {
            // Включаем кэширование
            enableCaching();
        }

        @Override
        public void close() {
            // Отключаем кэширование
            disableCaching();
        }

        public Set<Object> getCachedEntities() {
            return HibernateEntityManipulator.getCachedEntities();
        }
    }
}
