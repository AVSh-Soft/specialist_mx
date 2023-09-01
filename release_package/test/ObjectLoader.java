package com.sbt.compliance.aml.mockup.helpers.object.loader;

import com.sbt.compliance.dictionaries.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jboss.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Загрузчик объектов из файлов/ресурсов.
 *
 * @author -=AVSh=-
 */
@Slf4j
public final class ObjectLoader {
    public static final String FILE_SEPARATOR = File.separator;
    public static final String RESOURCE_SEPARATOR = "/";
    public static final String EXTENSION_SEPARATOR = ".";

    private final String filesRootPath;
    private final String resourcesRootPath;
    private final String extension;
    private final IDeserializationFunction deserializationFunction;

    /**
     * Конструктор.
     *
     * @param filesRootPath           путь к корневому каталогу с файлом
     * @param resourcesRootPath       путь к корневому каталогу с ресурсами
     * @param extension               расширение файлов/ресурсов (без точки)
     * @param deserializationFunction функция десериализации
     */
    public ObjectLoader(final String filesRootPath,
                        @NotNull final String resourcesRootPath,
                        @NotNull final String extension,
                        @NotNull final IDeserializationFunction deserializationFunction) {

        String rootPath = null;
        try {
            if (StringUtils.isNotBlank(filesRootPath)) {
                Path path = Paths.get(filesRootPath.trim());
                if (path.toFile().exists()) {
                    rootPath = path.toString();
                }
            }
        } catch (RuntimeException e) {
            if (log.isInfoEnabled()) {
                log.info("Внешний корневой путь [{}] для {}-файлов задан некорректно!\n{}",
                        filesRootPath, extension, ExceptionUtils.getStackTrace(e));
            }
        }
        this.filesRootPath = rootPath;
        this.resourcesRootPath = resourcesRootPath;
        this.extension = extension;
        this.deserializationFunction = deserializationFunction;
    }

    /**
     * Возвращает экземпляр загрузчика файлов/ресурсов.
     *
     * @param filesRootPath           путь к корневому каталогу с файлом
     * @param resourcesRootPath       путь к корневому каталогу с ресурсами
     * @param extension               расширение файлов/ресурсов (без точки)
     * @param deserializationFunction функция десериализации
     * @return экземпляр загрузчика файлов/ресурсов
     */
    public static ObjectLoader getObjectLoader(final String filesRootPath,
                                               @NotNull final String resourcesRootPath,
                                               @NotNull final String extension,
                                               @NotNull final IDeserializationFunction deserializationFunction) {
        return new ObjectLoader(filesRootPath, resourcesRootPath, extension, deserializationFunction);
    }

    /**
     * Объединяет prefix + word + suffix с проверкой на существование prefix-а и suffix-а в word.
     *
     * @param prefix префикс
     * @param word   слово
     * @param suffix суффикс
     * @return результат объединения (результат всегда != NULL)
     */
    @NotNull
    private String unite(final String prefix, final String word, final String suffix) {
        String result = "";
        if (StringUtils.isNotBlank(word)) {
            result = word;
        }
        if (StringUtils.isNotBlank(prefix) &&
                !result.toLowerCase(Constants.RU).startsWith(prefix.toLowerCase(Constants.RU))) {
            result = prefix.concat(result);
        }
        if (StringUtils.isNotBlank(suffix) &&
                !result.toLowerCase(Constants.RU).endsWith(suffix.toLowerCase(Constants.RU))) {
            result = result.concat(suffix);
        }
        return result;
    }

    /**
     * Формирует путь до файла/ресурса с разделителем на конце.
     *
     * @param rootPath  корневой каталог
     * @param className имя класса
     * @param separator разделитель
     * @return путь
     */
    private String getPath(@NotNull final String rootPath,
                           @NotNull final String className,
                           @NotNull final String separator) {
        return rootPath.concat(separator).concat(className).concat(separator);
    }

