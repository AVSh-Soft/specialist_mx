package ru.avsh.specialist.mx.units;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.avsh.specialist.mx.units.TestHelper.loadAndExecuteCpmProg;

/**
 * zexdoc и zexall — «Z80 instruction exerciser» Фрэнка Крингла: прогоняют десятки тысяч
 * комбинаций операндов через каждую команду и сверяют CRC состояния с эталонным Zilog Z80.
 * <p>
 * zexdoc сверяет только документированные флаги, zexall — ещё и недокументированные F5/F3,
 * поэтому zexall строже. Обе программы выводят одинаковый список групп, отличаются только
 * проверяемые биты, так что ожидаемый текст у них общий.
 */
class CpuZ80Test {
    /**
     * Тайм-аут прогона zexdoc/zexall. По реальному Z80 время измеряется часами,
     * поэтому запас взят с большим избытком относительно текущих ~12 минут.
     */
    private static final Duration Z80_EXERCISER_TIMEOUT = Duration.ofMinutes(40);
    /**
     * Эталонный вывод zexdoc/zexall: заголовок, имя каждой группы с точками,
     * строка {@code "  OK"} в той же строке и финальная строка {@code "Tests complete"}.
     * <p>
     * Перевод строки после {@code "Tests complete"} программой не печатается,
     * поэтому в эталоне его тоже нет.
     */
    private static final String Z80_EXERCISER_RESULT =
            "Z80 instruction exerciser\n" +
                    "<adc,sbc> hl,<bc,de,hl,sp>....  OK\n" +
                    "add hl,<bc,de,hl,sp>..........  OK\n" +
                    "add ix,<bc,de,ix,sp>..........  OK\n" +
                    "add iy,<bc,de,iy,sp>..........  OK\n" +
                    "aluop a,nn....................  OK\n" +
                    "aluop a,<b,c,d,e,h,l,(hl),a>..  OK\n" +
                    "aluop a,<ixh,ixl,iyh,iyl>.....  OK\n" +
                    "aluop a,(<ix,iy>+1)...........  OK\n" +
                    "bit n,(<ix,iy>+1).............  OK\n" +
                    "bit n,<b,c,d,e,h,l,(hl),a>....  OK\n" +
                    "cpd<r>........................  OK\n" +
                    "cpi<r>........................  OK\n" +
                    "<daa,cpl,scf,ccf>.............  OK\n" +
                    "<inc,dec> a...................  OK\n" +
                    "<inc,dec> b...................  OK\n" +
                    "<inc,dec> bc..................  OK\n" +
                    "<inc,dec> c...................  OK\n" +
                    "<inc,dec> d...................  OK\n" +
                    "<inc,dec> de..................  OK\n" +
                    "<inc,dec> e...................  OK\n" +
                    "<inc,dec> h...................  OK\n" +
                    "<inc,dec> hl..................  OK\n" +
                    "<inc,dec> ix..................  OK\n" +
                    "<inc,dec> iy..................  OK\n" +
                    "<inc,dec> l...................  OK\n" +
                    "<inc,dec> (hl)................  OK\n" +
                    "<inc,dec> sp..................  OK\n" +
                    "<inc,dec> (<ix,iy>+1).........  OK\n" +
                    "<inc,dec> ixh.................  OK\n" +
                    "<inc,dec> ixl.................  OK\n" +
                    "<inc,dec> iyh.................  OK\n" +
                    "<inc,dec> iyl.................  OK\n" +
                    "ld <bc,de>,(nnnn).............  OK\n" +
                    "ld hl,(nnnn)..................  OK\n" +
                    "ld sp,(nnnn)..................  OK\n" +
                    "ld <ix,iy>,(nnnn).............  OK\n" +
                    "ld (nnnn),<bc,de>.............  OK\n" +
                    "ld (nnnn),hl..................  OK\n" +
                    "ld (nnnn),sp..................  OK\n" +
                    "ld (nnnn),<ix,iy>.............  OK\n" +
                    "ld <bc,de,hl,sp>,nnnn.........  OK\n" +
                    "ld <ix,iy>,nnnn...............  OK\n" +
                    "ld a,<(bc),(de)>..............  OK\n" +
                    "ld <b,c,d,e,h,l,(hl),a>,nn....  OK\n" +
                    "ld (<ix,iy>+1),nn.............  OK\n" +
                    "ld <b,c,d,e>,(<ix,iy>+1)......  OK\n" +
                    "ld <h,l>,(<ix,iy>+1)..........  OK\n" +
                    "ld a,(<ix,iy>+1)..............  OK\n" +
                    "ld <ixh,ixl,iyh,iyl>,nn.......  OK\n" +
                    "ld <bcdehla>,<bcdehla>........  OK\n" +
                    "ld <bcdexya>,<bcdexya>........  OK\n" +
                    "ld a,(nnnn) / ld (nnnn),a.....  OK\n" +
                    "ldd<r> (1)....................  OK\n" +
                    "ldd<r> (2)....................  OK\n" +
                    "ldi<r> (1)....................  OK\n" +
                    "ldi<r> (2)....................  OK\n" +
                    "neg...........................  OK\n" +
                    "<rrd,rld>.....................  OK\n" +
                    "<rlca,rrca,rla,rra>...........  OK\n" +
                    "shf/rot (<ix,iy>+1)...........  OK\n" +
                    "shf/rot <b,c,d,e,h,l,(hl),a>..  OK\n" +
                    "<set,res> n,<bcdehl(hl)a>.....  OK\n" +
                    "<set,res> n,(<ix,iy>+1).......  OK\n" +
                    "ld (<ix,iy>+1),<b,c,d,e>......  OK\n" +
                    "ld (<ix,iy>+1),<h,l>..........  OK\n" +
                    "ld (<ix,iy>+1),a..............  OK\n" +
                    "ld (<bc,de>),a................  OK\n" +
                    "Tests complete";

    @Test
    void z80type_test() {
        final String text = loadAndExecuteCpmProg(true, "z80type.com", Duration.ofMinutes(1), "/D");
        assertEquals(
                "Z80 Processor Type Detection (C) 2024 Sergey Kiselev\n" +
                        "Raw results:       Z180: 00 CMOS: FE U880: 00 XF/YF: FF\n" +
                        "XF/YF flags test:  C000C000\n" +
                        "Detected CPU type: Zilog Z80, Zilog Z08400 or similar NMOS CPU\n" +
                        "                   Mostek MK3880N, SGS/ST Z8400, Sharp LH0080A, KR1858VM1\n",
                text);
    }

    // Тяжелый и долгий тест: около 12 минут на текущей реализации CpuZ80
    @Test
    void zexdoc_test() {
        final String text = loadAndExecuteCpmProg(true, "zexdoc.com", Z80_EXERCISER_TIMEOUT);
        assertEquals(Z80_EXERCISER_RESULT, text);
    }

    // Тяжелый и долгий тест: около 12 минут на текущей реализации CpuZ80
    @Test
    void zexall_test() {
        final String text = loadAndExecuteCpmProg(true, "zexall.com", Z80_EXERCISER_TIMEOUT);
        assertEquals(Z80_EXERCISER_RESULT, text);
    }
}
