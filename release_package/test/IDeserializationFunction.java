package com.sbt.compliance.aml.mockup.helpers.object.loader;

import java.io.InputStream;

/**
 * Функциональный интерфейс для реализации функции десериализации.
 * Created by -=AVSh=- (16.11.2017 8:51)
 */
@FunctionalInterface
public interface IDeserializationFunction {
    <T> T deserializationFromInputStream(final InputStream is, final Class<T> clazz) throws Exception;
}
