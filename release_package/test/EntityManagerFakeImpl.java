package com.sbt.compliance.aml.mockup.api;

import com.sbt.compliance.aml.mockup.helpers.object.loader.special.HibernateEntityManipulator;
import com.sbt.compliance.exceptions.RuntimeExceptionExt;
import com.sbt.compliance.storage.api.filter.EntitiesFilter;
import com.sbt.compliance.storage.oracle.utils.EntityKey;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.AssertionFailure;
import org.hibernate.Hibernate;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sbt.compliance.aml.mockup.helpers.object.loader.special.HibernateEntityManipulator.*;
import static com.sbt.compliance.storage.oracle.utils.HibernateEntityHelper.*;
import static com.sbt.compliance.tests.TestHelper.BAD_ID;
import static com.sbt.compliance.utils.StringHelper.firstCharToUpperCase;

/**
 * Упрощенная эмуляция
 * (только некоторые базовые функции, штука не многопоточная, как и настоящий EntityManager, несмотря на синхронизацию).
 *
 * @author -= AVSh =-
 * Created: 29.11.2019 14:54
 */
public class EntityManagerFakeImpl implements EntityManager {
    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final Map<EntityKey, EntityRec> contextBuffer = new HashMap<>();

    private final EntityTransaction entityTransaction = new EntityTransaction() {
        private final AtomicBoolean isActive = new AtomicBoolean(false);

        @Override
        public void begin() {
            isActive.getAndSet(true);
        }

        @Override
        public synchronized void commit() {
            if (isActive.get()) {
                EntityManagerFakeImpl.this.commitMethod();
                isActive.getAndSet(false);
            }
        }

        @Override
        public synchronized void rollback() {
            if (isActive.get()) {
                // Пока не реализованно
                isActive.getAndSet(false);
            }
        }

        @Override
        public void setRollbackOnly() {
            //
        }

        @Override
        public boolean getRollbackOnly() {
            return false;
        }

        @Override
        public boolean isActive() {
            return isActive.get();
        }
    };

    public EntityManagerFakeImpl() {
        //
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // --- Служебные публичные методы ----------------------------------------------------------------------------------

    /**
     * Извлекает сущность из EntityManagerFakeImpl.
     *
     * @param entityClass класс сущности
     * @param entityId    id сущности
     * @return сущность
     */
    public synchronized <T> T findEntity(@NotNull final Class<T> entityClass, final Object entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("id to load is required for loading");
        }
        return (T) findEntityInBuffer(entityClass, entityId);
    }

