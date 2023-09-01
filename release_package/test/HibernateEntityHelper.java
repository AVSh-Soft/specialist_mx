package com.sbt.compliance.storage.oracle.utils;

import com.sbt.compliance.exceptions.RuntimeExceptionExt;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sbt.compliance.storage.oracle.utils.EntityKey.createEntityKey;

/**
 * Хелпер для сущностей Hibernate.
 *
 * @author -= AVSh =-
 * Created: 14.11.2019 15:32
 */
@Slf4j
public final class HibernateEntityHelper {
    // Умолчательное поле для id сущности
    public static final String ENTITY_ID_FIELD_NAME = "objectId";

    // Кэши, для ускорения обработки
    private static final ConcurrentMap<String, List<FieldRec>> ENTITIES_GETTER_SETTER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, FieldRec> ID_GETTER_SETTER_CACHE = new ConcurrentHashMap<>();

    private static final String IS = "is";
    private static final String GET = "get";
    private static final String SET = "set";

    private static final String UNINITIALIZED = "Uninitialized";

    private HibernateEntityHelper() {
        //
    }

    /**
     * Выполняет преобразование сущности в строку
     * (сущность может не иметь инициализации).
     *
     * @param entity сущность Hibernate
     * @return строковое представление сущности
     */
    @NotNull
    public static String entityToString(final Object entity) {
        return entityToString(entity, new HashContainer());
    }