    /**
     * Формирует имя файла/ресурса с расширением.
     *
     * @param name имя файла/ресурса с расширением или без него
     * @return имя файла/ресурса с расширением
     */
    @NotNull
    public String getNameWithExt(final String name) {
        return unite("", name, EXTENSION_SEPARATOR.concat(extension));
    }

    /**
     * Возвращает имя файла/ресурса без расширения.
     *
     * @param name имя файла/ресурса с расширением или без него
     * @return имя файла/ресурса без расширения
     */
    @Nullable
    public String getNameWithoutExt(final String name) {
        return StringUtils.substringBefore(name, EXTENSION_SEPARATOR);
    }

    /**
     * Конвертирует файловую маску (или список файловых масок разделенных символами "|", ",", ";")
     * в регулярное выражение.
     *
     * @param rawMasks   файловая маска или файловые маски с разделителями
     * @param prefixMask постоянный префикс для каждой маски
     * @param suffixMask постоянный суффикс для каждой маски
     * @return регулярное выражение
     */
    private String maskToRegex(final String rawMasks, final String prefixMask, final String suffixMask) {
        final String[] masks = rawMasks.split("[|,;]");
        final StringBuilder regex = new StringBuilder();
        for (String mask : masks) {
            // признак начала строки
            regex.append('^');
            for (char symbol : unite(prefixMask, mask, suffixMask).toCharArray()) {
                switch (symbol) {
                    case '.':
                        regex.append("\\.");
                        break;
                    case '?':
                        regex.append('.');
                        break;
                    case '*':
                        regex.append(".*");
                        break;
                    case '\\':
                        regex.append("\\\\");
                        break;
                    default:
                        regex.append(symbol);
                }
            }
            // признак окончания строки
            regex.append("$|");
        }
        if (regex.length() > 0) {
            regex.setLength(regex.length() - 1);
        }
        return regex.toString();
    }

    /**
     * Возвращает экземпляр класса "Pattern", скомпилированный по заданной файловой маске.
     *
     * @param rawMasks   файловая маска или файловые маски с разделителями
     * @param prefixMask постоянный префикс для каждой маски
     * @return регулярное выражение
     */
    public Pattern getPattern(final String rawMasks, final String prefixMask) {
        return Pattern.compile(maskToRegex(rawMasks, prefixMask, EXTENSION_SEPARATOR.concat(extension)),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Возвращает набор имен файлов из заданного каталога по заданной файловой маске.
     *
     * @param dir  каталог файлов
     * @param mask файловая маска или файловые маски с разделителями
     * @return набор имен файлов (результат всегда != NULL)
     */
    @NotNull
    private Set<String> getSetOfFilenames(final Path dir, final String mask) {
        final Pattern pattern = getPattern(mask, "");
        try (final Stream<Path> pathStream = Files.list(dir)) {
            return pathStream
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> pattern.matcher(name).matches())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Ошибка при получении списка файлов из каталога: {} по маске: {}!\n{}",
                    dir, mask, ExceptionUtils.getStackTrace(e));
        }
        return Collections.emptySet();
    }

