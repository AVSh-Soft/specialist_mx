package ru.avsh.specialist.mx.units;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.avsh.specialist.mx.units.TestHelper.loadAndExecuteCpmProg;

class CpuZ80Test {
    @Test
    void z80type_test() {
        final String text = loadAndExecuteCpmProg(true, "z80type.com", Duration.ofMinutes(1));
        assertEquals(
                "Z80 Processor Type Detection (C) 2024 Sergey Kiselev\n" +
                        "Detected CPU type: Older MME U880\n",
                text);
    }

    @Test
    void zexdoc_test() {
        final String text = loadAndExecuteCpmProg(true, "zexdoc.com", Duration.ofMinutes(1));

    }

    @Test
    void zexall_test() {
        final String text = loadAndExecuteCpmProg(true, "zexall.com", Duration.ofMinutes(1));

    }
}