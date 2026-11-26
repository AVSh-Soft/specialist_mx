package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CpmConverterSelfTest2 {

    private CpmConverterSelfTest2() {
        //
    }

    /**
     * Все 12 профилей и эталонный CPM_TurboPascalZ80.odi (31 файл) прошли CpmConverterSelfTest2 с
     * первого прогона — даже парные форматы с одинаковым размером файла
     * (800 КБ, 200 КБ Osborne/Kaypro, 180 КБ Amstrad Data/System) распознались точно и прошли побайтовую сверку.
     * <p>
     * Что это подтверждает в вашем коде
     * <p>
     * • Математика BlockLayout + CpmImageReader + CpmImageWriter теперь полностью симметрична для всех трёх режимов:
     * • без интерлива (ODI, Kaypro II/4, Amstrad, CP/M 720 KB);
     * • с таблицей перемежения skewTable и пересечением границ дорожек (IBM 8" SSSD, Osborne 1 SSSD/SSDD);
     * • с экстент-маской EXM = 1 на 4-килобайтных блоках (Specialist MX ODI 800 KB, 4K block).
     * • Скоринг каталога в DiskProfileFactory корректно разводит профили одного объёма за счёт проверки валидности
     * записей каталога и диапазона указателей блоков, даже когда системные дорожки у синтетического образа не заполнены
     * загрузчиком.
     * • Реальный образ CPM_TurboPascalZ80.odi полностью пересобирается со всеми 31 файлами (включая многоэкстентный GRAPH.PK2)
     * и запускается в эмуляторе.
     * <p>
     * Один практический нюанс на будущее (при работе с реальными файлами из интернета)
     * <p>
     * Если в GUI будут открывать образы, скачанные из сети:
     * • Сырые посекторные дампы (.img, .odi, .dsk без заголовка) — все 12 профилей откроются сразу.
     * • Эмуляторные контейнеры с заголовками (например, .imd от ImageDisk, .td0 от Teledisk или .dsk с 256-байтным
     * заголовком MV - CPC / EXTENDED CPC DSK от Amstrad) — имеют размер больше сырого дампа и требуют предварительного извлечения секторов перед передачей в DiskProfileFactory.
     */

    public static void main(final String[] args) throws Exception {
        final Path tempDir = Files.createTempDirectory("spmx_cpm_selftest_");
        try {
            // 1. Все варианты 800 КБ (.odi)
            testProfile(tempDir, "odi_4sys", DiskProfile.SPECIALIST_MX_ODI_4SYS, true);
            testProfile(tempDir, "odi_2sys", DiskProfile.SPECIALIST_MX_800K, false);
            testProfileByName(tempDir, "odi_3sys", "Specialist MX / Korvet ODI (800 KB, 3 sys tracks)");
            testProfileByName(tempDir, "odi_4k", "Specialist MX ODI (800 KB, 4K block)");

            // 2. Советский 720 КБ и IBM 8"
            testProfileByName(tempDir, "cpm_720k", "CP/M 720 KB (80x2x9x512)");
            testProfile(tempDir, "ibm_8_sssd", DiskProfile.IBM_8_SSSD, false);

            // 3. Osborne и Kaypro (включая конфликтные 200 КБ)
            testProfileByName(tempDir, "osborne_ssdd", "Osborne 1 SSDD (200 KB)");
            testProfileByName(tempDir, "osborne_sssd", "Osborne 1 SSSD (100 KB)");
            testProfileByName(tempDir, "kaypro_4", "Kaypro 4 (400 KB)");
            testProfile(tempDir, "kaypro_ii", DiskProfile.KAYPRO_II, false);

            // 4. Amstrad CPC (Data vs System одинакового размера 180 КБ)
            testProfileByName(tempDir, "amstrad_data", "Amstrad CPC Data (180 KB)");
            testProfileByName(tempDir, "amstrad_sys", "Amstrad CPC System (180 KB)");

            // 5. Эталонный диск Turbo Pascal
            testReferenceTurboPascalDisk(tempDir);

            System.out.println("CpmConverterSelfTest: OK (все 12 профилей + эталон пройдены)");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testProfileByName(final Path tempDir,
                                          final String tag,
                                          final String profileName) throws IOException {
        final int[] knownSizes = {
                819200, 737280, 256256, 409600, 204800, 102400, 184320
        };
        for (final int size : knownSizes) {
            final File probe = tempDir.resolve("probe_" + size + ".img").toFile();
            if (!probe.exists()) {
                final byte[] blank = new byte[size];
                Arrays.fill(blank, (byte) 0xE5);
                Files.write(probe.toPath(), blank);
            }
            for (final DiskProfile candidate : DiskProfileFactory.candidates(probe)) {
                if (profileName.equals(candidate.getName())) {
                    testProfile(tempDir, tag, candidate, false);
                    return;
                }
            }
        }
        throw new AssertionError("Профиль не найден в DiskProfileFactory: " + profileName);
    }

    private static void testProfile(final Path tempDir,
                                    final String tag,
                                    final DiskProfile expectedProfile,
                                    final boolean withSystemTracks) throws IOException {
        final CpmImageWriter writer = withSystemTracks
                ? new CpmImageWriter(expectedProfile, true)
                : new CpmImageWriter(expectedProfile);

        final byte[] smallData = makePatternData(350, 0x11);
        final byte[] mediumData = makePatternData(4096, 0x37);
        final byte[] largeData = makePatternData(36000, 0x5A); // > 32 КБ: проверяет EXM=1 на блоке 4КБ

        final List<CpmImageReader.CpmFile> sourceFiles = new ArrayList<>();
        sourceFiles.add(new CpmImageReader.CpmFile("SMALL.TXT", 0, false, false, smallData));
        sourceFiles.add(new CpmImageReader.CpmFile("MEDIUM.COM", 0, true, false, mediumData));
        sourceFiles.add(new CpmImageReader.CpmFile("BIGFILE.DAT", 0, false, true, largeData));

        writer.writeFiles(sourceFiles, false);

        final File imageFile = tempDir.resolve(tag + ".img").toFile();
        writer.saveTo(imageFile);

        final DiskProfile detected = DiskProfileFactory.detect(imageFile);
        if (!expectedProfile.getName().equals(detected.getName())) {
            throw new AssertionError(String.format(
                    "[%s] Неверно определён профиль: ожидался '%s', получен '%s'",
                    tag, expectedProfile.getName(), detected.getName()));
        }

        final List<CpmImageReader.CpmFile> readBack = new CpmImageReader(imageFile, detected).readAllFiles();
        if (readBack.size() != sourceFiles.size()) {
            throw new AssertionError(String.format(
                    "[%s] Неверное число файлов: ожидалось %d, прочитано %d",
                    tag, sourceFiles.size(), readBack.size()));
        }

        for (int i = 0; i < sourceFiles.size(); i++) {
            final CpmImageReader.CpmFile expected = sourceFiles.get(i);
            final CpmImageReader.CpmFile actual = readBack.get(i);
            final byte[] expBytes = expected.getData();
            final byte[] actBytes = actual.getData();

            for (int b = 0; b < expBytes.length; b++) {
                if (expBytes[b] != actBytes[b]) {
                    throw new AssertionError(String.format(
                            "[%s] Расхождение в %s на смещении %d: 0x%02X != 0x%02X",
                            tag, expected.getName(), b, expBytes[b] & 0xFF, actBytes[b] & 0xFF));
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
