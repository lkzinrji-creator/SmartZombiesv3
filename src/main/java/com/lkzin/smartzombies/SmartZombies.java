package com.lkzin.smartzombies;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(SmartZombies.MODID)
public class SmartZombies {
    public static final String MODID = "smartzombies";

    public SmartZombies() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Zombie zombie
                && !zombie.getTags().contains("SmartZombiesV3")) {
            zombie.addTag("SmartZombiesV3");
            zombie.goalSelector.addGoal(2, new SmartZombieGoal(zombie));
        }
    }
}
