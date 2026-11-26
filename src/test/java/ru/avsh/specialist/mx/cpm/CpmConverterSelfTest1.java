package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Автономная проверка конвертера образов CP/M:
 * 1) создание и круглый прогон (round-trip) для ODI, KAYPRO_II и IBM_8_SSSD;
 * 2) автоопределение профиля через {@link DiskProfileFactory#detect(File)};
 * 3) многоэкстентные файлы (переход EX=0 -> EX=1), пересечение границ дорожек и skewTable;
 * 4) прогон на эталонном образе {@code src/test/resources/cpm/CPM_TurboPascalZ80.odi}, если он доступен.
 *
 * @author -=AVSh=-
 */
public final class CpmConverterSelfTest1 {
    /** Размер логической записи CP/M (хвост файла при чтении дополняется до кратности 128 байтам). */
    private static final int RECORD_SIZE = 128;

    private CpmConverterSelfTest1() {
        //
    }

    public static void main(final String[] args) throws Exception {
        final Path tempDir = Files.createTempDirectory("spmx_cpm_selftest_");
        try {
            testSyntheticRoundTrip(tempDir, "odi_4sys", DiskProfile.SPECIALIST_MX_ODI_4SYS, true);
            testSyntheticRoundTrip(tempDir, "kaypro_ii", DiskProfile.KAYPRO_II, false);
            testSyntheticRoundTrip(tempDir, "ibm_8_sssd", DiskProfile.IBM_8_SSSD, false);
            testReferenceTurboPascalDisk(tempDir);
            System.out.println("CpmConverterSelfTest: OK (все проверки пройдены)");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Создаёт образ заданного профиля с нуля, записывает три файла разного размера
     * (короткий, средний и многоэкстентный > 20 КБ), определяет профиль через
     * {@link DiskProfileFactory#detect(File)} и проверяет побайтовое совпадение при чтении.
     */
    private static void testSyntheticRoundTrip(final Path tempDir,
                                               final String tag,
                                               final DiskProfile expectedProfile,
                                               final boolean withSystemTracks) throws IOException {
        final CpmImageWriter writer = withSystemTracks
                ? new CpmImageWriter(expectedProfile, true)
                : new CpmImageWriter(expectedProfile);

        final byte[] smallData  = makePatternData(350, 0x11);     // 3 записи (меньше одного блока)
        final byte[] mediumData = makePatternData(4096, 0x37);    // 32 записи (ровно 2 или 4 блока)
        final byte[] largeData  = makePatternData(23450, 0x5A);   // ~23 КБ: больше 16 КБ (несколько экстентов EX=0, EX=1)

        final List<CpmImageReader.CpmFile> sourceFiles = new ArrayList<>();
        sourceFiles.add(new CpmImageReader.CpmFile("SMALL.TXT", 0, false, false, smallData));
        sourceFiles.add(new CpmImageReader.CpmFile("MEDIUM.COM", 0, true, false, mediumData));
        sourceFiles.add(new CpmImageReader.CpmFile("BIGFILE.DAT", 0, false, true, largeData));

        writer.writeFiles(sourceFiles, false);

        final File imageFile = tempDir.resolve(tag + ".img").toFile();
        writer.saveTo(imageFile);

        // Проверяем автоопределение профиля по содержимому образа
        final DiskProfile detected = DiskProfileFactory.detect(imageFile);
        if (!expectedProfile.getName().equals(detected.getName())) {
            throw new AssertionError(String.format(
                    "[%s] Неверно определён профиль: ожидался '%s', получен '%s'",
                    tag, expectedProfile.getName(), detected.getName()));
        }

        // Читаем файлы обратно и сверяем побайтово (в пределах исходной длины; хвост записи CP/M = 0x1A)
        final CpmImageReader reader = new CpmImageReader(imageFile, detected);
        final List<CpmImageReader.CpmFile> readBack = reader.readAllFiles();

        if (readBack.size() != sourceFiles.size()) {
            throw new AssertionError(String.format(
                    "[%s] Неверное число файлов: ожидалось %d, прочитано %d",
                    tag, sourceFiles.size(), readBack.size()));
        }

        for (int i = 0; i < sourceFiles.size(); i++) {
            final CpmImageReader.CpmFile expected = sourceFiles.get(i);
            final CpmImageReader.CpmFile actual   = readBack.get(i);

            if (!expected.getName().equals(actual.getName())) {
                throw new AssertionError(String.format(
                        "[%s] Имя файла #%d не совпадает: '%s' != '%s'",
                        tag, i, expected.getName(), actual.getName()));
            }
            if ((expected.isReadOnly() != actual.isReadOnly()) || (expected.isSystem() != actual.isSystem())) {
                throw new AssertionError(String.format(
                        "[%s] Атрибуты файла %s не совпали", tag, expected.getName()));
            }

            final byte[] expBytes = expected.getData();
            final byte[] actBytes = actual.getData();
            final int expectedCpmLen = ((expBytes.length + RECORD_SIZE - 1) / RECORD_SIZE) * RECORD_SIZE;

            if (actBytes.length != expectedCpmLen) {
                throw new AssertionError(String.format(
                        "[%s] Длина файла %s: ожидалось %d байт (кратно 128), получено %d",
                        tag, expected.getName(), expectedCpmLen, actBytes.length));
            }

            for (int b = 0; b < expBytes.length; b++) {
                if (expBytes[b] != actBytes[b]) {
                    throw new AssertionError(String.format(
                            "[%s] Расхождение данных в %s на смещении %d: ожидалось 0x%02X, получено 0x%02X",
                            tag, expected.getName(), b, expBytes[b] & 0xFF, actBytes[b] & 0xFF));
                }
            }
            for (int b = expBytes.length; b < actBytes.length; b++) {
                if (actBytes[b] != (byte) 0x1A) {
                    throw new AssertionError(String.format(
                            "[%s] Хвост записи CP/M в %s на смещении %d не заполнен 0x1A (получено 0x%02X)",
                            tag, expected.getName(), b, actBytes[b] & 0xFF));
                }
            }
        }

        System.out.println("  [PASS] " + expectedProfile.getName());
    }

    /**
     * Проверяет эталонный образ {@code CPM_TurboPascalZ80.odi}: перегоняет все файлы в новый .odi
     * и сверяет побайтовую идентичность каждого файла (включая TURBO.COM и 110-КБ GRAPH.PK2).
     */
    private static void testReferenceTurboPascalDisk(final Path tempDir) throws IOException {
        final File refFile = Paths.get("src", "test", "resources", "cpm", "CPM_TurboPascalZ80.odi").toFile();
        if (!refFile.isFile()) {
            System.out.println("  [SKIP] Эталонный диск не найден по пути: " + refFile.getPath());
            return;
        }

        final DiskProfile detected = DiskProfileFactory.detect(refFile);
        if (!DiskProfile.SPECIALIST_MX_ODI_4SYS.getName().equals(detected.getName())) {
            throw new AssertionError("Эталонный диск распознан неверно: " + detected.getName());
        }

        final List<CpmImageReader.CpmFile> originalFiles = new CpmImageReader(refFile, detected).readAllFiles();
        if (originalFiles.isEmpty()) {
            throw new AssertionError("На эталонном диске не найдено ни одного файла");
        }

        final CpmImageWriter writer = new CpmImageWriter(DiskProfile.SPECIALIST_MX_ODI_4SYS, true);
        writer.writeFiles(originalFiles, false);

        final File rebuiltFile = tempDir.resolve("rebuilt_tpas.odi").toFile();
        writer.saveTo(rebuiltFile);

        final List<CpmImageReader.CpmFile> rebuiltFiles = new CpmImageReader(rebuiltFile, detected).readAllFiles();
        if (originalFiles.size() != rebuiltFiles.size()) {
            throw new AssertionError(String.format(
                    "Число файлов после пересборки эталона не совпало: %d != %d",
                    originalFiles.size(), rebuiltFiles.size()));
        }

        for (int i = 0; i < originalFiles.size(); i++) {
            final CpmImageReader.CpmFile orig = originalFiles.get(i);
            final CpmImageReader.CpmFile copy = rebuiltFiles.get(i);
            if (!orig.getName().equals(copy.getName()) || !Arrays.equals(orig.getData(), copy.getData())) {
                throw new AssertionError("Файл после пересборки отличается от эталона: " + orig.getName());
            }
        }

        System.out.println("  [PASS] Эталон CPM_TurboPascalZ80.odi (" + originalFiles.size() + " файлов)");
    }

    private static byte[] makePatternData(final int size, final int seed) {
        final byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 31 + (i >> 7) + seed) & 0xFF);
        }
        return data;
    }

    private static void deleteRecursively(final Path root) {
        if ((root == null) || !Files.exists(root)) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (final IOException ignored) {
                            //
                        }
                    });
        } catch (final IOException ignored) {
            //
        }
    }
}
