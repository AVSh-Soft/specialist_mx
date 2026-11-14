package ru.avsh.specialist.mx.units;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.avsh.specialist.mx.units.TestHelper.loadAndExecuteCpmProg;

/**
 * Для полной проверки работоспособности и соответствия всех недокументированных флагов
 * оригинальной архитектуре используют классические утилиты для ОС CP/M (их также можно
 * запускать на тестовых стендах без ОС):
 * • 8080EXM.COM (8080 Instruction Set Exerciser): Самый строгий тест, проверяющий все
 * комбинации инструкций и флагов по контрольным суммам (CRC) эталонного Intel 8080.
 * • TST8080.COM и CPUTEST.COM: Базовые диагностические тесты, проверяющие корректность
 * работы АЛУ, регистров, стека и условных переходов.
 */
class CpuI8080Test {
    @Test
    void i8080PRE_test() {
        final String text = loadAndExecuteCpmProg(false, "8080PRE.COM", Duration.ofMinutes(1));
        assertEquals("8080 Preliminary tests complete", text);
    }

    @Test
    void TST8080_test() {
        final String text = loadAndExecuteCpmProg(false, "TST8080.COM", Duration.ofMinutes(1));
        assertEquals(
                "MICROCOSM ASSOCIATES 8080/8085 CPU DIAGNOSTIC\n" +
                        " VERSION 1.0  (C) 1980\n" +
                        "\n" +
                        " CPU IS OPERATIONAL",
                text);
    }

    @Test
    void CPUTEST_test() {
        final String text = loadAndExecuteCpmProg(false, "CPUTEST.COM", Duration.ofMinutes(1));
        assertEquals(
                "\u0000\u0000\u0000\u0000\u0000\u0000\n" +
                        "DIAGNOSTICS II V1.2 - CPU TEST\n" +
                        "COPYRIGHT (C) 1981 - SUPERSOFT ASSOCIATES\n" +
                        "\n" +
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
                        "CPU IS 8080/8085\n" +
                        "BEGIN TIMING TEST\n" +
                        "\u0007\u0007END TIMING TEST\n" +
                        "CPU TESTS OK\n",
                text);
    }

    // Тяжелый и долгий тест (1.5-2 минуты, на реальном i8080 2МГц около 3 часов 20 минут)
    @Test
    void i8080EXER_test() {
        final String text = loadAndExecuteCpmProg(false, "8080EXER.COM", Duration.ofMinutes(5));
        assertEquals(
                "8080 instruction exerciser\n" +
                        "dad <b,d,h,sp>................  OK\n" +
                        "aluop nn......................  OK\n" +
                        "aluop <b,c,d,e,h,l,m,a>.......  OK\n" +
                        "<daa,cma,stc,cmc>.............  OK\n" +
                        "<inr,dcr> a...................  OK\n" +
                        "<inr,dcr> b...................  OK\n" +
                        "<inx,dcx> b...................  OK\n" +
                        "<inr,dcr> c...................  OK\n" +
                        "<inr,dcr> d...................  OK\n" +
                        "<inx,dcx> d...................  OK\n" +
                        "<inr,dcr> e...................  OK\n" +
                        "<inr,dcr> h...................  OK\n" +
                        "<inx,dcx> h...................  OK\n" +
                        "<inr,dcr> l...................  OK\n" +
                        "<inr,dcr> m...................  OK\n" +
                        "<inx,dcx> sp..................  OK\n" +
                        "lhld nnnn.....................  OK\n" +
                        "shld nnnn.....................  OK\n" +
                        "lxi <b,d,h,sp>,nnnn...........  OK\n" +
                        "ldax <b,d>....................  OK\n" +
                        "mvi <b,c,d,e,h,l,m,a>,nn......  OK\n" +
                        "mov <bcdehla>,<bcdehla>.......  OK\n" +
                        "sta nnnn / lda nnnn...........  OK\n" +
                        "<rlc,rrc,ral,rar>.............  OK\n" +
                        "stax <b,d>....................  OK\n" +
                        "Tests complete",
                text);
    }

    // Тяжелый и долгий тест (1.5-2 минуты, на реальном i8080 2МГц около 3 часов 20 минут)
    // Тут для i8085, поэтому на i8080 два теста падают
    @Test
    void i8085EXM_test() {
        final String text = loadAndExecuteCpmProg(false, "8085EXM.COM", Duration.ofMinutes(5));
        assertEquals("8085 instruction exerciser\n" +
                "dad <b,d,h,sp>................  PASS! crc is:44331def\n" +
                "aluop nn......................  ERROR **** crc expected:98faf22b found:2b1ed911\n" +
                "aluop <b,c,d,e,h,l,m,a>.......  ERROR **** crc expected:0cfd75b5 found:c1f88c18\n" +
                "<daa,cma,stc,cmc>.............  PASS! crc is:17cfab99\n" +
                "<inr,dcr> a...................  PASS! crc is:9ef4ef36\n" +
                "<inr,dcr> b...................  PASS! crc is:b0afba7d\n" +
                "<inx,dcx> b...................  PASS! crc is:d38a84e2\n" +
                "<inr,dcr> c...................  PASS! crc is:d6b4db23\n" +
                "<inr,dcr> d...................  PASS! crc is:26f7fea2\n" +
                "<inx,dcx> d...................  PASS! crc is:5b56262e\n" +
                "<inr,dcr> e...................  PASS! crc is:fc681aae\n" +
                "<inr,dcr> h...................  PASS! crc is:21f03c14\n" +
                "<inx,dcx> h...................  PASS! crc is:bb3320ef\n" +
                "<inr,dcr> l...................  PASS! crc is:cc157a6e\n" +
                "<inr,dcr> m...................  PASS! crc is:a1abca85\n" +
                "<inx,dcx> sp..................  PASS! crc is:f1682c84\n" +
                "lhld nnnn.....................  PASS! crc is:6d1eeb35\n" +
                "shld nnnn.....................  PASS! crc is:2c5b71d8\n" +
                "lxi <b,d,h,sp>,nnnn...........  PASS! crc is:cfdfbbb3\n" +
                "ldax <b,d>....................  PASS! crc is:30cdccc6\n" +
                "mvi <b,c,d,e,h,l,m,a>,nn......  PASS! crc is:cc3f3d29\n" +
                "mov <bcdehla>,<bcdehla>.......  PASS! crc is:e2c0feb3\n" +
                "sta nnnn / lda nnnn...........  PASS! crc is:f6187eeb\n" +
                "<rlc,rrc,ral,rar>.............  PASS! crc is:7ea730b3\n" +
                "stax <b,d>....................  PASS! crc is:b726a433\n" +
                "Tests complete", text);
    }
}