    @NotNull
    private static String entityToString(final Object entity, @NotNull final HashContainer container) {
        if (entity == null) {
            return String.valueOf((Object) null);
        }

        if (!Hibernate.isInitialized(entity)) {
            return putInSquareBrackets(entity.getClass().getSimpleName(), UNINITIALIZED);
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        if (!isHibernateEntity(entityClass)) {
            return String.valueOf(entity);
        }

        if (container.contains(entity)) {
            final Object entityId = getEntityId(entity);
            return String.format("backlink->%s[%s=%s]",
                    entityClass.getSimpleName(),
                    getIdFieldName(entityClass),
                    valueOf((entityId != null) ? entityId.getClass() : null, entityId));
        }

        container.add(entity);
        final StringJoiner result = new StringJoiner(", ", entityClass.getSimpleName().concat("["), "]");
        getFieldRecs(entityClass).forEach(fieldRec -> {

            final Class<?> fieldClass = fieldRec.type;
            final Object fieldValue = fieldRec.getter.apply(entity);
            final String prefix = fieldRec.name.concat("=");

            if (fieldRec.isEntity) {
                // Рекурсия ->>
                result.add(prefix.concat(entityToString(fieldValue, container)));

            } else if (fieldRec.isEntityCollection) {

                if (Hibernate.isInitialized(fieldValue)) {
                    if (CollectionUtils.isNotEmpty((Collection<?>) fieldValue)) {
                        result.add(putInSquareBrackets(prefix, ((Collection<?>) fieldValue).stream()
                                // Рекурсия ->>
                                .map(item -> entityToString(item, container))
                                .collect(Collectors.joining(", "))));
                    } else {
                        result.add(putInSquareBrackets(prefix, ""));
                    }
                } else {
                    result.add(putInSquareBrackets(prefix, UNINITIALIZED));
                }
            } else {
                result.add(prefix.concat(valueOf(fieldClass, fieldValue)));
            }

        });
        return result.toString();
    }

    /**
     * Выполняет полное клонирование сущности Hibernate
     * (требуется полная инициализации клонируемой сущности).
     *
     * @param entity       сущность Hibernate, которую необходимо клонировать
     * @param parentEntity родительская сущность, для клонируемой сущности (если её нет, то null)
     * @return полный клон сущности
     */
    public static <T> T cloneEntity(final T entity, final Object parentEntity) {
        if (entity == null) {
            return null;
        }

        checkEntityClass(Hibernate.getClass(entity));
        final EntityContainer container = new EntityContainer();
        container.put(createEntityKey(parentEntity), parentEntity);
        return (T) cloneEntity(entity, container);
    }

    private static Object cloneEntity(final Object entity, @NotNull final EntityContainer container) {
        if (entity == null) {
            return null;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        final EntityKey entityKey = new EntityKey(entityClass, getEntityId(entity));
        final Object prevEntity = container.get(entityKey);
        if (prevEntity != null) {
            return prevEntity;
        }

        final Object result = createEntity(entityClass);
        if (result != null) {
            container.put(entityKey, result);

            getFieldRecs(entityClass).forEach(fieldRec -> {
                final Object fieldValue = fieldRec.getter.apply(entity);
                if (fieldRec.isEntity) {
                    // Рекурсия ->>
                    fieldRec.setter.accept(result, cloneEntity(fieldValue, container));

                } else if (fieldRec.isEntityCollection) {

                    final Stream<Object> resultStream = (fieldValue == null) ? Stream.empty() :
                            ((Collection<?>) fieldValue).stream().filter(Objects::nonNull)
                                    // Рекурсия ->>
                                    .map(item -> cloneEntity(item, container));

                    if (Set.class.isAssignableFrom(fieldRec.type)) {
                        fieldRec.setter.accept(result, resultStream.collect(Collectors.toSet()));
                    } else if (List.class.isAssignableFrom(fieldRec.type)) {
                        fieldRec.setter.accept(result, resultStream.collect(Collectors.toList()));
                    } else {
                        throw new RuntimeExceptionExt("Неизвестный тип коллекции: ".concat(fieldRec.type.getName()));
                    }

                } else if (fieldValue != null) {
                    cloneObject(fieldRec, fieldValue, result);
                }
            });
        }
        return result;
    }

    /**
     * Клонирует объект-источник (не сущность Hibernate) в поле сущности-получателя.
     *
     * @param fieldRec     поле {@link FieldRec} сущности-получателя
     * @param source       объект-источник
     * @param targetEntity сущность-получатель
     */
    public static void cloneObject(@NotNull final FieldRec fieldRec,
                                   @NotNull final Object source,
                                   @NotNull final Object targetEntity) {

        // Здесь требуется класс реализации, а не полиморфный класс поля из класса сущности!
        if (Cloneable.class.isAssignableFrom(source.getClass())) {
            try {
                // Тут, к сожалению, только через рефлексию
                fieldRec.setter.accept(targetEntity, MethodUtils.invokeMethod(source, true, "clone"));
            } catch (Exception e) {
                throw new RuntimeExceptionExt("Ошибка клонирования объекта класса ".concat(fieldRec.type.getName()), e);
            }
        } else if (Set.class.isAssignableFrom(fieldRec.type)) {
            fieldRec.setter.accept(targetEntity, new HashSet<>((Collection<?>) source));
        } else if (List.class.isAssignableFrom(fieldRec.type)) {
            fieldRec.setter.accept(targetEntity, new ArrayList<>((Collection<?>) source));
        } else if (Map.class.isAssignableFrom(fieldRec.type)) {
            fieldRec.setter.accept(targetEntity, new HashMap<>((Map<?, ?>) source));
        } else {
            fieldRec.setter.accept(targetEntity, source);
        }
    }

    /**
     * Копирует данные из одной сущности Hibernate в другую
     * (требуется полная инициализации сущностей).
     *
     * @param fromEntity   сущность-источник
     * @param toEntity     сущность-приемник
     * @param parentEntity родительская сущность, для сущности-источника (если её нет, то null)
     */
    public static <T> void copyEntity(final T fromEntity, final T toEntity, final Object parentEntity) {
        if ((fromEntity == null) || (toEntity == null)) {
            throw new RuntimeExceptionExt("Сущность-источник или сущность-получатель не должны быть пусты!");
        }

        final Class<?> fromEntityClass = Hibernate.getClass(fromEntity);
        final Class<?> toEntityClass = Hibernate.getClass(toEntity);
        checkEntityClass(fromEntityClass);
        checkEntityClass(toEntityClass);

        if (!fromEntityClass.equals(toEntityClass)) {
            throw new RuntimeExceptionExt("Классы сущности-источника и сущности-получателя не равны!");
        }

        final EntityContainer container = new EntityContainer();
        container.put(createEntityKey(parentEntity), parentEntity);
        replicateEntity(fromEntity, toEntity, container);
    }

    private static <T> void replicateEntity(@NotNull final T fromEntity,
                                            @NotNull final T toEntity,
                                            @NotNull final EntityContainer container) {

        container.put(createEntityKey(fromEntity), toEntity);
        getFieldRecs(Hibernate.getClass(fromEntity)).forEach(fieldRec -> {

            final Object fromFieldValue = fieldRec.getter.apply(fromEntity);
            final Object toFieldValue = fieldRec.getter.apply(toEntity);

            if (fieldRec.isEntity) {

                final EntityKey fromEntityKey = createEntityKey(fromFieldValue);
                final Object prevToEntity = container.get(fromEntityKey);
                if (prevToEntity != null) {
                    fieldRec.setter.accept(toEntity, prevToEntity);

                } else if (fromFieldValue != null) {
                    if (toFieldValue != null) {
                        // Рекурсия ->>
                        replicateEntity(fromFieldValue, toFieldValue, container);
                    } else {
                        final Object clonedFromEntity = cloneEntity(fromFieldValue, toEntity);
                        container.put(fromEntityKey, clonedFromEntity);
                        fieldRec.setter.accept(toEntity, clonedFromEntity);
                    }

                } else {
                    fieldRec.setter.accept(toEntity, null);
                }

            } else if (fieldRec.isEntityCollection) {

                final Collection<Object> fromCollection = (Collection<Object>) fromFieldValue;
                final Collection<Object> toCollection = (Collection<Object>) toFieldValue;

                final List<Object> result = CollectionUtils.isEmpty(fromCollection) ? new ArrayList<>() :
                        fromCollection.stream().filter(Objects::nonNull).map(fromItem -> {

                            final EntityKey fromItemKey = createEntityKey(fromItem);
                            final Object prevToItem = container.get(fromItemKey);
                            if (prevToItem != null) {
                                return prevToItem;
                            }

                            final Optional<Object> optToItem = CollectionUtils.isEmpty(toCollection) ? Optional.empty() :
                                    toCollection.stream()
                                            .filter(toItem -> Objects.equals(fromItemKey, createEntityKey(toItem)))
                                            .findAny();
                            if (optToItem.isPresent()) {
                                // Рекурсия ->>
                                replicateEntity(fromItem, optToItem.get(), container);
                                return optToItem.get();
                            }

                            final Object clonedFromItem = cloneEntity(fromItem, toEntity);
                            container.put(fromItemKey, clonedFromItem);
                            return clonedFromItem;

                        }).collect(Collectors.toList());

                if (toCollection != null) {
                    toCollection.clear();
                    toCollection.addAll(result);

                } else if (Set.class.isAssignableFrom(fieldRec.type)) {
                    fieldRec.setter.accept(toEntity, new HashSet<>(result));

                } else if (List.class.isAssignableFrom(fieldRec.type)) {
                    fieldRec.setter.accept(toEntity, result);

                } else {
                    throw new RuntimeExceptionExt("Неизвестный тип коллекции: ".concat(fieldRec.type.getName()));
                }

            } else if (fromFieldValue != null) {
                // Прочие объекты тоже клонируем
                cloneObject(fieldRec, fromFieldValue, toEntity);

            } else {
                fieldRec.setter.accept(toEntity, null);
            }
        });
    }

    /**
     * Выполняет точное сравнение двух сущностей Hibernate
     * (требуется полная инициализация сравниваемых сущностей).
     *
     * @param entityA сущность A
     * @param entityB сущность B
     * @return true - сущности идентичны
     */
    public static boolean areEntitiesEqual(final Object entityA, final Object entityB) {
        return areEntitiesEqual(entityA, entityB, new HashContainer());
    }

    private static boolean areEntitiesEqual(final Object entityA,
                                            final Object entityB,
                                            @NotNull final HashContainer container) {

        // Одинаковые ссылки или null
        if (entityA == entityB) {
            return true;
        }

        if ((entityA == null) || (entityB == null)) {
            return false;
        }

        final Class<?> entityClass = Hibernate.getClass(entityA);
        if (!entityClass.equals(Hibernate.getClass(entityB))) {
            return false;
        }
        checkEntityClass(entityClass);

        // Игнорируем ранее пройденные сущности
        if (container.contains(entityA)) {
            return true;
        }

        container.add(entityA);
        return getFieldRecs(entityClass).stream().allMatch(fieldRec -> {
            final Object fieldValueA = fieldRec.getter.apply(entityA);
            final Object fieldValueB = fieldRec.getter.apply(entityB);

            final boolean result;
            if (fieldRec.isEntity) {
                // Рекурсия ->>
                result = areEntitiesEqual(fieldValueA, fieldValueB, container);
            } else if (fieldRec.isEntityCollection) {
                // Рекурсия ->>
                result = areEntityCollectionsEqual((Collection<?>) fieldValueA, (Collection<?>) fieldValueB, container);
            } else {
                result = Objects.equals(fieldValueA, fieldValueB);
            }

            if (!result) {
                equalsInfo(entityClass, fieldRec.name);
            }
            return result;
        });
    }

    private static boolean areEntityCollectionsEqual(final Collection<?> collectionA,
                                                     final Collection<?> collectionB,
                                                     @NotNull final HashContainer container) {

        if ((collectionA == null) && (collectionB == null)) {
            return true;
        }

        if ((collectionA == null) || (collectionB == null) || (collectionA.size() != collectionB.size())) {
            return false;
        }

        return collectionA.stream().allMatch(itemA -> {
            final Object idA = getEntityId(itemA);
            final Optional<?> optItemB = collectionB.stream()
                    .filter(itemB -> Objects.equals(idA, getEntityId(itemB)))
                    .findAny();
            return optItemB.isPresent() && areEntitiesEqual(itemA, optItemB.get(), container);
        });
    }

    private static void equalsInfo(final Class<?> entityClass, final String fieldName) {
        if (log.isInfoEnabled()) {
            log.info("Сравнение сущностей класса: [{}] -> не равны значения в поле: [{}]", entityClass.getName(), fieldName);
        }
    }

    /**
     * Заменяет в сущности значение в поле id на новое - перегенерация id
     * (Важно: (1) требуется полная инициализация сущности; (2) в случае parentEntity == null
     * выполнится перегененрация id и по обратным ссылкам).
     *
     * @param entity       сущность Hibernate
     * @param parentEntity родительская сущность Hibernate, для сущности-источника (если её нет, то null)
     * @param idGenerator  генератор строковых id
     */
    public static void idRegenerate(final Object entity,
                                    final Object parentEntity,
                                    final Supplier<String> idGenerator) {

        if ((entity != null) && (idGenerator != null) && isHibernateEntity(Hibernate.getClass(entity))) {
            final HashContainer container = new HashContainer();
            if (parentEntity != null) {
                container.add(parentEntity);
            }
            idRegenerate(entity, idGenerator, container);
        }
    }

    private static void idRegenerate(
            final Object entity, @NotNull final Supplier<String> idGenerator, @NotNull final HashContainer container) {

        if ((entity == null) || container.contains(entity)) {
            return;
        }

        container.add(entity);
        final Class<?> entityClass = Hibernate.getClass(entity);
        getFieldRecs(entityClass).stream()
                .filter(fieldRec -> fieldRec.isEntity || fieldRec.isEntityCollection)
                .forEach(fieldRec -> {
                    final Object fieldValue = fieldRec.getter.apply(entity);
                    if (fieldValue != null) {
                        if (fieldRec.isEntity) {
                            // Рекурсия ->>
                            idRegenerate(fieldValue, idGenerator, container);
                        } else {
                            ((Collection<?>) fieldValue).stream()
                                    .filter(Objects::nonNull)
                                    // Рекурсия ->>
                                    .forEach(item -> idRegenerate(item, idGenerator, container));
                        }
                    }
                });

        setEntityId(entity, idRestorer(entityClass, idGenerator.get()));
    }

    /**
     * Возвращает набор id из сущности
     * (Важно: (1) если сущность не полностью инициализированна, то только id из инициализированных частей;
     * (2) в случае parentEntity == null будут взяты id и по обратным ссылкам).
     *
     * @param entity       сущность Hibernate
     * @param parentEntity родительская сущность Hibernate, для сущности-источника (если её нет, то null)
     * @return набор id из сущности
     */
    @NotNull
    public static Set<Object> getEntityIds(final Object entity, final Object parentEntity) {
        final Set<Object> result = new HashSet<>();
        final HashContainer container = new HashContainer();
        if (parentEntity != null) {
            container.add(parentEntity);
        }
        getEntityIds(entity, result, container);
        return result;
    }

    private static void getEntityIds(final Object entity,
                                     @NotNull final Set<Object> ids,
                                     @NotNull final HashContainer container) {

        if ((entity != null) && Hibernate.isInitialized(entity) && !container.contains(entity)) {
            container.add(entity);
            ids.add(getEntityId(entity));

            final Class<?> entityClass = Hibernate.getClass(entity);
            getFieldRecs(entityClass).stream()
                    .filter(fieldRec -> fieldRec.isEntity || fieldRec.isEntityCollection)
                    .forEach(fieldRec -> {
                        final Object fieldValue = fieldRec.getter.apply(entity);
                        if (fieldValue != null) {
                            if (fieldRec.isEntity) {
                                // Рекурсия ->>
                                getEntityIds(fieldValue, ids, container);
                            } else if (Hibernate.isInitialized(fieldValue)) {
                                ((Collection<?>) fieldValue).stream()
                                        .filter(Objects::nonNull)
                                        // Рекурсия ->>
                                        .forEach(item -> getEntityIds(item, ids, container));
                            }
                        }
                    });
        }
    }

    /**
     * Создает сущность Hibernate с установкой id (идентификатора).
     *
     * @param entityClass класс сущности
     * @param id          идентификатор для сущности Hibernate
     * @return сущность Hibernate
     */
    public static <T> T createEntityWithId(final Class<T> entityClass, final Object id) {
        final T result = createEntity(entityClass);
        Optional.ofNullable(result)
                .map(entity -> getFieldRecForId(entityClass))
                .ifPresent(fieldRec -> fieldRec.setter.accept(result, id));
        return result;
    }

    /**
     * Создает сущность Hibernate (в классе сущности должен быть конструктор по умолчанию).
     *
     * @param entityClass класс сущности
     * @return сущность Hibernate
     */
    public static <T> T createEntity(final Class<T> entityClass) {
        if (isHibernateEntity(entityClass)) {
            try {
                return entityClass.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeExceptionExt("Не удалось создать экземпляр класса: ".concat(entityClass.getName()), e);
            }
        }
        return null;
    }

    /**
     * Получает имя поля id сущности.
     *
     * @param entityClass класс сущности
     * @return имя поля id сущности
     */
    @NotNull
    public static String getIdFieldName(final Class<?> entityClass) {
        return Optional.ofNullable(HibernateEntityHelper.getFieldRecForId(entityClass))
                .map(FieldRec::getName)
                .orElse(ENTITY_ID_FIELD_NAME);
    }

    /**
     * Получает строковый id сущности Hibernate (здесь без затрагивания механизма lazy-инициализации).
     *
     * @param rawEntity сущность
     * @return строковый id сущности Hibernate
     */
    public static String getEntityStrId(Object rawEntity) {
        return String.valueOf(getEntityId(rawEntity));
    }

    /**
     * Получает id сущности Hibernate (здесь без затрагивания механизма lazy-инициализации).
     *
     * @param rawEntity сущность
     * @return id сущности Hibernate
     */
    public static Object getEntityId(final Object rawEntity) {
        if (rawEntity instanceof HibernateProxy) {
            return ((HibernateProxy) rawEntity).getHibernateLazyInitializer().getIdentifier();
        }
        return Optional.ofNullable(rawEntity)
                .map(entity -> getFieldRecForId(entity.getClass()))
                .map(fieldRec -> fieldRec.getter.apply(rawEntity))
                .orElse(null);
    }

    /**
     * Устанавливает id (идентификатор) для сущности Hibernate.
     *
     * @param rawEntity сущность
     * @param id        идентификатор для сущности Hibernate
     */
    public static void setEntityId(final Object rawEntity, final Object id) {
        Optional.ofNullable(rawEntity)
                .map(entity -> getFieldRecForId(Hibernate.getClass(entity)))
                .ifPresent(fieldRec -> fieldRec.setter.accept(rawEntity, id));
    }

    /**
     * Восстанавливает id сущности из строкового представления.
     *
     * @param entityClass класс сущности
     * @param entityStrId строковое представление id
     * @return id сущности
     */
    public static Object idRestorer(final Class<?> entityClass, final String entityStrId) {
        if ((entityClass == null) || StringUtils.isBlank(entityStrId)) {
            return null;
        }

        Object result = null;

        final FieldRec idFieldRec = getFieldRecForId(entityClass);
        if (idFieldRec != null) {
            final Class<?> idClass = idFieldRec.type;
            if (String.class.equals(idClass)) {
                result = entityStrId;
            } else if (Long.class.equals(idClass)) {
                result = Long.valueOf(entityStrId);
            } else if (UUID.class.equals(idClass)) {
                result = UUID.fromString(entityStrId);
            }
        }

        if (result == null) {
            throw new RuntimeExceptionExt(String.format(
                    "Не удалось восстановить id сущности из строки [%s] для класса [%s]",
                    entityStrId, entityClass.getName()));
        }
        return result;
    }

    /**
     * Получает поле {@link FieldRec} для id сущности.
     *
     * @param entityClass класс сущности
     * @return поле {@link FieldRec} для id сущности
     */
    public static FieldRec getFieldRecForId(final Class<?> entityClass) {
        return isHibernateEntity(entityClass) ? ID_GETTER_SETTER_CACHE.computeIfAbsent(entityClass.getName(), className -> {

            final String name;
            final String errorMessage = "Не удалось найти поля или методы получения/установки id для класса: ".concat(className);
            final List<Field> fields = FieldUtils.getFieldsListWithAnnotation(entityClass, Id.class);
            if (fields.isEmpty()) {
                final List<Method> methods = MethodUtils.getMethodsListWithAnnotation(entityClass, Id.class);
                if (methods.isEmpty()) {
                    throw new RuntimeExceptionExt(errorMessage);
                }
                final String methodName = methods.get(0).getName();
                name = firstCharToLowerCase(methodName.substring(methodName.startsWith(IS) ? IS.length() : GET.length()));
            } else {
                name = fields.get(0).getName();
            }
            return getFieldRecs(entityClass).stream()
                    .filter(fieldRec -> name.equals(fieldRec.name))
                    .findAny()
                    .orElseThrow(() -> new RuntimeExceptionExt(errorMessage));
        }) : null;
    }

    /**
     * Получает список полей {@link FieldRec}.
     *
     * @param entityClass класс сущности
     * @return список полей {@link FieldRec}
     */
    @NotNull
    public static List<FieldRec> getFieldRecs(final Class<?> entityClass) {
        return isHibernateEntity(entityClass) ? ENTITIES_GETTER_SETTER_CACHE.computeIfAbsent(entityClass.getName(), className -> {

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            // Будем просматривать все публичные методы класса и родительских классов,
            final Set<Method> methods = new HashSet<>(Arrays.asList(entityClass.getMethods()));
            // а также приватные методы
            methods.addAll(Arrays.asList(entityClass.getDeclaredMethods()));

            return methods.stream()
                    .filter(method -> method.getName().startsWith(SET))
                    .map(methodSet -> {

                        final String name = methodSet.getName().substring(SET.length());
                        final String nameIs = IS.concat(name);
                        final String nameGet = GET.concat(name);

                        // Попытка найти getter по имени setter-а
                        final Optional<Method> optMethodGet = methods.stream()
                                .filter(method -> nameIs.equals(method.getName()) || nameGet.equals(method.getName()))
                                .findAny();

                        if (optMethodGet.isPresent()) {

                            final Method methodGet = optMethodGet.get();
                            final Class<?> type = methodGet.getReturnType();
                            final Type genericType = methodGet.getGenericReturnType();

                            return new FieldRec(
                                    firstCharToLowerCase(name),
                                    type,
                                    genericType,
                                    createGetter(lookup, methodGet),
                                    createSetter(lookup, methodSet),
                                    isHibernateEntity(type),
                                    isHibernateEntityCollection(type, genericType));
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
        }) : Collections.emptyList();
    }

    /**
     * Создает getter-функцию для метода.
     *
     * @param lookup    экземпляр MethodHandles.Lookup
     * @param methodGet getter-метод
     * @return getter-функция
     */
    public static Function<Object, Object> createGetter(@NotNull final MethodHandles.Lookup lookup,
                                                        @NotNull final Method methodGet) {
        try {
            final MethodHandle methodHandleGet = lookup.unreflect(methodGet);
            final CallSite site = LambdaMetafactory.metafactory(lookup,
                    "apply",
                    MethodType.methodType(Function.class),
                    // Signature of method Function.apply after type erasure
                    MethodType.methodType(Object.class, Object.class),
                    methodHandleGet,
                    // Actual signature of getter
                    methodHandleGet.type());

            return (Function<Object, Object>) site.getTarget().invokeExact();

        } catch (final Throwable e) {
            if (log.isWarnEnabled()) {
                log.warn("Не удалось построить быструю getter-функцию для метода '{}': {}",
                        methodGet.getName(), ExceptionUtils.getStackTrace(e));
            }

            return object -> {
                try {
                    methodGet.setAccessible(true);
                    return methodGet.invoke(object);

                } catch (Throwable ex) {
                    throw new RuntimeExceptionExt(
                            String.format("Ошибка вызова getter-функции для метода '%s'", methodGet.getName()), ex);
                }
            };
        }
    }

    /**
     * Создает setter-функцию для метода.
     *
     * @param lookup    экземпляр MethodHandles.Lookup
     * @param methodSet setter-метод
     * @return setter-функция
     */
    public static BiConsumer<Object, Object> createSetter(@NotNull final MethodHandles.Lookup lookup,
                                                          @NotNull final Method methodSet) {
        try {
            final MethodHandle methodHandleSet = lookup.unreflect(methodSet);
            final CallSite site = LambdaMetafactory.metafactory(lookup,
                    "accept",
                    MethodType.methodType(BiConsumer.class),
                    // Signature of method BiConsumer.accept after type erasure
                    MethodType.methodType(void.class, Object.class, Object.class),
                    methodHandleSet,
                    // Actual signature of setter
                    methodHandleSet.type());

            return (BiConsumer<Object, Object>) site.getTarget().invokeExact();

        } catch (final Throwable e) {
            if (log.isWarnEnabled()) {
                log.warn("Не удалось построить быструю setter-функцию для метода '{}': {}",
                        methodSet.getName(), ExceptionUtils.getStackTrace(e));
            }

            return (object, data) -> {
                try {
                    methodSet.setAccessible(true);
                    methodSet.invoke(object, data);

                } catch (Throwable ex) {
                    throw new RuntimeExceptionExt(
                            String.format("Ошибка вызова setter-функции для метода '%s'", methodSet.getName()), ex);
                }
            };
        }
    }

    /**
     * Проверяет, что на входе именно класс-сущность Hibernate.
     *
     * @param entityClass класс сущности
     */
    public static void checkEntityClass(final Class<?> entityClass) {
        if (!isHibernateEntity(entityClass)) {
            throw new RuntimeExceptionExt(String.format(
                    "Класс [%s] не является сущностью Hibernate!", entityClass));
        }
    }

    /**
     * Определяет, что поле является коллекцией Hibernate.
     *
     * @param entityClass класс сущности
     * @param genericType {@link Type}
     * @return true - коллекция Hibernate
     */
    public static boolean isHibernateEntityCollection(final Class<?> entityClass, final Type genericType) {
        return (entityClass != null) &&
                Collection.class.isAssignableFrom(entityClass) &&
                isHibernateEntity(getParameterizedType(genericType));
    }

    /**
     * Получает параметризованный тип (класс).
     *
     * @param genericType {@link Type}
     * @return параметризованный тип или null, если {@link Type} непараметризованный
     */
    public static Class<?> getParameterizedType(final Type genericType) {
        if (genericType instanceof ParameterizedType) {
            final Type[] types = ((ParameterizedType) genericType).getActualTypeArguments();
            if ((types != null) && (types.length > 0)) {
                return (Class<?>) types[0];
            }
        }
        return null;
    }

    /**
     * Определяет, что класс является сущностью Hibernate.
     *
     * @param entityClass класс сущности
     * @return true - сущность Hibernate
     */
    public static boolean isHibernateEntity(final Class<?> entityClass) {
        return (entityClass != null) && entityClass.isAnnotationPresent(Entity.class);
    }

    /**
     * Переводит первый символ строки в нижний регистр.
     *
     * @param string строка
     * @return строка с первым символом в нижнем регистре
     */
    private static String firstCharToLowerCase(final String string) {
        if ((string != null) && (string.length() > 0)) {
            final char[] chars = string.toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            return new String(chars);
        }
        return string;
    }

    @NotNull
    private static String valueOf(final Class<?> clazz, final Object value) {
        return String.class.equals(clazz) ? String.format("'%s'", value) : String.valueOf(value);
    }

    @NotNull
    private static String putInSquareBrackets(final String prefix, final String value) {
        return String.format("%s[%s]", StringUtils.isEmpty(prefix) ? "" : prefix, value);
    }

    /**
     * Поле сущности.
     */
    @Getter
    @AllArgsConstructor
    public static class FieldRec {
        private final String name;
        private final Class<?> type;
        private final Type genericType;
        private final Function<Object, Object> getter;
        private final BiConsumer<Object, Object> setter;
        private final boolean isEntity;
        private final boolean isEntityCollection;
    }

    /**
     * Контейнер для сущностей
     * (предотвращает переполнение стека при проходе по полям сущностей).
     */
    public static class EntityContainer {
        private final Map<EntityKey, Object> entityMap = new HashMap<>();

        public void put(final EntityKey entityKey, final Object entity) {
            if ((entityKey != null) && (entity != null)) {
                entityMap.put(entityKey, entity);
            }
        }

        public Object get(final EntityKey entityKey) {
            return (entityKey != null) ? entityMap.get(entityKey) : null;
        }

        public boolean contains(final EntityKey entityKey) {
            return get(entityKey) != null;
        }
    }

    /**
     * Контейнер для чистых (через {@link System#identityHashCode(Object)}) хешей сущностей
     * (предотвращает переполнение стека при проходе по полям сущностей).
     */
    public static class HashContainer {
        private final Set<Integer> hashes = new HashSet<>();

        public void add(final Object entity) {
            hashes.add(System.identityHashCode(entity));
        }

        public boolean contains(final Object entity) {
            return hashes.contains(System.identityHashCode(entity));
        }

        public boolean isEmpty() {
            return hashes.isEmpty();
        }
    }
}
