package net.micmu.mcmods.micwands.core;

import net.micmu.mcmods.micwands.items.ItemWandEnfeeble;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import net.micmu.mcmods.micwands.items.ItemWand;

/**
 * @author Micmu
 */
@EventBusSubscriber
final class EventHandlers {

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && (event.getEntity() instanceof EntityLiving))
            WandsCore.getInstance().initializeMob((EntityLiving) event.getEntity());
    }

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityTamed(AnimalTameEvent event) {
        final EntityAnimal entity = event.getAnimal();
        if (!entity.world.isRemote)
            WandsCore.getInstance().initializeNewTamedAnimal(entity, event.getTamer());
    }

    /**
     * @param event
     */
        @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if ((event.getSide() == Side.SERVER) && (event.getHand() == EnumHand.MAIN_HAND) && (event.getTarget() instanceof EntityLivingBase) && !event.isCanceled()) {
            final ItemStack stack = event.getEntityPlayer().getHeldItem(EnumHand.MAIN_HAND);
            if (!stack.isEmpty()) {
                final Item item = stack.getItem();
                if ((item instanceof ItemWand) && item.itemInteractionForEntity(stack, event.getEntityPlayer(), (EntityLivingBase)event.getTarget(), EnumHand.MAIN_HAND)) {
                    event.setResult(Result.DENY);
                    event.setCanceled(true);
                }
            }
        }
    } 
    /*@SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if ((event.getSide() == Side.SERVER) && (event.getTarget() instanceof EntityLivingBase)) {
            EntityPlayer caster = event.getEntityPlayer();
            EntityLivingBase target = (EntityLivingBase) event.getTarget();
            ItemStack stack = caster.getHeldItem(event.getHand());

            if (stack.getItem() instanceof ItemWandEnfeeble) {
                int result = WandsCore.getInstance().wandPacify(target, caster);
                // 处理结果...
            }
        }
    }*/

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGolemSetAttackTarget(LivingSetAttackTargetEvent event) {
        if ((event.getTarget() != null) && (event.getEntityLiving() instanceof EntityGolem) && !event.isCanceled()) {
            EntityGolem golem = (EntityGolem) event.getEntityLiving();
            if (((golem instanceof EntityIronGolem) || (golem instanceof EntitySnowman)) && WandsCore.getInstance().isEnfeebled(event.getTarget())) {
                if (golem.getAttackTarget() != null)
                    golem.setAttackTarget(null);
                if (golem.getRevengeTarget() != null)
                    golem.setRevengeTarget(null);
            }
        }
    }

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityHurt(LivingHurtEvent event) {
        // 修改调用点为新方法 isFollowing
        if (!event.getEntityLiving().world.isRemote && ((event.getSource() == DamageSource.FALL) || (event.getSource() == DamageSource.IN_WALL)) && !event.isCanceled() && (event.getAmount() > 0.0F)
                && WandsCore.getInstance().isFollowing(event.getEntityLiving())) {
            // FOLLOWING MOBS take fall/suffocation damage
            if (event.getSource() == DamageSource.FALL) {
                // Absorb 4 points of fall damage from following mobs. Helps them out a bit.
                event.setAmount(event.getAmount() - 4.0F);
                if (event.getAmount() <= 0.0F) {
                    event.setAmount(0.0F);
                    event.setResult(Result.DENY);
                    event.setCanceled(true);
                    return;
                }
            }
            if (WandsCore.getInstance().isNegateWarpDamage((EntityLiving) event.getEntityLiving())) {
                // Make mob invulnerable to fall/suffocation damage for few ticks after teleport. Helps them a bit.
                event.setResult(Result.DENY);
                event.setCanceled(true);
            }
        }
    }

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLootTableLoad(LootTableLoadEvent event) {
        LootTableHandler.getInstance().initTable(event.getName(), event.getTable());
    }

    /**
     * @param event
     */
    @SuppressWarnings("deprecation")
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (event.getType() == ChatType.GAME_INFO) {
            ITextComponent msg = event.getMessage();
            if (msg instanceof TextComponentTranslation) {
                String k = ((TextComponentTranslation) msg).getKey();
                if ((k != null) && k.startsWith("msg.micwands.", 0) && !net.minecraft.util.text.translation.I18n.canTranslate(k))
                    event.setCanceled(true);
            }
        }
    }

    /**
     *
     */
    private EventHandlers() {
    }
}
