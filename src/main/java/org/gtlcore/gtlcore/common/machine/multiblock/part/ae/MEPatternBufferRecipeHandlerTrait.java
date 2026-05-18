package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import com.gregtechceu.gtceu.api.capability.recipe.*;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

public class MEPatternBufferRecipeHandlerTrait extends MEPatternBufferRecipeHandlerTraitBase {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MEPatternBufferPartMachine.class);

    public MEPatternBufferRecipeHandlerTrait(MEPatternBufferPartMachine ioBuffer, IO io) {
        super(ioBuffer, io);
    }

    @Override
    protected MEItemInputHandlerBase createMEItemHandler(IO io) {
        return new MEItemInputHandler(getMachine(), io);
    }

    @Override
    protected MEFluidHandlerBase createMEFluidHandler(IO io) {
        return new MEFluidHandler(getMachine(), io);
    }

    @Override
    public MEPatternBufferPartMachine getMachine() {
        return (MEPatternBufferPartMachine) super.getMachine();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private static class MEItemInputHandler extends MEItemInputHandlerBase {

        public MEItemInputHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        public MEPatternBufferPartMachine getMachine() {
            return (MEPatternBufferPartMachine) this.machine;
        }
    }

    private static class MEFluidHandler extends MEFluidHandlerBase {

        public MEFluidHandler(MEPatternBufferPartMachine machine, IO io) {
            super(machine, io);
        }

        public MEPatternBufferPartMachine getMachine() {
            return (MEPatternBufferPartMachine) this.machine;
        }
    }
}
