package net.micmu.mcmods.micwands.items;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.EnumDifficulty;

import net.micmu.mcmods.micwands.core.ModConfig;
import net.micmu.mcmods.micwands.core.WandsCore;

/**
 *
 * @author Micmu
 */
public class ItemWandEnfeeble extends ItemWand {
    private final boolean pacify;

    /**
     *
     * @param name
     * @param pacify
     */
    public ItemWandEnfeeble(String name, boolean pacify) {
        super(name);
        this.pacify = pacify;
    }

    /**
     *
     */
    @Override
    protected int onWandInteract(EntityPlayer player, EntityLivingBase entity) {
        final WandsCore wc = WandsCore.getInstance();
        // 新功能调用
        final int r = pacify ? wc.wandPacify(entity, player) : wc.wandEnfeeble(entity);
        
        // 从旧代码恢复的反馈逻辑
        if (r >= 0) {
            player.sendStatusMessage(new TextComponentTranslation("msg.micwands." + (pacify ? "pacify." : "enfeeble.") + r, entity.getName()), true);
            if (r > 0) {
                if (!player.isCreative() && (player.getEntityWorld().getDifficulty() != EnumDifficulty.PEACEFUL)) {
                    // 应用减益效果
                    if (player.isPotionActive(MobEffects.SLOWNESS))
                        player.removePotionEffect(MobEffects.SLOWNESS);
                    player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 400 + player.getRNG().nextInt(400), 4));
                    
                    if (player.isPotionActive(MobEffects.WEAKNESS))
                        player.removePotionEffect(MobEffects.WEAKNESS);
                    player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 600 + player.getRNG().nextInt(600), 1));
                }
                return 8 + player.getRNG().nextInt(8);
            }
            return 0;
        } else {
            // 新增错误码处理
            if (r == -2) {
                player.sendStatusMessage(new TextComponentTranslation("msg.micwands.faction.full", ModConfig.factionIMobMaxMembers), true);
            } else {
                player.sendStatusMessage(new TextComponentTranslation("msg.micwands.err.worksonly", 
                    new TextComponentTranslation("msg.micwands.err.mob")), true);
            }
            return 0;
        }
    }
}
