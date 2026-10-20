package com.pixelmoneffectiveness.client.autofarm.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public class ForageLootEntry {
    public final String id;
    public final ForageLootCategory category;
    public final ItemStack stack;
    public final String localizedName;
    public final String englishName;
    public final boolean isValid;

    public ForageLootEntry(String id, ForageLootCategory category) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.category = category;

        ItemStack s = ItemStack.EMPTY;
        String loc = id;
        String eng = id;
        boolean valid = false;

        try {
            ResourceLocation rl = ResourceLocation.parse(this.id);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                s = new ItemStack(item);
                Component comp = s.getHoverName();
                loc = (comp != null) ? comp.getString() : rl.getPath();
                eng = rl.getPath().replace('_', ' ');
                valid = true;
            }
        } catch (Throwable ignored) {}

        this.stack = s;
        this.localizedName = loc;
        this.englishName = eng;
        this.isValid = valid;
    }
}
