package ru.avsh.specialist.mx.units;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.units.memory.MemoryManager;
import ru.avsh.specialist.mx.units.memory.units.SimpleMemory;
import ru.avsh.specialist.mx.units.types.CpuCore;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public final class TestHelper {

    private TestHelper() {
        //
    }

    /**
     * Загружает и выполняет тестовую CP/M-программу.
     *
     * @param isZ80       true, если процессор Z80, иначе i8080
     * @param cpmProgName тестовую CP/M-программа
     * @param timeout     тайм-аут, который не должна превысить исполняемая программа
     * @return содержимое текстового буфера, сформированного CP/M-программой
     */
    public static String loadAndExecuteCpmProg(final boolean isZ80,
                                               final @NotNull String cpmProgName,
                                               final @NotNull Duration timeout) {

        final int start = 0x0100;
        final MemoryManager memory = new MemoryManager();
        memory.addMemoryUnit(0, new SimpleMemory(0x1_0000));

        final CpuCore cpu = isZ80
                ? new CpuZ80(null, memory, null)
                : new CpuI8080(null, memory, null);
        cpu.setActive(true);

        final URL url = TestHelper.class.getResource("/" + cpmProgName);
        if (url != null) {
            assertDoesNotThrow(() -> {
                try (final InputStream is = url.openStream()) {
                    final int available = is.available();
                    for (int i = 0; i < available; i++) {
                        memory.writeByte(i + start, is.read());
                    }
                }
            });
        } else {
            fail("Не удалось загрузить файл: " + cpmProgName);
        }

        cpu.setCommonRegPair(9, start);

        memory.writeByte(5, 0xC9); // RET
        memory.writeByte(6, 0xFF); // SP
        memory.writeByte(7, 0xFF);

        final StringBuffer stringBuffer = new StringBuffer();
        assertTimeoutPreemptively(timeout, () -> {
            while (true) {
                // Одна команда CPU
                while (!cpu.cycle()) {
                    //
                }
                final int pc = cpu.getCommonRegPair(9);
                // Выход из BDOS
                if (pc == 0) {
                    break;
                }
                // BDOS
                if ((pc == 5) && callBDOS(cpu, memory, stringBuffer)) {
                    break;
                }
            }
        });
        return stringBuffer.toString()
                .replace("\n\r", "\n")
                .replaceAll("\\R", "\n");
    }

    private static boolean callBDOS(final @NotNull CpuCore cpu,
                                    final @NotNull MemoryManager memory,
                                    final @NotNull StringBuffer stringBuffer) {

        final int bc = cpu.getCommonRegPair(0);
        final int de = cpu.getCommonRegPair(2);
        final int c = bc & 0xFF;

        switch (c) {
            case 0: {
                return true;
            }

            case 1: {
                final int af = cpu.getCommonRegPair(6);
                cpu.setCommonRegPair(6, af & 0xFF | 0x0D00); // Enter
                break;
            }

            case 2: {
                char value = (char) (de & 0xFF);
                stringBuffer.append(value);
                System.out.print(value);
                break;
            }

            case 9: {
                int adr = de & 0xFFFF;
                int value;
                do {
                    value = memory.readByte(adr++) & 0xFF;
                    if (value != 0x24) {
                        stringBuffer.append((char) value);
                        System.out.print((char) value);
                    }
                }
                while (value != 0x24);
                break;
            }
            default: {
                System.out.println("BDOS: " + c);
            }
        }
        return false;
    }
}