    /**
     * Возвращает набор имен ресурсов из jar-файла по заданному каталогу и заданной файловой маске.
     *
     * @param resDir каталог ресурсов (с разделителем на конце)
     * @param dirURL Uniform Resource Locator внутри jar-файла, соответствующий resDir
     * @param mask   файловая маска или файловые маски с разделителями
     * @return набор имен ресурсов (результат всегда != NULL)
     * @throws IOException исключение
     */
    @NotNull
    private Set<String> getSetOfResourceNamesFromJar(final String resDir, final URL dirURL, final String mask) throws IOException {
        if (StringUtils.isBlank(resDir) || (dirURL == null)) {
            return Collections.emptySet();
        }

        final Set<String> result = new HashSet<>();
        // Здесь ресурсы лежат внутри jar-файла, путь возвращается в таком виде:
        // [file:полный_путь_к_jar!путь_внутри_jar], причем путь_внутри_jar = resDir
        String jarPath = dirURL.getPath();
        // Вырезаем подстроку после ":" и до "!" - остается только полный_путь_к_jar
        jarPath = jarPath.substring(jarPath.indexOf(':') + 1, jarPath.indexOf('!'));
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
            // Впереди маску дополняем путём
            final Pattern pattern = getPattern(mask, resDir);
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (pattern.matcher(name).matches()) {
                    result.add(name.substring(resDir.length()));
                }
            }
        }
        return result;
    }

    /**
     * Возвращает набор имен ресурсов из war-файла по заданному каталогу и заданной файловой маске.
     *
     * @param dirURL Uniform Resource Locator для ресурсов внутри war-файла
     * @param mask   файловая маска или файловые маски с разделителями
     * @return набор имен ресурсов (результат всегда != NULL)
     * @throws IOException исключение
     */
    @NotNull
    private Set<String> getSetOfResourceNamesFromWar(final URL dirURL, final String mask) throws IOException {
        if (dirURL == null) {
            return Collections.emptySet();
        }

        final Set<String> result = new HashSet<>();
        // Здесь идем по виртуальной файловой системе JBoss WildFly
        final URLConnection connection = dirURL.openConnection();
        final VirtualFile virtualDir = (VirtualFile) connection.getContent();
        final Pattern pattern = getPattern(mask, "");
        for (VirtualFile virtualFile : virtualDir.getChildren()) {
            final String name = virtualFile.getName();
            if (pattern.matcher(name).matches()) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * Возвращает набор имен ресурсов по заданному каталогу и заданной файловой маске.
     *
     * @param resDir каталог ресурсов (с разделителем на конце)
     * @param mask   файловая маска или файловые маски с разделителями
     * @return набор имен ресурсов (результат всегда != NULL)
     * @throws URISyntaxException исключение
     * @throws IOException        исключение
     */
    @NotNull
    private Set<String> getSetOfResourceNames(final String resDir, final String mask)
            throws URISyntaxException, IOException {
        // Получаем Uniform Resource Locator для заданного каталога ресурсов
        final URL dirURL = this.getClass().getClassLoader().getResource(resDir);
        if (dirURL == null) {
            return Collections.emptySet();
        }

        Set<String> result;
        switch (dirURL.getProtocol()) {
            case "file":
                result = getSetOfFilenames(Paths.get(dirURL.toURI()), mask);
                break;
            case "jar":
                result = getSetOfResourceNamesFromJar(resDir, dirURL, mask);
                break;
            case "vfs":
                result = getSetOfResourceNamesFromWar(dirURL, mask);
                break;
            default:
                result = Collections.emptySet();
                if (log.isWarnEnabled()) {
                    log.warn("При получении списка {}-ресурсов был обнаружен неизвестный протокол: {}",
                            extension, dirURL.getProtocol());
                }
        }
        return result;
    }

    private void errorGetNames(final String what, final String resDir, final Exception e) {
        log.error("Ошибка получения списка {}-{} из каталога: [{}]!%n{}",
                extension, what, resDir, ExceptionUtils.getStackTrace(e));
    }

    private void traceGetNames(final String what, final String resDir, final String mask) {
        if (log.isTraceEnabled()) {
            log.trace("Не удалось найти {}-{}, удовлетворяющие файловой маске [{}] в каталоге: [{}]",
                    extension, what, mask, resDir);
        }
    }

    //---= [getKeys] =---\/---------------------------------------------------------------------------------------------

    /**
     * Возвращает набор имен файлов/ресурсов (для использования в методе getObject)
     * с расширением extension по заданному классу и маске.
     * (Список никогда не возвращается = null)
     *
     * @param clazz класс объекта
     * @param mask  маска
     * @return набор имен файлов/ресурсов
     */
    @NotNull
    public Set<String> getNames(final Class<?> clazz, final String mask) {
        final Set<String> result = new HashSet<>();
        if ((clazz == null) || StringUtils.isBlank(mask)) {
            return result;
        }

        String resDir;
        if (filesRootPath != null) {
            resDir = getPath(filesRootPath, clazz.getSimpleName(), FILE_SEPARATOR);
            try {
                result.addAll(getSetOfFilenames(Paths.get(resDir), mask));
            } catch (RuntimeException e) {
                errorGetNames("файлов", resDir, e);
            }

            if (result.isEmpty()) {
                traceGetNames("файлы", resDir, mask);
            }
        }

        if (result.isEmpty()) {
            resDir = getPath(resourcesRootPath, clazz.getSimpleName(), RESOURCE_SEPARATOR);
            try {
                result.addAll(getSetOfResourceNames(resDir, mask));
            } catch (IOException | URISyntaxException e) {
                errorGetNames("ресурсов", resDir, e);
            }

            if (result.isEmpty()) {
                traceGetNames("ресурсы", resDir, mask);
            }
        }
        return result;
    }
    //---= [getKeys] =---/\---------------------------------------------------------------------------------------------

    /**
     * Выполняет десериализацию объекта из потока с данными.
     *
     * @param clazz класс десериализуемого объекта
     * @param is    поток с данными
     * @param name  имя файла/ресурса
     * @param <T>   тип десериализуемого объекта
     * @return объект
     */
    private <T> T deserialization(final Class<T> clazz, final InputStream is, final String name) {
        try {
            return deserializationFunction.deserializationFromInputStream(is, clazz);
        } catch (Exception e) {
            log.error("Ошибка при десериализации {}-файла: [{}]!\n{}", extension, name, ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    /**
     * Получает объект из файла.
     *
     * @param clazz класс объекта
     * @param name  имя для поиска объекта
     * @param <T>   тип объекта
     * @return объект или null, если были ошибки
     */
    private <T> T getObjectFromFiles(final Class<T> clazz, final String name) {
        T result = null;
        if (filesRootPath != null) {
            final File file = FileUtils.getFile(
                    getPath(filesRootPath, clazz.getSimpleName(), FILE_SEPARATOR).concat(getNameWithExt(name)));
            try (InputStream is = new FileInputStream(file)) {
                result = deserialization(clazz, is, file.getPath());
            } catch (IOException e) {
                if (log.isTraceEnabled()) {
                    log.trace("{}-файл: [{}] не найден!\n{}", extension, file.getPath(), ExceptionUtils.getStackTrace(e));
                }
            }
        }
        return result;
    }

    /**
     * Получает объект из ресурса.
     *
     * @param clazz класс объекта
     * @param name  имя для поиска объекта
     * @param <T>   тип объекта
     * @return объект или null, если были ошибки
     */
    private <T> T getObjectFromResources(final Class<T> clazz, final String name) {
        T result = null;
        final String fullName = getPath(resourcesRootPath, clazz.getSimpleName(), RESOURCE_SEPARATOR).concat(getNameWithExt(name));
        final URL resource = this.getClass().getClassLoader().getResource(fullName);
        if (resource != null) {
            try (InputStream is = resource.openStream()) {
                result = deserialization(clazz, is, resource.toString());
            } catch (IOException e) {
                log.error("Ошибка открытия {}-ресурса: [{}]!\n{}", extension, resource, ExceptionUtils.getStackTrace(e));
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("{}-ресурс: [{}] не найден!", extension, fullName);
            }
        }
        return result;
    }

    //---= [getObject] =---\/-------------------------------------------------------------------------------------------

    /**
     * Получает объект из файла/ресурса.
     *
     * @param clazz класс объекта
     * @param name  имя для поиска объекта
     * @return объект или null, если не были ошибки
     */
    public <T> T getObject(final Class<T> clazz, final String name) {
        T result = null;
        if ((clazz != null) && StringUtils.isNotBlank(name)) {
            // Загрузчик из файлов
            result = getObjectFromFiles(clazz, name);
            if (result == null) {
                // Загрузчик из ресурсов
                result = getObjectFromResources(clazz, name);
            }
        }
        return result;
    }
    //---= [getObject] =---/\-------------------------------------------------------------------------------------------
}
