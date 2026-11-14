package ru.avsh.specialist.mx.units;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.avsh.specialist.mx.units.TestHelper.loadAndExecuteCpmFile;

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

    // Тяжелый и долгий тест, но очень точный
    @Test
    void i8080EXER_test() {
        final String text = loadAndExecuteCpmFile(false, "8080EXER.COM", Duration.ofMinutes(5));
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
}