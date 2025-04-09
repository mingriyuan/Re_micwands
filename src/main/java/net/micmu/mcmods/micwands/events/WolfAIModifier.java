package net.micmu.mcmods.micwands.events;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.micmu.mcmods.micwands.core.FactionManager;

public class WolfAIModifier {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWolfTarget(LivingSetAttackTargetEvent event) {
        // 仅处理未驯化的狼
        if (event.getEntity() instanceof EntityWolf && !((EntityWolf) event.getEntity()).isTamed()) {
            EntityWolf wolf = (EntityWolf) event.getEntity();
            EntityLivingBase target = event.getTarget();
            
            // 检测目标是否为亡灵生物且存在阵营
            if (target != null && 
                target.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD &&
                hasFaction(target)) {
                
                // 清除攻击目标
                wolf.setAttackTarget(null);
                
                // 移除近战攻击AI
                wolf.targetTasks.taskEntries.removeIf(task -> 
                    task.action instanceof EntityAIAttackMelee
                );
            }
        }
    }

    private boolean hasFaction(EntityLivingBase entity) {
        if (entity.world == null) return false;
        FactionManager fm = FactionManager.getInstance(entity.world);
        return fm != null && fm.getPlayerFaction(entity.getPersistentID()) != null;
    }
}