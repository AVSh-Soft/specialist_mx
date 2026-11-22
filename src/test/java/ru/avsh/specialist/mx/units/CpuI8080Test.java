package ru.avsh.specialist.mx.units;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.avsh.specialist.mx.units.TestHelper.loadAndExecuteCpmProg;

/**
 * Для полной проверки работоспособности и соответствия всех недокументированных флагов
 * оригинальной архитектуре используют классические утилиты для ОС CP/M (их также можно
 * запускать на тестовых стендах без ОС):
 * • 8080exm.com (8080 Instruction Set Exerciser): Самый строгий тест, проверяющий все
 * комбинации инструкций и флагов по контрольным суммам (CRC) эталонного Intel 8080.
 * • tst8080.com и cputest.com: Базовые диагностические тесты, проверяющие корректность
 * работы АЛУ, регистров, стека и условных переходов.
 */
class CpuI8080Test {
    @Test
    void test_8080pre() {
        final String text = loadAndExecuteCpmProg(false, "8080pre.com", Duration.ofMinutes(1));
        assertEquals("8080 Preliminary tests complete", text);
    }

    @Test
    void test_8080() {
        final String text = loadAndExecuteCpmProg(false, "8080.com", Duration.ofMinutes(1));
        assertEquals(
                "MICROCOSM ASSOCIATES 8080/8085 CPU DIAGNOSTIC VERSION 1.0  (C) 1980\n" +
                        "\n" +
                        "CPU IS OPERATIONAL",
                text);
    }

    @Test
    void test_tst8080() {
        final String text = loadAndExecuteCpmProg(false, "tst8080.com", Duration.ofMinutes(1));
        assertEquals(
                "MICROCOSM ASSOCIATES 8080/8085 CPU DIAGNOSTIC\n" +
                        " VERSION 1.0  (C) 1980\n" +
                        "\n" +
                        " CPU IS OPERATIONAL",
                text);
    }

    @Test
    void test_cputest() {
        final String text = loadAndExecuteCpmProg(false, "cputest.com", Duration.ofMinutes(1));
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

    // Самый точный, но очень тяжелый и долгий тест (1.5-2 минуты, на реальном I8080 2МГц около 3 часов 20 минут)
    @Test
    void test_8080exm() {
        final String text = loadAndExecuteCpmProg(false, "8080exm.com", Duration.ofMinutes(15));
        assertEquals(
                "8080 instruction exerciser\n" +
                        "dad <b,d,h,sp>................  PASS! crc is:14474ba6\n" +
                        "aluop nn......................  PASS! crc is:9e922f9e\n" +
                        "aluop <b,c,d,e,h,l,m,a>.......  PASS! crc is:cf762c86\n" +
                        "<daa,cma,stc,cmc>.............  PASS! crc is:bb3f030c\n" +
                        "<inr,dcr> a...................  PASS! crc is:adb6460e\n" +
                        "<inr,dcr> b...................  PASS! crc is:83ed1345\n" +
                        "<inx,dcx> b...................  PASS! crc is:f79287cd\n" +
                        "<inr,dcr> c...................  PASS! crc is:e5f6721b\n" +
                        "<inr,dcr> d...................  PASS! crc is:15b5579a\n" +
                        "<inx,dcx> d...................  PASS! crc is:7f4e2501\n" +
                        "<inr,dcr> e...................  PASS! crc is:cf2ab396\n" +
                        "<inr,dcr> h...................  PASS! crc is:12b2952c\n" +
                        "<inx,dcx> h...................  PASS! crc is:9f2b23c0\n" +
                        "<inr,dcr> l...................  PASS! crc is:ff57d356\n" +
                        "<inr,dcr> m...................  PASS! crc is:92e963bd\n" +
                        "<inx,dcx> sp..................  PASS! crc is:d5702fab\n" +
                        "lhld nnnn.....................  PASS! crc is:a9c3d5cb\n" +
                        "shld nnnn.....................  PASS! crc is:e8864f26\n" +
                        "lxi <b,d,h,sp>,nnnn...........  PASS! crc is:fcf46e12\n" +
                        "ldax <b,d>....................  PASS! crc is:2b821d5f\n" +
                        "mvi <b,c,d,e,h,l,m,a>,nn......  PASS! crc is:eaa72044\n" +
                        "mov <bcdehla>,<bcdehla>.......  PASS! crc is:10b58cee\n" +
                        "sta nnnn / lda nnnn...........  PASS! crc is:ed57af72\n" +
                        "<rlc,rrc,ral,rar>.............  PASS! crc is:e0d89235\n" +
                        "stax <b,d>....................  PASS! crc is:2b0471e9\n" +
                        "Tests complete",
                text);
    }

    // Вариант 8080exm.com, но без вывода контрольных сумм
    @Disabled
    @Test
    void test_8080exer() {
        final String text = loadAndExecuteCpmProg(false, "8080exer.com", Duration.ofMinutes(15));
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

    // Вариант 8080exm.com, но без вывода контрольных сумм и с заголовком KR580VM80A CPU
    @Disabled
    @Test
    void test_8080ex1() {
        final String text = loadAndExecuteCpmProg(false, "8080ex1.com", Duration.ofMinutes(15));
        assertEquals(
                "8080 instruction exerciser (KR580VM80A CPU)\n" +
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
}