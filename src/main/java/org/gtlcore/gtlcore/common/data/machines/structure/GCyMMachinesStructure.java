package org.gtlcore.gtlcore.common.data.machines.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

public class GCyMMachinesStructure {

    public static final FactoryBlockPattern LARGE_MACERATION_TOWER = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXXXX", "XXXXX")
            .aisle("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .aisle("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .aisle("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .aisle("XXXXX", "XXXXX", "XXSXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_CHEMICAL_BATH = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXXXX")
            .aisle("XXXXX", "XTTTX", "X   X")
            .aisle("XXXXX", "X   X", "X   X")
            .aisle("XXXXX", "X   X", "X   X")
            .aisle("XXXXX", "X   X", "X   X")
            .aisle("XXXXX", "XTTTX", "X   X")
            .aisle("XXXXX", "XXSXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_CENTRIFUGE = FactoryBlockPattern.start()
            .aisle("#XXX#", "XXXXX", "#XXX#")
            .aisle("XXXXX", "XAPAX", "XXXXX")
            .aisle("XXXXX", "XPAPX", "XXXXX")
            .aisle("XXXXX", "XAPAX", "XXXXX")
            .aisle("#XXX#", "XXSXX", "#XXX#");

    public static final FactoryBlockPattern LARGE_MIXER = FactoryBlockPattern.start()
            .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#XXX#", "##F##")
            .aisle("XXXXX", "XAPAX", "XAAAX", "XAPAX", "XAAAX", "##F##")
            .aisle("XXXXX", "XPPPX", "XAPAX", "XPPPX", "XAGAX", "FFGFF")
            .aisle("XXXXX", "XAPAX", "XAAAX", "XAPAX", "XAAAX", "##F##")
            .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#XXX#", "##F##");

    public static final FactoryBlockPattern LARGE_ELECTROLYZER = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXXXX")
            .aisle("XXXXX", "XCCCX", "XCCCX")
            .aisle("XXXXX", "XCCCX", "XCCCX")
            .aisle("XXXXX", "XXSXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_ELECTROMAGNET = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXXXX")
            .aisle("XCXCX", "XCXCX", "XCXCX")
            .aisle("XCXCX", "XCXCX", "XCXCX")
            .aisle("XXXXX", "XXSXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_PACKER = FactoryBlockPattern.start()
            .aisle("XXX", "XXX", "XXX")
            .aisle("XXX", "XAX", "XXX")
            .aisle("XXX", "XAX", "XXX")
            .aisle("XXX", "XAX", "XXX")
            .aisle("XXX", "XAX", "XXX")
            .aisle("XXX", "XSX", "XXX");

    public static final FactoryBlockPattern LARGE_ASSEMBLER = FactoryBlockPattern.start()
            .aisle("XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX")
            .aisle("XXXXXXXXX", "XAAAXAAAX", "XGGGXXXXX")
            .aisle("XXXXXXXXX", "XGGGXXSXX", "XGGGX###X");

    public static final FactoryBlockPattern LARGE_CIRCUIT_ASSEMBLER = FactoryBlockPattern.start()
            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX")
            .aisle("XXXXXXX", "XPPPPPX", "XGGGGGX")
            .aisle("XXXXXXX", "XAAAAPX", "XGGGGGX")
            .aisle("XXXXXXX", "XTTTTXX", "XXXXXXX")
            .aisle("#####XX", "#####SX", "#####XX");

    public static final FactoryBlockPattern LARGE_ARC_SMELTER = FactoryBlockPattern.start()
            .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#")
            .aisle("XXXXX", "XCACX", "XCACX", "XXXXX")
            .aisle("XXXXX", "XAAAX", "XAAAX", "XXMXX")
            .aisle("XXXXX", "XACAX", "XACAX", "XXXXX")
            .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#");

    public static final FactoryBlockPattern LARGE_ENGRAVING_LASER = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXGXX", "XXGXX", "XXXXX")
            .aisle("XXXXX", "XAAAX", "XAAAX", "XKKKX")
            .aisle("XXXXX", "GAAAG", "GACAG", "XKXKX")
            .aisle("XXXXX", "XAAAX", "XAAAX", "XKKKX")
            .aisle("XXSXX", "XXGXX", "XXGXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_SIFTING_FUNNEL = FactoryBlockPattern.start()
            .aisle("#X#X#", "#X#X#", "#XXX#", "XXXXX", "#XXX#")
            .aisle("XXXXX", "XAXAX", "XKKKX", "XKKKX", "X###X")
            .aisle("#XXX#", "#XAX#", "XKKKX", "XKKKX", "X###X")
            .aisle("XXXXX", "XAXAX", "XKKKX", "XKKKX", "X###X")
            .aisle("#X#X#", "#X#X#", "#XSX#", "XXXXX", "#XXX#");

    public static final FactoryBlockPattern BLAST_ALLOY_SMELTER = FactoryBlockPattern.start()
            .aisle("#XXX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#")
            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXMXX")
            .aisle("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
            .aisle("#XSX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#");

    public static final FactoryBlockPattern LARGE_AUTOCLAVE = FactoryBlockPattern.start()
            .aisle("XXX", "XXX", "XXX")
            .aisle("XXX", "XTX", "XXX")
            .aisle("XXX", "XTX", "XXX")
            .aisle("XXX", "XTX", "XXX")
            .aisle("XXX", "XSX", "XXX");

    public static final FactoryBlockPattern LARGE_MATERIAL_PRESS = FactoryBlockPattern.start()
            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX")
            .aisle("XXXXXXX", "XAXGGGX", "XXXXXXX")
            .aisle("XXXXXXX", "XSXCCCX", "XXXXXXX");

    public static final FactoryBlockPattern LARGE_BREWER = FactoryBlockPattern.start()
            .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#####")
            .aisle("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
            .aisle("XXXXX", "XCPCX", "XAPAX", "XAPAX", "#XMX#")
            .aisle("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
            .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#####");

    public static final FactoryBlockPattern LARGE_CUTTER = FactoryBlockPattern.start()
            .aisle("XXXXXXX", "XXXXXXX", "XXXXXXX", "##XXXXX")
            .aisle("XXXXXXX", "XAXCCCX", "XXXAAAX", "##XXXXX")
            .aisle("XXXXXXX", "XAXCCCX", "XXXAAAX", "##XXXXX")
            .aisle("XXXXXXX", "XSXGGGX", "XXXGGGX", "##XXXXX");

    public static final FactoryBlockPattern LARGE_EXTRACTOR = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXXXX")
            .aisle("XXXXX", "XCACX", "XXXXX")
            .aisle("XXXXX", "XXSXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_EXTRUDER = FactoryBlockPattern.start()
            .aisle("##XXX", "##XXX", "##XXX")
            .aisle("##XXX", "##XPX", "##XGX").setRepeatable(2)
            .aisle("XXXXX", "XXXPX", "XXXGX")
            .aisle("XXXXX", "XAXPX", "XXXGX")
            .aisle("XXXXX", "XSXXX", "XXXXX");

    public static final FactoryBlockPattern LARGE_SOLIDIFIER = FactoryBlockPattern.start()
            .aisle("#XXX#", "#XXX#", "#XXX#", "#XXX#")
            .aisle("XXXXX", "XCACX", "XCACX", "XXXXX")
            .aisle("XXXXX", "XAAAX", "XAAAX", "XXXXX")
            .aisle("XXXXX", "XCACX", "XCACX", "XXXXX")
            .aisle("#XXX#", "#XSX#", "#XXX#", "#XXX#");

    public static final FactoryBlockPattern LARGE_WIREMILL = FactoryBlockPattern.start()
            .aisle("XXXXX", "XXXXX", "XXX##")
            .aisle("XXXXX", "X#CCX", "XXXXX")
            .aisle("XXXXX", "XSXXX", "XXX##");

    public static final FactoryBlockPattern MEGA_BLAST_FURNACE = FactoryBlockPattern.start()
            .aisle("##XXXXXXXXX##", "##XXXXXXXXX##", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .aisle("#XXXXXXXXXXX#", "#XXXXXXXXXXX#", "###F#####F###", "###F#####F###", "###FFFFFFF###",
                    "#############", "#############", "#############", "#############", "#############",
                    "####FFFFF####", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .aisle("XXXXXXXXXXXXX", "XXXXVVVVVXXXX", "##F#######F##", "##F#######F##", "##FFFHHHFFF##",
                    "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##",
                    "##FFFHHHFFF##", "#############", "#############", "#############", "#############",
                    "#############", "###TTTTTTT###")
            .aisle("XXXXXXXXXXXXX", "XXXXXXXXXXXXX", "#F####P####F#", "#F####P####F#", "#FFHHHPHHHFF#",
                    "######P######", "######P######", "######P######", "######P######", "######P######",
                    "##FHHHPHHHF##", "######P######", "######P######", "######P######", "######P######",
                    "######P######", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BBPBB####", "####TITIT####", "#FFHHHHHHHFF#",
                    "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####BITIB####",
                    "#FFHHHHHHHFF#", "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####",
                    "####BITIB####", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BAAAB####", "####IAAAI####", "#FHHHAAAHHHF#",
                    "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####", "####IAAAI####",
                    "#FHHHAAAHHHF#", "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####",
                    "####IAAAI####", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "###PPAAAPP###", "###PTAAATP###", "#FHPHAAAHPHF#",
                    "###PTAAATP###", "###PCAAACP###", "###PCAAACP###", "###PCAAACP###", "###PTAAATP###",
                    "#FHPHAAAHPHF#", "###PTAAATP###", "###PCAAACP###", "###PCAAACP###", "###PCAAACP###",
                    "###PTAAATP###", "##TPPPMPPPT##")
            .aisle("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BAAAB####", "####IAAAI####", "#FHHHAAAHHHF#",
                    "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####", "####IAAAI####",
                    "#FHHHAAAHHHF#", "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####",
                    "####IAAAI####", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BBPBB####", "####TITIT####", "#FFHHHHHHHFF#",
                    "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####BITIB####",
                    "#FFHHHHHHHFF#", "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####",
                    "####BITIB####", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXXXXXXXXXXXX", "#F####P####F#", "#F####P####F#", "#FFHHHPHHHFF#",
                    "######P######", "######P######", "######P######", "######P######", "######P######",
                    "##FHHHPHHHF##", "######P######", "######P######", "######P######", "######P######",
                    "######P######", "##TTTTPTTTT##")
            .aisle("XXXXXXXXXXXXX", "XXXXVVVVVXXXX", "##F#######F##", "##F#######F##", "##FFFHHHFFF##",
                    "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##",
                    "##FFFHHHFFF##", "#############", "#############", "#############", "#############",
                    "#############", "###TTTTTTT###")
            .aisle("#XXXXXXXXXXX#", "#XXXXXXXXXXX#", "###F#####F###", "###F#####F###", "###FFFFFFF###",
                    "#############", "#############", "#############", "#############", "#############",
                    "####FFFFF####", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .aisle("##XXXXXXXXX##", "##XXXXSXXXX##", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############");

    public static final FactoryBlockPattern MEGA_VACUUM_FREEZER = FactoryBlockPattern.start()
            .aisle("XXXXXXX#KKK", "XXXXXXX#KVK", "XXXXXXX#KVK", "XXXXXXX#KVK", "XXXXXXX#KKK", "XXXXXXX####",
                    "XXXXXXX####")
            .aisle("XXXXXXX#KVK", "XPPPPPPPPPV", "XPAPAPX#VPV", "XPPPPPPPPPV", "XPAPAPX#KVK", "XPPPPPX####",
                    "XXXXXXX####")
            .aisle("XXXXXXX#KVK", "XPAPAPXAVPV", "XAAAAAX#VPV", "XPAAAPX#VPV", "XAAAAAX#KVK", "XPAPAPX####",
                    "XXXXXXX####")
            .aisle("XXXXXXX#KVK", "XPAPAPPPPPV", "XAAAAAX#VPV", "XPAAAPPPPPV", "XAAAAAX#KVK", "XPAPAPX####",
                    "XXXXXXX####")
            .aisle("XXXXXXX#KKK", "XPPPPPX#KVK", "XPA#APX#KVK", "XPAAAPX#KVK", "XPAAAPX#KKK", "XPPPPPX####",
                    "XXXXXXX####")
            .aisle("#XXXXX#####", "#XXSXX#####", "#XGGGX#####", "#XGGGX#####", "#XGGGX#####", "#XXXXX#####",
                    "###########");

    public static final FactoryBlockPattern LARGE_DISTILLERY = FactoryBlockPattern.start(RIGHT, BACK, UP)
            .aisle("#YYY#", "YYYYY", "YYYYY", "YYYYY", "#YYY#")
            .aisle("#YSY#", "YAAAY", "YAAAY", "YAAAY", "#YYY#")
            .aisle("##X##", "#XAX#", "XAPAX", "#XAX#", "##X##").setRepeatable(1, 12)
            .aisle("#####", "#ZZZ#", "#ZCZ#", "#ZZZ#", "#####");
}
