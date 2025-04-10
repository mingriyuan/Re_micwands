package net.micmu.mcmods.micwands.core;

import net.micmu.mcmods.micwands.MicWandsMod;

@net.minecraftforge.common.config.Config(modid = MicWandsMod.MODID)
public class ModConfig {
    @net.minecraftforge.common.config.Config.Comment("是否移除苦力怕膨胀AI（默认关闭）")
    public static boolean removeCreeperSwellAI = false;
    
    @net.minecraftforge.common.config.Config.Comment("阵营最大怪物成员数量(1-50)")
    @net.minecraftforge.common.config.Config.RangeInt(min = 1, max = 50)
    public static int factionIMobMaxMembers = 15;
    
    @net.minecraftforge.common.config.Config.Comment("AI最大跟随距离（方块数，1-100）")
    @net.minecraftforge.common.config.Config.RangeInt(min = 1, max = 64)
    public static int aiMaxFollowDistance = 16; 
}