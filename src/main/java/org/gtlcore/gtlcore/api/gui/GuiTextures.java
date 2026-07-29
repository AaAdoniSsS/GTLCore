package org.gtlcore.gtlcore.api.gui;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dust;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dustSmall;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Gold;

public class GuiTextures {

    public static final ResourceTexture BUTTON_VISIBLE = new ResourceTexture("gtlcore:textures/guis/button_visible.png");
    public static final ResourceTexture BUTTON_DISABLE_BYPRODUCT = new ResourceTexture("gtlcore:textures/guis/button_disable_byproduct.png");
    public static final IGuiTexture BATCH_PROCESSING_DISABLED = new ItemStackTexture(ChemicalHelper.get(dustSmall, Gold));
    public static final IGuiTexture BATCH_PROCESSING_ENABLED = new ItemStackTexture(ChemicalHelper.get(dust, Gold));
}
