package net.micmu.mcmods.micwands;

import net.micmu.mcmods.micwands.core.WandsCore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.micmu.mcmods.micwands.events.WolfAIModifier;

/**
 * @author Micmu 重生
 */
@Mod(modid = MicWandsMod.MODID, name = MicWandsMod.NAME, version = MicWandsMod.VERSION, acceptedMinecraftVersions = MicWandsMod.ACCEPTED_MINECRAFT_VERSIONS, acceptableRemoteVersions = MicWandsMod.ACCEPTED_REMOTE_VERSIONS)
public class MicWandsMod {
    public static final String MODID = "micwands";
    public static final String NAME = "Re:Mob Control Wands";
    public static final String VERSION = "1.0.5";
    public static final String ACCEPTED_MINECRAFT_VERSIONS = "[1.12,1.13)";
    public static final String ACCEPTED_REMOTE_VERSIONS = "[1.0,1.1)";

    @Mod.Instance(MODID)
    public static MicWandsMod INSTANCE;

    public static final Logger LOG = LogManager.getLogger(MODID);

    // 在初始化方法中添加注册
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new WolfAIModifier());
        MinecraftForge.EVENT_BUS.register(WandsCore.getInstance());
    }
}
