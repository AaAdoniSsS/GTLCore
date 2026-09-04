package org.gtlcore.gtlcore.common.data.machines.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

public class MultiBlockMachineBStructure {

    public static final FactoryBlockPattern LARGE_FRAGMENT_WORLD_COLLECTION_MACHINE = FactoryBlockPattern.start()
            .aisle("AAA", "AXA", "XXX", "XXX", "XXX", "AXA", "AAA")
            .aisle("AOA", "XXX", "XXX", "XXX", "XXX", "XXX", "AIA")
            .aisle("AAA", "AXA", "XXX", "XSX", "XXX", "AXA", "AAA");

    public static final FactoryBlockPattern PRIMITIVE_VOID_ORE = FactoryBlockPattern.start()
            .aisle("XXX", "XXX", "XXX")
            .aisle("XXX", "XAX", "XXX")
            .aisle("XXX", "XSX", "XXX");

    public static final FactoryBlockPattern DESULFURIZER = FactoryBlockPattern.start()
            .aisle("CCCCCIIIIIII", "CCCCCILILILI", "CCCCCILILILI", "            ", "            ", "            ")
            .aisle("CCCCCIIIIIII", "CGCCCXXXXXXI", "CCCCCILILILI", "  CCCILILILI", "  CCCIIIIIII", "            ")
            .aisle("CCCCCIIIIIII", "CGGGGILILILI", "CCCCCILILILI", "  CXXXXXXXXI", "  CCCIIIIIII", "            ")
            .aisle("CCDDDIIIIIII", "CGDPDXXXXXXI", "CCDDDILILILI", "  DDDILILILI", "  DDDIIIIIII", "  DDD       ")
            .aisle("CCCCCIIIIIII", "CCDPDILILILI", "CCDPDILILILI", "  DPD       ", "  DPD       ", "  DDD       ")
            .aisle("  DDD       ", "  D~D       ", "  DDD       ", "  DDD       ", "  DDD       ", "  DDD       ");

    public static final FactoryBlockPattern WOOD_DISTILLATION = FactoryBlockPattern.start()
            .aisle("ABBBA IIIII IIIII ABBBA", "AAAAA IIIII IIIII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
            .aisle("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKKKI IKKKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DJD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
            .aisle("BBBBB IKKKI IKKKI BBBBB", "AAGAA IKGKI IKGKI AAGAA", "ADGDA IJGJI IJGJI ADGDA", " DGD   JGJ   JGJ  CDGDC", "CDGDC  JGJ   JGJC A G A", "A G A  JGJ  CJGJC  EGE ", " EGE  CJGJC J   J A G A", "A G A J   J J   J  EGE ", " EGE  J   J J   J A G A", "A G A J   J B   B  EGE ", " EGE  B   B  DGD  B   B", "A G A CDGDC CDGDC D   D", " EEE   DGD  D   GGD   D", "B   B CEEEC D   D D   D", "D   GGG   D D   D CD DC", "D   D D   D CD DC  DDD ", "CD DC D   D  DDD       ", " DDD  D   D            ", "      CD DC            ", "       DDD             ")
            .aisle("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKGKI IKGKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DGD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
            .aisle("ABBBA IKKKI IKKKI ABBBA", "AAAAA IIGII IIGII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
            .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("C   C  IIIIIIIII       ", "CCCCC  IGIIIIIGI       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIEIIIEII       ", " DDD    IEI IEI        ", "        IEI IEI        ", "        IHI IHI        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("       IILLLLLII       ", "CDDDC  IILLMLLII       ", "D   D  IILLLLLII       ", "D   D  IIIIIIIII       ", "D   D  IIIIIIIII       ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("                       ", "CDDDC                  ", "D   D                  ", "D   D                  ", "D   D                  ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .aisle("C   C                  ", "CCCCC                  ", "CDNDC                  ", "CDNDC                  ", "CDDDC                  ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ");

    public static final FactoryBlockPattern DIGESTION_TANK = FactoryBlockPattern.start()
            .aisle("#OOOOO#", "#YMMMY#", "##YYY##", "#######")
            .aisle("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
            .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .aisle("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .aisle("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
            .aisle("#OOSOO#", "#YMMMY#", "##YYY##", "#######");

    public static final FactoryBlockPattern DISSOLVING_TANK = FactoryBlockPattern.start()
            .aisle("X###X", "OOOOO", "XGGGX", "XGGGX", "#XXX#")
            .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .aisle("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .aisle("X###X", "OOSOO", "XGGGX", "XGGGX", "#XXX#");

    public static final FactoryBlockPattern GRAVITATION_SHOCKBURST = FactoryBlockPattern.start()
            .aisle("aaaaaaaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " bcccccb ", " bcc ccb ", " bc   cb ", " bcc ccb ", " bcccccb ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
            .aisle("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
            .aisle("aaaa~aaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa");
}
