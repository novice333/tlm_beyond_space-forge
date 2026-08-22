package com.github.tlmbeyondspace.data;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.github.tlmbeyondspace.registry.ModItems;

@LittleMaidExtension
public final class BeyondSpaceExtension implements ILittleMaid {
    @Override
    public void registerTaskData(TaskDataRegister register) {
        register.register(MaidRescueProfileData.KEY);
        register.register(MaidCombatPreferenceData.KEY);
        register.register(MaidRescueSessionData.KEY);
        register.register(MaidWeaponSwapData.KEY);
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.MAID_WEAPON_CASE.get(), ModItems.MAID_WEAPON_CASE.get());
    }
}
