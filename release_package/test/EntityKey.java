package com.sbt.compliance.storage.oracle.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.Nullable;

/**
 * Ключ сущности.
 */
@Slf4j
@Getter
@ToString
@EqualsAndHashCode
public class EntityKey {
    private final String clazzName;
    private final Object id;

    public EntityKey(final Class<?> clazz, final Object id) {
        // Нежелательно использовать Class в качестве элемента ключа
        this.clazzName = (clazz != null) ? clazz.getName() : null;
        this.id = id;
    }

    @Nullable
    public static EntityKey createEntityKey(final Object entity) {
        if (entity != null) {
            final Class<?> entityClass = Hibernate.getClass(entity);
            if (HibernateEntityHelper.isHibernateEntity(entityClass)) {
                return new EntityKey(entityClass, HibernateEntityHelper.getEntityId(entity));
            }
        }
        return null;
    }

    public Class<?> findClassByName() {
        try {
            return StringUtils.isNotEmpty(clazzName) ? ClassUtils.getClass(clazzName) : null;
        } catch (ClassNotFoundException e) {
            if (log.isErrorEnabled()) {
                log.error("Не удалось получить класс по его имени [{}]: {}", clazzName, ExceptionUtils.getStackTrace(e));
            }
        }
        return null;
    }
}