    /**
     * Помещает сущность в контекст EntityManagerFakeImpl.
     *
     * @param entity сущность
     */
    public synchronized void persistEntity(final Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("attempt to create event with null entity");
        }
        persistEntityToBuffer(entity);
    }

    /**
     * Удаляет сущность из контекста EntityManagerFakeImpl.
     *
     * @param entity сущность
     */
    public synchronized void removeEntity(final Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("attempt to create delete event with null entity");
        }
        removeEntityInBuffer(entity);
    }

    /**
     * Обновляет контекст данными из переданной сущности, и возвращает результат из контекста.
     *
     * @param entity сущность
     * @return результат из контекста
     */
    public synchronized <T> T mergeEntity(final T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("attempt to create merge event with null entity");
        }
        return (T) mergeEntityToBuffer(entity);
    }

    /**
     * Перечитывает сущность.
     *
     * @param entity сущность
     */
    public synchronized void refreshEntity(final Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Attempt to generate refresh event with null object");
        }
        refreshEntityInBuffer(entity);
    }

    /**
     * Отсоединяет сущность от контекста EntityManager.
     *
     * @param entity сущность
     */
    public synchronized void detachEntity(final Object entity) {
        if (entity == null) {
            throw new NullPointerException("null passed to Session.evict()");
        }
        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        detachEntityFromBuffer(entity);
    }

    /**
     * Проверяет, входит ли сущность в контекст EntityManager.
     *
     * @param entity сущность
     * @return true - сущность в контексте EntityManager
     */
    public synchronized boolean containsEntity(final Object entity) {
        if (entity == null) {
            return false;
        }

        val entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityRec = contextBuffer.get(new EntityKey(entityClass, getEntityId(entity)));
        // Тут сравниваем ссылки
        return (entityRec != null) && entityRec.isAttached && (entityRec.entity == entity);
    }

    /**
     * Нормализует состояние буфера, и выполняет проверки.
     */
    public synchronized void flushMethod() {
        // Ищем все главные неудаленные сущности, посущности которых отсутствуют в буфере
        val entities = contextBuffer.entrySet().stream()
                // Обрабатываем только главные сущности
                .filter(entry -> entry.getValue().isMain && !entry.getValue().isRemoved)
                // Получаем через findEntityInBuffer, т.к. это наиболее точное извлечение сущности
                .map(entry -> findEntityInBuffer(entry.getKey().findClassByName(), entry.getKey().getId()))
                .filter(entity -> createEntityMapFromEntity(entity).entrySet().stream().anyMatch(entry -> {
                    val entityRec = contextBuffer.get(entry.getKey());
                    // Тут при проверке сравниваем ссылки
                    return (entityRec == null) || (entityRec.entity != entry.getValue().entity);
                }))
                .collect(Collectors.toList());
        // Синхронизируем найденные сущности с буфером, используя persist,
        // т.к. нужно полностью присоединить "висящие в воздухе" сущности
        entities.forEach(this::persistEntityToBuffer);

        // Проверяем, есть ли внутри измененных главных сущностей не isAttached подсущности
        contextBuffer.entrySet().stream()
                .filter(entry -> entry.getValue().isMain && !entry.getValue().isRemoved)
                .forEach(entry -> {
                    if (!areEntitiesEqual(
                            entry.getValue().entity,
                            getEntityDirectly(entry.getKey().findClassByName(), entry.getKey().getId()))) {

                        createEntityMapFromEntity(entry.getValue().entity)
                                .forEach((entityKey, entityInfoRec) -> {
                                    val entityRec = contextBuffer.get(entityKey);
                                    if (entityRec == null) {
                                        throw new IllegalStateException(String.format(
                                                "В контексте не найдена сущность : [%s#%s]",
                                                entityKey.getClazzName(), entityKey.getId()));
                                    }

                                    if (!entityRec.isAttached && !entityRec.isRemoved) {
                                        throw new TransientPropertyValueException(
                                                "object references an unsaved transient instance - " +
                                                        "save the transient instance before flushing",
                                                entityKey.getClazzName(),
                                                String.valueOf(entityInfoRec.parentClass),
                                                entityInfoRec.parentFieldName);
                                    }
                                });
                    }
                });
    }

    /**
     * Фиксирует контекст в хранилище.
     */
    public synchronized void commitMethod() {
        flushMethod();

        // Обрабатываем буфер
        contextBuffer.entrySet().stream()
                // Обрабатываем только корневые сущности
                .filter(entry -> entry.getValue().isRoot)
                // Сортируем корневые сущности по убыванию количества
                // подсущностей, чтобы уменьшить количество сохранений
                .sorted(Comparator.comparing(
                        entry -> getEntityIds(entry.getValue().entity, null).size(), Comparator.reverseOrder()))
                //.map(AbstractMap.SimpleEntry::getKey)
                .forEach(entry -> {
                    val entityClass = entry.getKey().findClassByName();
                    val entityId = entry.getKey().getId();
                    // Удаляем
                    if (entry.getValue().isRemoved) {
                        removeEntityDirectly(getEntityDirectly(entityClass, entityId));
                    } else {
                        // Сохраняем, если были обнаружены изменения
                        val entity = findEntityInBuffer(entityClass, entityId);
                        if (!areEntitiesEqual(entity, getEntityDirectly(entityClass, entityId))) {
                            saveEntityDirectly(entity);
                        }
                    }
                });
    }

    /**
     * Возвращает отфильтрованные сущности.
     *
     * @param entityClass класс сущности
     * @param filter      фильтр
     * @param filterFunc  фильтрующая функция
     * @return отфильтрованные сущности
     */
    public synchronized <T> List<T> getFilteredEntities(
            final Class<T> entityClass,
            final EntitiesFilter filter,
            final BiFunction<Class<T>, EntitiesFilter, Function<Function<Class<T>, Stream<T>>, List<T>>> filterFunc) {

        if ((entityClass == null) || (filterFunc == null)) {
            return Collections.emptyList();
        }

        // Создаем копию буфера (чтобы в буфер не попали ненужные сущности)
        final Map<EntityKey, EntityRec> copyContextBuffer = new HashMap<>();
        contextBuffer.forEach((entityKey, entityRec) -> copyContextBuffer.put(entityKey, new EntityRec(entityRec)));
        // Фильтруем сущности
        val filteredEntities = filterFunc.apply(entityClass, filter)
                .apply(clazz -> getAllEntityIds(clazz).stream().map(entityId -> findEntity(clazz, entityId)));
        // Восстанавливаем буфер
        contextBuffer.clear();
        contextBuffer.putAll(copyContextBuffer);
        // Перечитываем сущности, чтобы добавить отфильтрованные в восстановленный контекст
        return filteredEntities.stream()
                .map(entity -> findEntity((Class<T>) Hibernate.getClass(entity), getEntityId(entity)))
                .collect(Collectors.toList());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // --- Служебные закрытые методы -----------------------------------------------------------------------------------
    private Object findEntityInBuffer(final Class<?> entityClass, final Object entityId) {
        return findEntityInBuffer(entityClass, entityId, null, null, new HashContainer());
    }

    private Object findEntityInBuffer(final Class<?> entityClass,
                                      final Object entityId,
                                      final Object currentEntity,
                                      final Boolean isStoredEntity,
                                      @NotNull final HashContainer container) {

        checkEntityClass(entityClass);
        if (entityId == null) {
            return null;
        }

        final Object result;
        final Map<String, Set<EntityKey>> childKeyMap;

        val isStored = new AtomicBoolean();
        val entityKey = new EntityKey(entityClass, entityId);
        EntityRec entityRec = contextBuffer.get(entityKey);
        // Первая итерация
        if (container.isEmpty()) {
            // Сущности нет в контексте
            if (entityRec == null) {
                // Пытаемся получить из БД
                val entity = getEntityDirectly(entityClass, entityId);
                // В БД нет - выходим
                if (entity == null) {
                    return null;
                }
                // В БД есть - устанавливаем флаг и засовываем в контекст, как главную/корневую
                isStored.set(true);
                entityRec = new EntityRec(true, true, true, false, entity);
                contextBuffer.put(entityKey, entityRec);

                // Есть в контексте, но удалена - выходим
            } else if (entityRec.isRemoved) {
                return null;
            }
            result = entityRec.entity;
            childKeyMap = entityRec.childKeyMap;

            // Последующие итерации
        } else {
            // На предыдущей итерации сущность была получена из БД
            if (Boolean.TRUE.equals(isStoredEntity)) {
                // Получена из БД, но нет в контексте
                if (entityRec == null) {
                    // Устанавливаем флаг и засовываем в контекст, как подчиненную
                    isStored.set(true);
                    entityRec = new EntityRec(false, false, true, false, currentEntity);
                    contextBuffer.put(entityKey, entityRec);

                    // Есть в контексте
                } else {
                    // Но удалена - выходим
                    if (entityRec.isRemoved) {
                        return null;
                    }
                    // Помечаем, как подчиненную
                    entityRec.isMain = false;
                }
                result = entityRec.entity;
                childKeyMap = entityRec.childKeyMap;

                // На предыдущей итерации не была получена из БД, но в контексте был найден нужный экземпляр
            } else if ((entityRec != null) && (entityRec.entity == currentEntity)) {
                // Удалена - выходим
                if (entityRec.isRemoved) {
                    return null;
                }
                // Помечаем, как подчиненную
                entityRec.isMain = false;
                result = entityRec.entity;
                childKeyMap = entityRec.childKeyMap;

                // "Висит в воздухе" (не закреплена в контексте) - не помещаем в контекст
            } else {
                result = currentEntity;
                childKeyMap = new HashMap<>();
            }
        }

        if (result == null) {
            throw new NullPointerException(String.format("Ошибка при выполнении find для: [%s#%s]", entityClass, entityId));
        }

        // Пройденные ранее повторно не проходим
        if (container.contains(result)) {
            return result;
        }

        container.add(result);
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                // Только не пустые подсущности
                .filter(fieldRec -> fieldRec.getGetter().apply(result) != null)
                .forEach(fieldRec -> {

                    val fieldValue = fieldRec.getGetter().apply(result);
                    if (fieldRec.isEntity()) {
                        val childId = getEntityId(fieldValue);
                        childKeyMap.computeIfAbsent(fieldRec.getName(), fieldName -> new HashSet<>())
                                .add(new EntityKey(fieldRec.getType(), childId));
                        // Рекурсия ->>
                        fieldRec.getSetter().accept(result, findEntityInBuffer(
                                fieldRec.getType(), childId, fieldValue, isStored.get(), container));

                    } else if (fieldRec.isEntityCollection()) {
                        val childKeys =
                                childKeyMap.computeIfAbsent(fieldRec.getName(), fieldName -> new HashSet<>());
                        val collection = (Collection<Object>) fieldValue;
                        val newCollection = collection.stream()
                                .filter(Objects::nonNull)
                                .map(item -> {
                                    val childClass = Hibernate.getClass(item);
                                    val childId = getEntityId(item);
                                    childKeys.add(new EntityKey(childClass, childId));
                                    // Рекурсия ->>
                                    return findEntityInBuffer(childClass, childId, item, isStored.get(), container);
                                })
                                .collect(Collectors.toList());

                        collection.clear();
                        collection.addAll(newCollection);
                    }
                });
        return result;
    }

    private void persistEntityToBuffer(final Object entity) {
        persistEntityToBuffer(entity, null, null, null, new HashContainer());
    }

    private void persistEntityToBuffer(final Object entity,
                                       final Class<?> parentClass,
                                       final String parentFieldName,
                                       final Object parentId,
                                       @NotNull final HashContainer container) {

        if ((entity == null) || container.contains(entity)) {
            return;
        }

        // Выполняем разные проверки
        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        if (entityKey.getId() == null) {
            throwExceptionIds(entityClass);
        }

        var entityRec = contextBuffer.get(entityKey);
        // Нет в буфере
        if (entityRec == null) {
            entityRec = new EntityRec();
            entityRec.isMain = parentClass == null;
            entityRec.isRoot = parentClass == null;
            contextBuffer.put(entityKey, entityRec);

        } else {
            // Если уже имеется другой экземпляр (тут сравниваем ссылки)
            if (entityRec.isAttached && (entityRec.entity != entity)) {
                throw new EntityExistsException(String.format(
                        "A different object with the same identifier value was already associated with the session : [%s#%s]",
                        entityKey.getClazzName(), entityKey.getId()));
            }
            // Если уже имеется другой удаленный экземпляр (тут сравниваем ссылки)
            if (entityRec.isRemoved && (entityRec.entity != entity)) {
                throw new EntityNotFoundException(String.format(
                        "deleted object would be re-saved by cascade (remove deleted object from associations): [%s#%s]",
                        entityKey.getClazzName(), entityKey.getId()));
            }
            if (entityRec.isMain && (parentClass != null)) {
                entityRec.isMain = false;
            }
            if (entityRec.isRemoved) {
                entityRec.isRemoved = false;
            }
        }

        if (!entityRec.isAttached) {
            // Тут сразу вызываем ошибку, не дожидаясь commit
            // (здесь вынужденно отличается от Hibernate из-за более простой реализации)
            if (getEntityDirectly(entityClass, entityKey.getId()) != null) {
                throw new ConstraintViolationException(
                        String.format("ORA-00001: нарушено ограничение уникальности для: [%s#%s]",
                                entityClass, entityKey.getId()), null, null);
            }

            // isAttached меняем только у тех сущностей, у которых он не был установлен
            if ((parentClass == null) || isCascadeTypeRequired(parentClass, parentFieldName, CascadeType.PERSIST)) {
                entityRec.isAttached = true;
            }
        }

        // Запоминаем ссылку
        container.add(entity);
        entityRec.entity = entity;
        val childKeyMap = entityRec.childKeyMap;
        // Проходим по подсущностям
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                .forEach(fieldRec -> {
                    val fieldValue = fieldRec.getGetter().apply(entity);
                    if (fieldRec.isEntity()) {
                        if (fieldValue != null) {
                            // Рекурсия ->>
                            persistEntityToBuffer(fieldValue, entityClass, fieldRec.getName(), entityKey.getId(), container);
                        }
                        handleOrphans(childKeyMap, entityClass, fieldRec.getName(),
                                createSingletonEntityKey(fieldRec.getType(), getEntityId(fieldValue)));

                    } else if (fieldRec.isEntityCollection()) {
                        val parentEntity = checkAndGetParentEntity(
                                parentClass, parentFieldName, parentId,
                                getParameterizedType(fieldRec.getGenericType()),
                                getMappedFieldName(entityClass, fieldRec.getName()));

                        var collection = (Collection<Object>) fieldValue;
                        if (parentEntity != null) {
                            if (collection == null) {
                                final Class<?> collectionClass = fieldRec.getType();
                                if (Set.class.isAssignableFrom(collectionClass)) {
                                    collection = new HashSet<>(Collections.singleton(parentEntity));
                                    fieldRec.getSetter().accept(entity, collection);

                                } else if (List.class.isAssignableFrom(collectionClass)) {
                                    collection = new ArrayList<>(Collections.singleton(parentEntity));
                                    fieldRec.getSetter().accept(entity, collection);
                                }

                            } else if (!collection.contains(parentEntity)) {
                                collection.add(parentEntity);
                            }
                        }

                        final Set<EntityKey> childKeys = new HashSet<>();
                        if (CollectionUtils.isNotEmpty(collection)) {
                            collection.forEach(item -> {
                                if (item != null) {
                                    childKeys.add(new EntityKey(Hibernate.getClass(item), getEntityId(item)));
                                    // Рекурсия ->>
                                    persistEntityToBuffer(item, entityClass, fieldRec.getName(), entityKey.getId(), container);
                                }
                            });
                        }
                        handleOrphans(childKeyMap, entityClass, fieldRec.getName(), childKeys);
                    }
                });
    }

    private void removeEntityInBuffer(final Object entity) {
        removeEntityInBuffer(entity, new HashContainer());
    }

    private void removeEntityInBuffer(final Object entity, @NotNull final HashContainer container) {
        if ((entity == null) || container.contains(entity)) {
            return;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        if (entityKey.getId() == null) {
            return;
        }

        val entityRec = contextBuffer.get(entityKey);
        // Если сущность не под управлением EntityManager, то ничего не делаем
        if ((entityRec == null) || !entityRec.isAttached) {
            return;
        }

        // Удаляемую сущность делаем корневой
        entityRec.isRoot = container.isEmpty();
        entityRec.isRemoved = true;
        entityRec.isAttached = false;

        container.add(entity);
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                // Обрабатываем не пустые подсущности
                .filter(fieldRec -> (fieldRec.getGetter().apply(entity) != null))
                .forEach(fieldRec -> {
                    val fieldValue = fieldRec.getGetter().apply(entity);
                    if (fieldRec.isEntity()) {
                        if (isCascadeRemovalNeeded(entityClass, fieldRec.getName())) {
                            // Рекурсия ->>
                            removeEntityInBuffer(fieldValue, container);
                        } else {
                            makeEntityMainAndRoot(fieldValue);
                        }

                    } else if (fieldRec.isEntityCollection()) {
                        ((Collection<?>) fieldValue).stream()
                                .filter(Objects::nonNull)
                                // Рекурсия ->>
                                .forEach(item -> {
                                    if (isCascadeRemovalNeeded(entityClass, fieldRec.getName())) {
                                        // Рекурсия ->>
                                        removeEntityInBuffer(item, container);
                                    } else {
                                        makeEntityMainAndRoot(item);
                                    }
                                });
                    }
                });
    }

    private Object mergeEntityToBuffer(final Object entity) {
        return mergeEntityToBuffer(entity, null, null, null, new HashContainer());
    }

    private Object mergeEntityToBuffer(final Object entity,
                                       final Class<?> parentClass,
                                       final String parentFieldName,
                                       final Object parentId,
                                       @NotNull final HashContainer container) {

        if (entity == null) {
            return null;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        if (entityKey.getId() == null) {
            throwExceptionIds(entityClass);
        }

        var entityRec = contextBuffer.get(entityKey);
        if (entityRec == null) {
            // Вызываем для наполнения childKeyMap
            findEntity(entityClass, entityKey.getId());
            entityRec = contextBuffer.get(entityKey);
        }

        // Нет в буфере
        if (entityRec == null) {
            entityRec = new EntityRec();
            entityRec.isMain = parentClass == null;
            entityRec.isRoot = parentClass == null;
            entityRec.entity = createEntity(entityClass);
            contextBuffer.put(entityKey, entityRec);

        } else {
            // Если уже имеется удаленный экземпляр
            if (entityRec.isRemoved) {
                throw new EntityNotFoundException(String.format(
                        "Unable to find %s with id %s", entityClass, entityKey.getId()));
            }
            if (entityRec.isMain && (parentClass != null)) {
                entityRec.isMain = false;
            }
        }

        // isAttached меняем только у тех сущностей, у которых он не был установлен
        if (!entityRec.isAttached && ((parentClass == null) || isCascadeTypeRequired(parentClass, parentFieldName, CascadeType.MERGE))) {
            entityRec.isAttached = true;
        }

        val toEntity = entityRec.entity;
        if (toEntity == null) {
            throw new NullPointerException(String.format(
                    "Ошибка при выполнении merge для: [%s#%s]", entityClass, entityKey.getId()));
        }

        // Пройденные ранее повторно не проходим
        if (container.contains(toEntity)) {
            return toEntity;
        }

        container.add(toEntity);
        val childKeyMap = entityRec.childKeyMap;
        getFieldRecs(entityClass).forEach(fieldRec -> {
            val fieldValue = fieldRec.getGetter().apply(entity);
            if (fieldRec.isEntity()) {
                fieldRec.getSetter().accept(toEntity, (fieldValue == null) ? null :
                        // Рекурсия ->>
                        mergeEntityToBuffer(fieldValue, entityClass, fieldRec.getName(), entityKey.getId(), container));

                handleOrphans(childKeyMap, entityClass, fieldRec.getName(),
                        createSingletonEntityKey(fieldRec.getType(), getEntityId(fieldValue)));

            } else if (fieldRec.isEntityCollection()) {
                val fromCollection = (Collection<Object>) fieldValue;
                val toCollection = (Collection<Object>) fieldRec.getGetter().apply(toEntity);
                val parentEntity = checkAndGetParentEntity(
                        parentClass, parentFieldName, parentId,
                        getParameterizedType(fieldRec.getGenericType()),
                        getMappedFieldName(entityClass, fieldRec.getName()));

                final Set<EntityKey> childKeys = new HashSet<>();
                final List<Object> result = CollectionUtils.isEmpty(fromCollection) ? new ArrayList<>() :
                        fromCollection.stream().filter(Objects::nonNull)
                                // Рекурсия ->>
                                .map(fromItem -> {
                                    childKeys.add(new EntityKey(Hibernate.getClass(fromItem), getEntityId(fromItem)));
                                    return mergeEntityToBuffer(fromItem, entityClass, fieldRec.getName(), entityKey.getId(), container);
                                })
                                .collect(Collectors.toList());

                if ((parentEntity != null) && !result.contains(parentEntity)) {
                    childKeys.add(new EntityKey(parentClass, parentId));
                    result.add(parentEntity);
                }

                if (toCollection != null) {
                    toCollection.clear();
                    toCollection.addAll(result);

                } else if (Set.class.isAssignableFrom(fieldRec.getType())) {
                    fieldRec.getSetter().accept(toEntity, new HashSet<>(result));

                } else if (List.class.isAssignableFrom(fieldRec.getType())) {
                    fieldRec.getSetter().accept(toEntity, result);

                } else {
                    throw new RuntimeExceptionExt("Неизвестный тип коллекции: ".concat(fieldRec.getType().getName()));
                }
                handleOrphans(childKeyMap, entityClass, fieldRec.getName(), childKeys);

            } else if (fieldValue != null) {
                // Прочие объекты клонируем
                cloneObject(fieldRec, fieldValue, toEntity);

            } else {
                fieldRec.getSetter().accept(toEntity, null);
            }
        });
        return toEntity;
    }

    private void refreshEntityInBuffer(final Object entity) {
        val container = new HashContainer();
        val entityMap = createEntityMapFromEntity(entity);

        // Обрабатываем главную сущность
        entityMap.values().stream()
                .filter(infoRec -> infoRec.parentClass == null)
                .findAny()
                .ifPresent(infoRec -> refreshEntityFragment(infoRec.entity, null, true, container));

        // Обрабатываем другие дочерние сущности не связанные с главной (refresh такое позволяет)
        entityMap.values().stream()
                .filter(infoRec -> infoRec.parentClass != null)
                .filter(infoRec -> isCascadeTypeRequired(infoRec.parentClass, infoRec.parentFieldName, CascadeType.REFRESH))
                .forEach(infoRec -> {
                    // Это условие не добавляем в фильтр, поскольку container изменяется
                    if (!container.contains(infoRec.entity)) {
                        refreshEntityFragment(infoRec.entity, null, false, container);
                    }
                });
    }

    private Object refreshEntityFragment(final Object entity,
                                         final Class<?> parentClass,
                                         final boolean isRootFragment,
                                         @NotNull final HashContainer container) {

        if (entity == null) {
            return null;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        if (entityKey.getId() == null) {
            throw new AssertionFailure("null identifier");
        }

        var entityRec = contextBuffer.get(entityKey);
        val fromEntity = (parentClass != null) ? entity : getEntityDirectlyWithCheck(entityKey,
                (entityRec != null) ? "this instance does not yet exist as a row in the database" : null);

        final Object toEntity;
        // Нет в буфере
        if (entityRec == null) {
            toEntity = (parentClass != null) ? fromEntity : entity;
            entityRec = new EntityRec(parentClass == null, parentClass == null, true, false, toEntity);
            contextBuffer.put(entityKey, entityRec);

        } else {
            // Если перечитываемая сущность уже имеется в контексте и передан отличный
            // от контекста экземпляр, то ничего не делаем (как в Hibernate)
            // (здесь сравниваем непосредственно ссылки)
            if ((parentClass == null) && (entityRec.entity != entity) && isRootFragment) {
                System.out.printf(
                        "<<< Обратите внимание на то, что в данном случае refresh не сработал для: [%s#%s] >>>%n",
                        entityKey.getClazzName(), entityKey.getId());
                return null;
            }
            if (entityRec.isRemoved) {
                throw new PersistenceException("instance was not in a valid state");
            }
            // Тут заполняем флаги isMain и isRoot одинаково
            if (entityRec.isMain && (parentClass != null)) {
                entityRec.isMain = false;
            }
            if (entityRec.isRoot && (parentClass != null)) {
                entityRec.isRoot = false;
            }
            entityRec.isAttached = true;
            toEntity = entityRec.entity;
        }

        // Пройденные ранее повторно не проходим
        if (container.contains(toEntity)) {
            return toEntity;
        }

        container.add(toEntity);
        val childKeyMap = entityRec.childKeyMap;
        getFieldRecs(entityClass).forEach(fieldRec -> {
            final Object fieldValue = fieldRec.getGetter().apply(fromEntity);
            if (fieldRec.isEntity()) {
                if (fieldValue != null) {
                    childKeyMap.computeIfAbsent(fieldRec.getName(), fieldName -> new HashSet<>())
                            .add(new EntityKey(fieldRec.getType(), getEntityId(fieldValue)));
                    fieldRec.getSetter().accept(toEntity,
                            // Рекурсия ->>
                            refreshEntityFragment(fieldValue, entityClass, isRootFragment, container));
                } else {
                    fieldRec.getSetter().accept(toEntity, null);
                }

            } else if (fieldRec.isEntityCollection()) {
                val childKeys = childKeyMap.computeIfAbsent(fieldRec.getName(), fieldName -> new HashSet<>());
                val resultStream = (fieldValue == null) ? Stream.empty() :
                        ((Collection<?>) fieldValue).stream().filter(Objects::nonNull).map(item -> {
                            childKeys.add(new EntityKey(Hibernate.getClass(item), getEntityId(item)));
                            // Рекурсия ->>
                            return refreshEntityFragment(item, entityClass, isRootFragment, container);
                        });

                if (Set.class.isAssignableFrom(fieldRec.getType())) {
                    fieldRec.getSetter().accept(toEntity, resultStream.collect(Collectors.toSet()));
                } else if (List.class.isAssignableFrom(fieldRec.getType())) {
                    fieldRec.getSetter().accept(toEntity, resultStream.collect(Collectors.toList()));
                } else {
                    throw new RuntimeExceptionExt("Неизвестный тип коллекции: ".concat(fieldRec.getType().getName()));
                }

            } else if (fieldValue != null) {
                if (fromEntity != toEntity) {
                    cloneObject(fieldRec, fieldValue, toEntity);
                }

            } else {
                fieldRec.getSetter().accept(toEntity, null);
            }
        });
        return toEntity;
    }

    private void detachEntityFromBuffer(final Object entity) {
        detachEntityFromBuffer(entity, null, null, new HashContainer());
    }

    private void detachEntityFromBuffer(final Object entity,
                                        final Class<?> parentClass,
                                        final String parentFieldName,
                                        @NotNull final HashContainer container) {

        if ((entity == null) || container.contains(entity)) {
            return;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        var entityRec = contextBuffer.get(entityKey);
        // Не идем дальше: если сущности нет буфере
        if ((entityRec == null)
                // или если встретили первую отсоединенную сущность
                || !entityRec.isAttached
                // или если каскад DETACH не распространяется дальше
                || ((parentClass != null) && !isCascadeTypeRequired(parentClass, parentFieldName, CascadeType.DETACH))) {
            return;
        }

        // Отсоединяем сущность
        entityRec.isAttached = false;

        container.add(entity);
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                // Пустые подсущности пропускаем
                .filter(fieldRec -> fieldRec.getGetter().apply(entity) != null)
                .forEach(fieldRec -> {
                    val fieldValue = fieldRec.getGetter().apply(entity);
                    if (fieldRec.isEntity()) {
                        // Рекурсия ->>
                        detachEntityFromBuffer(fieldValue, entityClass, fieldRec.getName(), container);

                    } else if (fieldRec.isEntityCollection()) {
                        ((Collection<?>) fieldValue).stream()
                                .filter(Objects::nonNull)
                                // Рекурсия ->>
                                .forEach(item -> detachEntityFromBuffer(item, entityClass, fieldRec.getName(), container));
                    }
                });
    }

    @Nullable
    private Set<EntityKey> createSingletonEntityKey(final Class<?> clazz, final Object id) {
        if ((clazz != null) && (id != null)) {
            final Set<EntityKey> result = new HashSet<>();
            result.add(new EntityKey(clazz, id));
            return result;
        }
        return null;
    }

    @NotNull
    private Map<EntityKey, EntityInfoRec> createEntityMapFromEntity(final Object entity) {
        final Map<EntityKey, EntityInfoRec> result = new HashMap<>();
        fillEntityMap(entity, null, null, result);
        return result;
    }

    private void fillEntityMap(final Object entity,
                               final Class<?> parentClass,
                               final String parentFieldName,
                               @NotNull final Map<EntityKey, EntityInfoRec> entityMap) {

        if (entity == null) {
            return;
        }

        final Class<?> entityClass = Hibernate.getClass(entity);
        checkEntityClass(entityClass);

        val entityKey = new EntityKey(entityClass, getEntityId(entity));
        if (entityMap.get(entityKey) != null) {
            return;
        }

        entityMap.put(entityKey, new EntityInfoRec(parentClass, parentFieldName, entity));
        getFieldRecs(entityClass).stream()
                // Только поля - сущности
                .filter(fieldRec -> fieldRec.isEntity() || fieldRec.isEntityCollection())
                // Пустые подсущности пропускаем
                .filter(fieldRec -> fieldRec.getGetter().apply(entity) != null)
                .forEach(fieldRec -> {
                    val fieldValue = fieldRec.getGetter().apply(entity);
                    if (fieldRec.isEntity()) {
                        // Рекурсия ->>
                        fillEntityMap(fieldValue, entityClass, fieldRec.getName(), entityMap);

                    } else if (fieldRec.isEntityCollection()) {
                        ((Collection<?>) fieldValue).stream()
                                .filter(Objects::nonNull)
                                // Рекурсия ->>
                                .forEach(item -> fillEntityMap(item, entityClass, fieldRec.getName(), entityMap));
                    }
                });
    }

    private boolean isOrphanRemoval(final Class<?> entityClass, final String fieldName) {
        if ((entityClass != null) && (fieldName != null)) {
            try {
                // Если аннотации расставлены над полями
                var optOrphanRemoval = getOrphanRemoval(entityClass.getDeclaredField(fieldName));
                if (!optOrphanRemoval.isPresent()) {
                    // Если аннотации расставлены над геттерами
                    optOrphanRemoval = getOrphanRemoval(
                            entityClass.getDeclaredMethod("get".concat(firstCharToUpperCase(fieldName))));
                }
                return optOrphanRemoval.orElse(false);

            } catch (ReflectiveOperationException e) {
                //
            }
        }
        return false;
    }

    private Optional<Boolean> getOrphanRemoval(@NotNull final AccessibleObject accessibleObject) {
        var result = Optional.ofNullable(accessibleObject.getDeclaredAnnotation(OneToOne.class))
                .map(OneToOne::orphanRemoval);
        if (!result.isPresent()) {
            result = Optional.ofNullable(accessibleObject.getDeclaredAnnotation(OneToMany.class))
                    .map(OneToMany::orphanRemoval);
        }
        return result;
    }

    private void handleOrphans(@NotNull final Map<String, Set<EntityKey>> childKeyMap,
                               @NotNull final Class<?> entityClass,
                               @NotNull final String fieldName,
                               final Set<EntityKey> childKeys) {

        childKeyMap.compute(fieldName, (name, prevChildKeys) -> {
            if (CollectionUtils.isNotEmpty(prevChildKeys) && isOrphanRemoval(entityClass, fieldName)) {

                if (CollectionUtils.isNotEmpty(childKeys)) {
                    prevChildKeys.removeAll(childKeys);
                }

                prevChildKeys.forEach(key -> {
                    val entityRec = contextBuffer.get(key);
                    if (entityRec != null) {
                        removeEntityInBuffer(entityRec.entity);
                    }
                });
            }
            return childKeys;
        });
    }

    private void makeEntityMainAndRoot(@NotNull final Object entity) {
        val entityRec = contextBuffer.get(new EntityKey(Hibernate.getClass(entity), getEntityId(entity)));
        if (entityRec != null) {
            entityRec.isMain = true;
            entityRec.isRoot = true;
        }
    }

    private void makeEntityRoot(@NotNull final Object entity) {
        val entityRec = contextBuffer.get(new EntityKey(Hibernate.getClass(entity), getEntityId(entity)));
        if (entityRec != null) {
            entityRec.isRoot = true;
        }
    }

    @NotNull
    private Set<Object> getAllEntityIds(@NotNull final Class<?> entityClass) {
        final Set<Object> result = contextBuffer.entrySet().stream()
                .filter(entry -> entityClass.equals(entry.getKey().findClassByName()) && !entry.getValue().isRemoved)
                .map(entry -> entry.getKey().getId())
                .collect(Collectors.toSet());

        HibernateEntityManipulator.getNames(entityClass, "*").stream()
                .map(OBJECT_LOADER::getNameWithoutExt)
                .map(strId -> idRestorer(entityClass, strId))
                .forEach(result::add);

        return result;
    }

    private void throwExceptionIds(final Class<?> entityClass) {
        throw new PersistenceException(String.format(
                "ids for this class must be manually assigned before calling save(): %s", entityClass));
    }

    @NotNull
    private Object getEntityDirectlyWithCheck(@NotNull final EntityKey entityKey, final String message) {
        val result = getEntityDirectly(entityKey.findClassByName(), entityKey.getId());
        if (result == null) {
            throw new EntityNotFoundException(String.format("No row with the given identifier exists: [%s#%s]",
                    StringUtils.isEmpty(message) ? entityKey.getClazzName() : message, entityKey.getId()));
        }
        return result;
    }

    private <T> T getEntityDirectly(final Class<T> entityClass, final Object entityId) {
        return HibernateEntityManipulator.getEntity(entityClass, String.valueOf(entityId));
    }

    private void saveEntityDirectly(final Object entity) {
        HibernateEntityManipulator.saveEntity(entity);
    }

    private void removeEntityDirectly(final Object entity) {
        HibernateEntityManipulator.removeEntity(entity);
    }

    private void checkEntityClass(final Class<?> entityClass) {
        if (!isHibernateEntity(entityClass)) {
            throw new IllegalArgumentException(String.format("Not an entity [%s]", entityClass));
        }
    }

    private void checkEntityManagerAndSimulateException(final Object entity, final Object entityId) {
        if (!isOpen()) {
            throw new IllegalStateException("Session/EntityManager is closed");
        }

        if (BAD_ID.equals(getEntityStrId(entity)) || BAD_ID.equals(String.valueOf(entityId))) {
            throw new RuntimeExceptionExt("Обнаружена сущность с плохим id!");
        }
    }

    @Nullable
    private Object checkAndGetParentEntity(final Class<?> parentClass,
                                           final String parentFieldName,
                                           final Object parentId,
                                           final Class<?> itemClass,
                                           final String mappedFieldName) {

        Object result = null;
        if (ObjectUtils.allNotNull(parentClass, parentFieldName, parentId) &&
                parentClass.equals(itemClass) &&
                parentFieldName.equals(mappedFieldName)) {

            result = Optional.ofNullable(contextBuffer.get(new EntityKey(parentClass, parentId)))
                    .map(rec -> rec.entity)
                    .orElse(null);
        }
        return result;
    }

    @Nullable
    public static String getMappedFieldName(final Class<?> entityClass, final String fieldName) {
        if ((entityClass != null) && (fieldName != null)) {
            try {
                // Если аннотации расставлены над полями
                final Field field = entityClass.getDeclaredField(fieldName);
                String result = getMappedFieldName(field);
                // Если аннотации расставлены над геттерами
                if (StringUtils.isEmpty(result)) {
                    final Method method = entityClass.getDeclaredMethod("get".concat(firstCharToUpperCase(fieldName)));
                    result = getMappedFieldName(method);
                }
                return result;

            } catch (ReflectiveOperationException e) {
                //
            }
        }
        return null;
    }

    @Nullable
    private static String getMappedFieldName(@NotNull final AccessibleObject accessibleObject) {
        return Optional.ofNullable(accessibleObject.getDeclaredAnnotation(OneToMany.class))
                .map(OneToMany::mappedBy)
                .orElseGet(() -> Optional.ofNullable(accessibleObject.getDeclaredAnnotation(ManyToMany.class))
                        .map(ManyToMany::mappedBy)
                        .orElse(null));
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public synchronized void persist(Object entity) {
        checkEntityManagerAndSimulateException(entity, null);
        persistEntity(entity);
        if (!entityTransaction.isActive()) {
            commitMethod();
        }
    }

    @Override
    public synchronized <T> T merge(T entity) {
        checkEntityManagerAndSimulateException(entity, null);
        final T result = mergeEntity(entity);
        if (!entityTransaction.isActive()) {
            commitMethod();
        }
        return result;
    }

    @Override
    public synchronized void remove(Object entity) {
        checkEntityManagerAndSimulateException(entity, null);
        removeEntity(entity);
        if (!entityTransaction.isActive()) {
            commitMethod();
        }
    }

    @Override
    public synchronized <T> T find(Class<T> entityClass, Object primaryKey) {
        checkEntityManagerAndSimulateException(null, primaryKey);
        return findEntity(entityClass, primaryKey);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
        return find(entityClass, primaryKey);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
        return find(entityClass, primaryKey);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
        return find(entityClass, primaryKey);
    }

    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        final T result = find(entityClass, primaryKey);
        if (result == null) {
            throw new EntityNotFoundException(String.format(
                    "Unable to find %s with id %s", entityClass, primaryKey));
        }
        return result;
    }

    @Override
    public void flush() {
        checkEntityManagerAndSimulateException(null, null);
        flushMethod();
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
        //
    }

    @Override
    public FlushModeType getFlushMode() {
        return null;
    }

    @Override
    public void lock(Object entity, LockModeType lockMode) {
        //
    }

    @Override
    public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        //
    }

    @Override
    public synchronized void refresh(Object entity) {
        checkEntityManagerAndSimulateException(entity, null);
        refreshEntity(entity);
    }

    @Override
    public void refresh(Object entity, Map<String, Object> properties) {
        refresh(entity);
    }

    @Override
    public void refresh(Object entity, LockModeType lockMode) {
        refresh(entity);
    }

    @Override
    public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        refresh(entity);
    }

    @Override
    public synchronized void clear() {
        contextBuffer.clear();
    }

    @Override
    public synchronized void detach(Object entity) {
        checkEntityManagerAndSimulateException(entity, null);
        detachEntity(entity);
    }

    @Override
    public synchronized boolean contains(Object entity) {
        checkEntityManagerAndSimulateException(entity, null);
        return containsEntity(entity);
    }

    @Override
    public LockModeType getLockMode(Object entity) {
        return null;
    }

    @Override
    public void setProperty(String propertyName, Object value) {
        //
    }

    @Override
    public Map<String, Object> getProperties() {
        return Collections.emptyMap();
    }

    @Override
    public Query createQuery(String qlString) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        return null;
    }

    @Override
    public Query createQuery(CriteriaUpdate updateQuery) {
        return null;
    }

    @Override
    public Query createQuery(CriteriaDelete deleteQuery) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        return null;
    }

    @Override
    public Query createNamedQuery(String name) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, Class resultClass) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        return null;
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
        return null;
    }

    @Override
    public void joinTransaction() {
        //
    }

    @Override
    public boolean isJoinedToTransaction() {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;
    }

    @Override
    public Object getDelegate() {
        return null;
    }

    @Override
    public synchronized void close() {
        clear();
        isOpen.getAndSet(false);
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    @Override
    public EntityTransaction getTransaction() {
        return entityTransaction;
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return null;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return null;
    }

    @Override
    public Metamodel getMetamodel() {
        return null;
    }

    @Override
    public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
        return null;
    }

    @Override
    public EntityGraph<?> createEntityGraph(String graphName) {
        return null;
    }

    @Override
    public EntityGraph<?> getEntityGraph(String graphName) {
        return null;
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
        return Collections.emptyList();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private static class EntityRec {
        private boolean isMain;
        private boolean isRoot;
        private boolean isAttached;
        private boolean isRemoved;
        private Object entity;
        private final Map<String, Set<EntityKey>> childKeyMap = new HashMap<>();

        /**
         * Конструктор для метода getFilteredEntities.
         *
         * @param entityRec другой экземпляр {@link EntityRec}
         */
        public EntityRec(final EntityRec entityRec) {
            if (entityRec != null) {
                this.isMain = entityRec.isMain;
                this.isRoot = entityRec.isRoot;
                this.isAttached = entityRec.isAttached;
                this.isRemoved = entityRec.isRemoved;
                // Сущность не клонируем! (нужна текущая ссылка)
                this.entity = entityRec.entity;
                this.childKeyMap.putAll(entityRec.childKeyMap);
            }
        }
    }

    @AllArgsConstructor
    private static class EntityInfoRec {
        private final Class<?> parentClass;
        private final String parentFieldName;
        private final Object entity;
    }
}
