package net.micmu.mcmods.micwands.core;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraft.entity.EnumCreatureAttribute;

import net.micmu.mcmods.micwands.MicWandsMod;

/**
 * @author Micmu
 */
public class WandsCore {
    // @formatter:off
    public static final String NBT_KEY_B_PACIFIED             = "Pacified";
    public static final String NBT_KEY_B_PERMANENT_FIRE       = "PermanentFire";
    public static final String NBT_KEY_I_ORIGINAL_AGE         = "OriginalAge";
    public static final String NBT_KEY_S_FOLLOW_PLAYER        = "FollowPlayer";
    public static final String NBT_KEY_S_FOLLOW_LAST_PLAYER   = "FollowLastPlayer";
    public static final String NBT_KEY_L_FOLLOW_LAST_TIME     = "FollowLastTime";
    // @formatter:off
    public static final String NBT_KEY_MiCOWNER               = "MicOwner"; // 修改访问修饰符为public
    public static final String NBT_KEY_S_FACTION_DISPLAY      = "MiCFactionDisplay";
    // @formatter:on
    private static final WandsCore INSTANCE = new WandsCore();
    private static final int PARMANENT_BABY_MAX_AGE = -500000000;
    private Map<Block, Object> noWarpBlocks_cache = null;
    private Field f_targetEntitySelector = null;
    private boolean failed_targetEntitySelector = false;
    private int errc_targetEntitySelector = 0;

    // 新增伤害免疫事件处理器
    // 新增阵营检查方法
    // 修改为调用FactionManager的方法
    boolean isSameFaction(EntityLivingBase entity1, EntityLivingBase entity2) {
        return FactionManager.getInstance(entity1.world).isSameFaction(entity1, entity2);
    }

    @SubscribeEvent
    public void onEntityDamage(LivingAttackEvent event) {
        Entity source = event.getSource().getTrueSource();
        if (source instanceof EntityLivingBase
                && event.getEntity() instanceof EntityLivingBase
                && source.world != null) {
            FactionManager.getInstance(source.world).onEntityDamage(event);
        }
    }

    /**
     * @return
     */
    public static WandsCore getInstance() {
        return INSTANCE;
    }

    /**
     * @param entity
     * @return
     */
    public boolean canSilence(EntityLivingBase entity) {
        return (entity instanceof EntityLiving) && !(entity instanceof EntityDragon) && !(entity instanceof EntityWither);
    }

    /**
     * @param entity
     * @return
     */
    public int wandSilence(EntityLivingBase entity) {
        if (!canSilence(entity))
            return -1;
        boolean b = !entity.isSilent();
        entity.setSilent(b);
        return b ? 1 : 0;
    }

    /**
     * @param entity
     * @return
     */
    public boolean canAge(EntityLivingBase entity) {
        return (entity instanceof EntityAgeable) && ((EntityAgeable) entity).isChild();
    }

    /**
     * @param entity
     * @return
     */
    public int wandAge(EntityLivingBase entity) {
        if (!canAge(entity))
            return -1;
        EntityAgeable mob = (EntityAgeable) entity;
        int age = mob.getGrowingAge();
        if (age > PARMANENT_BABY_MAX_AGE) {
            // Not perm baby -> turn on
            entity.getEntityData().setInteger(NBT_KEY_I_ORIGINAL_AGE, age);
            mob.setGrowingAge(-2000000000);
            return 1;
        } else {
            // Is perm baby -> turn off
            age = entity.getEntityData().getInteger(NBT_KEY_I_ORIGINAL_AGE);
            if (age > -2)
                age = -24000;
            mob.setGrowingAge(age);
            return 0;
        }
    }

    /**
     * @param entity
     * @return
     */
    public boolean isEnfeebled(EntityLivingBase entity) {
        if ((entity instanceof EntityLiving) && ((EntityLiving) entity).isNoDespawnRequired() && (entity instanceof IMob)) {
            final IAttributeInstance dmg = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            return ((dmg == null) || (dmg.getBaseValue() == 0.0D)) && (entity.getEntityData().getByte(NBT_KEY_B_PACIFIED) != (byte) 0);
        }
        return false;
    }

    /**
     * @return
     */
    public boolean canEnfeeble(EntityLivingBase entity) {
        return (entity instanceof EntityLiving) && (entity instanceof IMob) && !entity.isDead;
    }

    /**
     * @param entity
     * @return
     */
    public int wandEnfeeble(EntityLivingBase entity) {
        return canEnfeeble(entity) ? doEnfebleOrPacify((EntityLiving) entity, false, null) : -1; // 添加null作为第三个参数
    }

    /**
     * @param entity
     * @return
     */
    public boolean isPacified(EntityLivingBase entity) {
        if ((entity instanceof EntityLiving) && ((EntityLiving) entity).isNoDespawnRequired() && (entity instanceof IMob)) {
            return (entity.getEntityData().getByte(NBT_KEY_B_PACIFIED) == (byte) 1);
        }
        return false;
    }

    /**
     * @param entity
     * @return
     */
    public boolean canPacify(EntityLivingBase entity) {
        return canEnfeeble(entity);
    }

    /**
     * @param entity 被施法的生物实体
     * @param caster 施法玩家（从事件参数获取）
     * @return
     */
    public int wandPacify(EntityLivingBase entity, EntityPlayer caster) {
        if (entity.world.isRemote)
            return -1;
        return canPacify(entity) ? doEnfebleOrPacify((EntityLiving) entity, true, caster) : -1;
    }

    /**
     * @param entity
     * @param player
     * @return
     */
    // 修改 isFollowingPlayer 方法
    public boolean isFollowingPlayer(EntityLivingBase entity, EntityPlayer player) {
        if (player != null && canFollowing(entity, player)) { // 添加空指针检查
            String s = entity.getEntityData().getString(NBT_KEY_S_FOLLOW_PLAYER);
            return (s != null) && !s.isEmpty() && s.equals(player.getCachedUniqueIdString())
                    && isSameFaction(entity, player);
        }
        return false;
    }

    // 新增无玩家参数的基础检查方法
    public boolean isFollowing(EntityLivingBase entity) {
        return !entity.getEntityData().getString(NBT_KEY_S_FOLLOW_PLAYER).isEmpty();
    }

    /**
     * @param entity
     * @return
     */
    public boolean canFollowing(EntityLivingBase entity, EntityPlayer player) {
        if (entity == null || player == null || !(entity instanceof EntityLiving) || entity.isDead) {
            return false;
        }

        if (entity instanceof EntityIronGolem) {
            return ((EntityIronGolem) entity).isPlayerCreated();
        }
        return (entity instanceof EntityVillager)
                || ((entity instanceof IAnimals) && !(entity instanceof IMob))
                || (isPacified(entity)/* && isSameFaction(entity, player)*/);
    }

    public int wandFollowing(EntityLivingBase entity, EntityPlayer player) {
        return canFollowing(entity, player) ? doFollowing((EntityLiving) entity, player, false) : -1;
    }

    /**
     * @param entity
     * @return
     */
    public boolean isFire(EntityLivingBase entity) {
        return canFire(entity, null) && entity.getEntityData().getBoolean(NBT_KEY_B_PERMANENT_FIRE);
    }

    /**
     * @param entity
     * @return
     */
    public boolean canFire(EntityLivingBase entity, EntityPlayer player) {  // 新增player参数
        return !entity.isImmuneToFire() && canFollowing(entity, player);
    }

    /**
     * @param entity
     * @return
     */
    public int wandFire(EntityLivingBase entity, EntityPlayer player) {  // 新增player参数
        if (!canFire(entity, player))  // 使用新参数
            return -1;
        if (entity.getEntityData().getBoolean(NBT_KEY_B_PERMANENT_FIRE)) {
            // On. Turn off!
            entity.getEntityData().removeTag(NBT_KEY_B_PERMANENT_FIRE);
            updateMobAI((EntityLiving) entity, false, -1, 0);
            entity.extinguish();
            entity.removePotionEffect(MobEffects.FIRE_RESISTANCE);
            return 0;
        } else {
            // Off. Turn on!
            entity.getEntityData().setBoolean(NBT_KEY_B_PERMANENT_FIRE, true);
            updateMobAI((EntityLiving) entity, false, -1, 1);
            return 1;
        }
    }

    /**
     * @param mob
     */
    public void initializeMob(EntityLiving mob) {
        if (mob.isDead)
            return;
        //如果需要，更新暴徒AI。
        boolean p = isPacified(mob);
        boolean f = isFollowing(mob);
        boolean i = isFire(mob);
        if (p || f || i) {
            updateMobAI(mob, p, (f ? 1 : 0), (i ? 1 : 0));
            if (i)
                applyBurnEffects(mob);
        }
        //看看这个暴徒是否需要当婴儿养。
        if (mob instanceof EntityAgeable) {
            int a = ((EntityAgeable) mob).getGrowingAge();
            if ((a <= PARMANENT_BABY_MAX_AGE) && (a > -1900000000))
                ((EntityAgeable) mob).setGrowingAge(-2000000000);
        }
        //初始化铁傀儡
        if (mob instanceof EntityIronGolem)
            updateGolemAI((EntityIronGolem) mob, 3);
        if (mob instanceof EntitySnowman)
            updateGolemAI((EntitySnowman) mob, 1);
    }

    /**
     * @param animal
     * @param player
     */
    public void initializeNewTamedAnimal(EntityAnimal animal, EntityPlayer player) {
        if (isFollowing(animal) && (!(animal instanceof AbstractHorse) || (player == null) || player.getCachedUniqueIdString().equals(animal.getEntityData().getString(NBT_KEY_S_FOLLOW_PLAYER))))
            doFollowing(animal, player, true);
    }

    /**
     * @param creature
     * @return
     */
    public boolean warpToPlayerFollowing(EntityLivingBase creature, EntityPlayer player) {
        if (isFollowingPlayer(creature, player)) {
            EntityAITasks tasks = ((EntityLiving) creature).tasks;
            if (tasks != null)
                for (EntityAITasks.EntityAITaskEntry e : tasks.taskEntries)
                    if (e.action.getClass() == AIMobFollowPlayer.class)
                        return ((AIMobFollowPlayer) e.action).warpToPlayer(player);
        }
        return false;
    }

    /**
     * @param creature
     * @return
     */
    protected int applyBurnEffects(EntityLiving creature) {
        int tick = 18 + creature.getRNG().nextInt(6);
        creature.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, ((tick + 2) * 20), 0, false, false));
        creature.setFire(tick);
        return tick;
    }

    /**
     * @param creature
     */
    public boolean isNegateWarpDamage(EntityLiving creature) {
        EntityAITasks tasks = creature.tasks;
        if (tasks == null)
            return false;
        long p = 0L;
        for (EntityAITasks.EntityAITaskEntry e : tasks.taskEntries) {
            if (e.action.getClass() == AIMobFollowPlayer.class) {
                p = ((AIMobFollowPlayer) e.action).getLastWarp();
                break;
            }
        }
        return (creature.world.getTotalWorldTime() < p);
    }

    /**
     * @param bs
     * @return
     */
    protected boolean isAvoidWarpBlock(IBlockState bs) {
        Map<Block, Object> nb = this.noWarpBlocks_cache;
        if (nb == null)
            this.noWarpBlocks_cache = nb = initNoWarpBlocks();
        return nb.containsKey(bs.getBlock());
    }

    /**
     * @param mob
     * @param pacify
     * @return
     */
    private int doEnfebleOrPacify(EntityLiving mob, boolean pacify, EntityPlayer caster) {
        if (!(mob instanceof IMob))
            return -1;
        IAttributeInstance atr = mob.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        double v = 0.0D;
        if (atr != null)
            v = atr.getBaseValue();
        if (pacify) {
            if ((v == 0.0D) && (mob.getEntityData().getByte(NBT_KEY_B_PACIFIED) == (byte) 1))
                return 0;
        } else if (v == 0.0D) {
            if (mob.getEntityData().getByte(NBT_KEY_B_PACIFIED) != (byte) 0)
                return 0;
        }
        mob.enablePersistence();
        if ((atr != null) && (v != 0.0D))
            atr.setBaseValue(0.0D);
        atr = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE);
        if (atr != null) {
            v = atr.getBaseValue();
            if (v > 16.0D)
                atr.setBaseValue(16.0D);
        }
        if (pacify) {
            if (FactionManager.getInstance(mob.world).canAddMoreMembers(caster.getUniqueID().toString())) {
                FactionManager factionManager = FactionManager.getInstance(mob.world);
                factionManager.addTamedMobToPlayerFaction(mob, caster);
                mob.getEntityData().setByte(NBT_KEY_B_PACIFIED, (byte) 1);
                mob.getEntityData().setUniqueId(NBT_KEY_MiCOWNER, caster.getUniqueID());
                // 新增亡灵生物处理
                if (mob.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD) {
                    FactionManager.getInstance(mob.world).setupUndeadEquipment(mob, caster);
                }
                // 保持原有AI更新逻辑
                updateMobAI(mob, true, -1, -1);
            } else if (FactionManager.getInstance(mob.world).isFactionFull(caster.getUniqueID().toString())) {
                return -2; // 仅返回错误码，不发送消息
            }
        } else if (mob.getEntityData().getByte(NBT_KEY_B_PACIFIED) == (byte) 0) {
            mob.getEntityData().setByte(NBT_KEY_B_PACIFIED, (byte) 2);
            // 使它在虚弱时掉落手上的物品（武器）
            mob.setCanPickUpLoot(false);
            ItemStack stack = mob.getHeldItem(EnumHand.MAIN_HAND);
            if ((stack != null) && !stack.isEmpty()) {
                mob.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                BlockPos p = mob.getPosition();
                InventoryHelper.spawnItemStack(mob.world, p.getX(), p.getY(), p.getZ(), stack);
            }
        }
        return 1;
    }

    /**
     * @param mob
     * @param player
     * @param forceUnfollow
     * @return
     */
    private int doFollowing(EntityLiving mob, EntityPlayer player, boolean forceUnfollow) {
        String s;
        if (forceUnfollow || isFollowing(mob)) {
            // FOLLOW OFF
            s = mob.getEntityData().getString(NBT_KEY_S_FOLLOW_PLAYER);
            if (!forceUnfollow && ((s == null) || (player == null) || !s.equals(player.getCachedUniqueIdString())))
                return -2; // Not an owner!!
            mob.getEntityData().removeTag(NBT_KEY_S_FOLLOW_PLAYER);
            if (s != null) {
                mob.getEntityData().setString(NBT_KEY_S_FOLLOW_LAST_PLAYER, s);
                mob.getEntityData().setLong(NBT_KEY_L_FOLLOW_LAST_TIME, mob.getEntityWorld().getTotalWorldTime());
            } else {
                mob.getEntityData().removeTag(NBT_KEY_S_FOLLOW_LAST_PLAYER);
                mob.getEntityData().removeTag(NBT_KEY_L_FOLLOW_LAST_TIME);
            }
            updateMobAI(mob, false, 0, -1);
            return 3;
        } else if (player != null) {
            // FOLLOW ON
            //检查一下是否有驯养的动物！！
            UUID owner = null;
            boolean tamedNotHorse = false;
            if ((mob instanceof AbstractHorse) && ((AbstractHorse) mob).isTame()) {
                owner = ((AbstractHorse) mob).getOwnerUniqueId();
            } else if ((mob instanceof EntityTameable) && ((EntityTameable) mob).isTamed()) {
                owner = ((EntityTameable) mob).getOwnerId();
                tamedNotHorse = true;
            }
            if ((owner != null && !owner.equals(player.getUniqueID()))
                    || (mob.world != null
                    && FactionManager.getInstance(mob.world) != null
                    && FactionManager.getInstance(mob.world).getEntityFaction(mob.getPersistentID()) != null
                    && !FactionManager.getInstance(mob.world).isSameFaction(mob, player))) {
                return -2; // 不是所有者或有阵营生物且不同阵营!!
            }

            if (tamedNotHorse)
                return -3; //已经驯服了! !
            long now = mob.getEntityWorld().getTotalWorldTime();
            long ts = mob.getEntityData().getLong(NBT_KEY_L_FOLLOW_LAST_TIME);
            s = mob.getEntityData().getString(NBT_KEY_S_FOLLOW_LAST_PLAYER);
            if (s != null)
                mob.getEntityData().removeTag(NBT_KEY_S_FOLLOW_LAST_PLAYER);
            mob.getEntityData().setString(NBT_KEY_S_FOLLOW_PLAYER, player.getCachedUniqueIdString());
            mob.getEntityData().setLong(NBT_KEY_L_FOLLOW_LAST_TIME, now);
            updateMobAI(mob, false, 1, -1);
            if ((s != null) && s.equals(player.getCachedUniqueIdString()))
                return (Math.abs(now - ts) <= 6000L) ? 2 : 4;
            return 1;
        }
        return -3;
    }

    /**
     * @param creature
     * @param doPacify
     * @param doFollow
     * @param doFire   保持方法签名用于向后兼容
     */
    private void updateMobAI(EntityLiving creature, boolean doPacify, int doFollow, int doFire) {
        int i;
        EntityAITasks tasks;
        EntityAITasks.EntityAITaskEntry[] ar;
        if (doPacify) {
            // 清除所有攻击相关AI
            tasks = creature.targetTasks;
            ar = tasks.taskEntries.toArray(new EntityAITasks.EntityAITaskEntry[0]);
            for (EntityAITasks.EntityAITaskEntry entry : ar) {
                tasks.removeTask(entry.action);
            }

            // 添加阵营感知的目标选择，新增受伤检测
            Predicate<EntityLivingBase> predicate = new Predicate<EntityLivingBase>() {
                @Override
                public boolean apply(EntityLivingBase target) {
                    final int configDistance = ModConfig.aiMaxFollowDistance;
                    final double MAX_DIST_SQ = configDistance * configDistance;
                    EntityPlayer caster = creature.world.getPlayerEntityByUUID(
                            creature.getEntityData().getUniqueId(NBT_KEY_MiCOWNER)
                    );
                    // 基础条件：只攻击无阵营的敌对生物，有阵营生物保持中立，获取生物当前复仇目标
                    EntityLivingBase revengeTarget = creature.getRevengeTarget();
                    boolean isRevengeTarget = (revengeTarget != null) && (revengeTarget == target);
                    if (isRevengeTarget) {
                        return true; // 新增独立复仇判断
                    }
                    boolean baseCondition = !isInFaction(target) &&
                            (target instanceof IMob) &&
                            !(target instanceof EntityPigZombie) && // 新增排除僵尸猪人判断
                            (caster != null && caster.world != null); // 增加world非空判断
                    // 新增：检测玩家当前攻击目标
                    boolean isCasterTarget = (caster != null) &&
                            (caster.getLastAttackedEntity() == target);
                    // 新增：检测目标是否正在攻击玩家
                    boolean isAttackingCaster = (target.getRevengeTarget() == caster) || 
                                                (target.getLastAttackedEntity() == caster);
                    // 修改为服务端玩家列表检测来实现更准确的在线状态判断。以下是修改方案：
                    if (caster != null && caster.world != null) { // 增加双重空指针保护
                        MinecraftServer server = caster.world.getMinecraftServer();
                        if (server != null && server.getPlayerList().getPlayerByUUID(caster.getUniqueID()) != null) {
                            double creatureToCaster = creature.getDistanceSq(caster);
                            double targetToCaster = target.getDistanceSq(caster);

                            return (baseCondition || isCasterTarget || isAttackingCaster)  // 添加新条件
                                    && (creatureToCaster <= MAX_DIST_SQ)
                                    && (targetToCaster <= MAX_DIST_SQ);
                        }
                    }
                    return baseCondition || isCasterTarget || isAttackingCaster;  // 添加新条件
                }
            };
            // 修正类型转换问题翻译
            creature.targetTasks.addTask(0, new EntityAINearestAttackableTarget<EntityLivingBase>(
                    (EntityCreature) creature, EntityLivingBase.class, 16, true, false, predicate));
        }
        tasks = creature.tasks;
        ar = tasks.taskEntries.toArray(new EntityAITasks.EntityAITaskEntry[tasks.taskEntries.size()]);
        EntityAIBase b;
        EntityAIBase thePanic = null;
        int doStayPrio = (creature instanceof EntityCreature) ? 2 : -123;
        int doFollowPrio = 1;
        int doFirePrio = 0;
        int thePanicPrio = 0;
        for (i = ar.length - 1; i >= 0; i--) {
            b = ar[i].action;
            if (doPacify) {
                // 拆分判断条件：需要直接移除的AI类型
                if ((b instanceof EntitySpellcasterIllager.AIUseSpell)
                        || (b instanceof EntitySpellcasterIllager.AICastingApell)
                        || (ModConfig.removeCreeperSwellAI && b instanceof EntityAICreeperSwell) // 添加爬行者爆炸配置
                        || (b instanceof EntityAIAvoidEntity)) { // 新增逃避实体AI清除
                    // 直接移除不进行阵营判断
                    tasks.removeTask(b);
                } else if ((b instanceof EntityAIAttackRanged)
                        || (b instanceof EntityAIAttackMelee)
                        || (b instanceof EntityAILeapAtTarget)) {
                    // 保留需要阵营判断的攻击类AI
                    Predicate<EntityLivingBase> targetSelector = getTargetSelector(b);
                    if (isHostileToFaction(creature, targetSelector)) {
                        EntityLivingBase attackTarget = creature.getAttackTarget();
                        // 新增：获取施法者玩家
                        EntityPlayer caster = creature.world.getPlayerEntityByUUID(
                                creature.getEntityData().getUniqueId(NBT_KEY_MiCOWNER)
                        );
                        // 新增：如果是玩家当前攻击目标则保留AI
                        boolean isPlayerTarget = (caster != null) &&
                                (caster.getLastAttackedEntity() == attackTarget);
                        // 新增：检测复仇目标状态
                        boolean hasRevengeTarget = (creature.getRevengeTarget() != null);

                        if (isInFaction(attackTarget) &&
                                attackTarget instanceof IMob &&
                                !isPlayerTarget &&
                                !hasRevengeTarget) { // 仅在无复仇目标时移除
                            tasks.removeTask(b);
                        }
                    }
                } else if (b instanceof EntityAIMoveTowardsRestriction) {
                    // 保留原有移动限制逻辑
                    tasks.removeTask(b);
                    if (doStayPrio != -123)
                        doStayPrio = ar[i].priority;
                } else if (b.getClass() == AIMobStayInVillage.class) {
                    doStayPrio = -123;
                }
            }
            if (doFollow >= 0) {
                if (b instanceof EntityAIFollowOwner) {
                    if (doFollowPrio != -123)
                        doFollowPrio = ar[i].priority;
                } else if (b.getClass() == AIMobFollowPlayer.class) {
                    if (doFollow > 0) {
                        doFollowPrio = -123;
                    } else {
                        tasks.removeTask(b);
                    }
                }
            }
            if (doFire >= 0) {
                if (b instanceof EntityAIPanic) {
                    if (doFire > 0) {
                        thePanic = b;
                        thePanicPrio = ar[i].priority;
                        tasks.removeTask(b);
                    } else {
                        thePanic = null;
                        thePanicPrio = -123;
                    }
                } else if (b.getClass() == AIMobSelfImmolation.class) {
                    if (doFire > 0) {
                        doFirePrio = -123;
                    } else {
                        if (thePanicPrio != -123) {
                            thePanic = ((AIMobSelfImmolation) b).getPanicAI();
                            thePanicPrio = ((AIMobSelfImmolation) b).getPanicPriority();
                        }
                        tasks.removeTask(b);
                    }
                }
            }
            ar[i] = null;
        }
        if (doPacify && (doStayPrio != -123))
            tasks.addTask(doStayPrio, new AIMobStayInVillage((EntityCreature) creature));
        if ((doFollow > 0) && (doFollowPrio != -123))
            tasks.addTask(doFollowPrio, new AIMobFollowPlayer(creature));
        if ((doFire > 0) && (doFirePrio != -123))
            tasks.addTask(doFirePrio, new AIMobSelfImmolation(creature, thePanic, thePanicPrio));
        if ((doFire == 0) && (thePanicPrio != -123) && (thePanic != null))
            tasks.addTask(thePanicPrio, thePanic);
    }

    /**
     * @param golem
     * @param expPrio
     */
    private void updateGolemAI(EntityGolem golem, int expPrio) {
        // Modify EntityAINearestAttackableTarget attack AI to *ignore* enfeebled and pacified mobs.
        EntityAITasks tasks = golem.targetTasks;
        EntityAINearestAttackableTarget t = null;
        for (EntityAITasks.EntityAITaskEntry aie : tasks.taskEntries) {
            if ((aie.priority == expPrio) && (aie.action instanceof EntityAINearestAttackableTarget) && !failed_targetEntitySelector) {
                t = (EntityAINearestAttackableTarget) aie.action;
                break;
            }
        }
        boolean targetAImod = false;
        if (t != null) {
            final Field f = getEntitySelectorField();
            if (f != null) {
                try {
                    Predicate p = (Predicate) f.get(t);
                    if (p != null) {
                        if (!(p instanceof WrappedTargetPredicate))
                            f.set(t, new WrappedTargetPredicate(p));
                        targetAImod = true;
                    }
                } catch (Exception e) {
                    if (++errc_targetEntitySelector > 4) {
                        // 在完全放弃之前尝试5次，并放弃进一步尝试的所有逻辑
                        //（也许我们有一些修改EntityIronGolem从其他mod）
                        logGolemAIError("modify", e);
                        failed_targetEntitySelector = true;
                    }
                }
            }
        }
    }

    /**
     * @return
     */
    private Field getEntitySelectorField() {
        Field f = this.f_targetEntitySelector;
        if (f == null) {
            try {
                f = findField(EntityAINearestAttackableTarget.class, "targetEntitySelector", "field_82643_g");
                failed_targetEntitySelector = false;
                this.f_targetEntitySelector = f;
            } catch (Exception e) {
                //记录并只尝试一次。不要垃圾日志。
                failed_targetEntitySelector = true;
                logGolemAIError("access", e);
                return null;
            }
        }
        return f;
    }

    /**
     * @return
     */
    private Map<Block, Object> initNoWarpBlocks() {
        // Built-in. For now.
        final String[] noWarpBocks = {"biomesoplenty:poison", "biomesoplenty:sand", "biomesoplenty:blood", "biomesoplenty:blue_fire"};
        final IForgeRegistry<Block> registry = GameRegistry.findRegistry(Block.class);
        Map<Block, Object> out = null;
        if (registry != null) {
            Block b = null;
            for (String nme : noWarpBocks) {
                b = registry.getValue(new ResourceLocation(nme));
                if ((b != null) && (b != Blocks.AIR)) {
                    if (out == null)
                        out = new IdentityHashMap<>(8);
                    out.put(b, null);
                }
            }
        }
        if (out == null)
            return Collections.emptyMap();
        return out;
    }

    /**
     * @param s
     * @param e
     */
    private void logGolemAIError(String s, Throwable e) {
        MicWandsMod.LOG.error("Unable to " + s + " field targetEntitySelector in EntityAINearestAttackableTarget AI task class. Minecraft/Forge compatibility issue?", e);
        MicWandsMod.LOG.error("As a result, Iron Golems might act confused around enfeebled and pacified mobs (but not attack them).");
    }

    /**
     * @param clazz
     * @param fieldName
     * @param fieldObfName
     * @return
     * @throws NoSuchFieldException
     * @throws SecurityException
     */
    @Nonnull
    private Field findField(@Nonnull Class<?> clazz, @Nonnull String fieldName, @Nullable String fieldObfName) throws NoSuchFieldException, SecurityException {
        Field f;
        if (fieldObfName != null) {
            try {
                f = clazz.getDeclaredField(fieldObfName);
                f.setAccessible(true);
                return f;
            } catch (Exception e) {
            }
        }
        f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f;
    }

    /**
     *
     */
    private WandsCore() {
    }

    /**
     * @author Micmu
     */
    @SuppressWarnings("unchecked")
    private static final class WrappedTargetPredicate implements Predicate<EntityLivingBase> {
        private final Predicate orig;

        private WrappedTargetPredicate(Predicate o) {
            orig = o;
        }

        @Override
        public boolean apply(EntityLivingBase p) {
            return orig.apply(p) && !WandsCore.getInstance().isEnfeebled(p);
        }
    }

    /**
     * 获取AI任务的攻击目标选择器（通过反射）
     */
    @Nullable
    private Predicate<EntityLivingBase> getTargetSelector(EntityAIBase aiTask) {
        try {
            if (aiTask instanceof EntityAINearestAttackableTarget) {
                if (f_targetEntitySelector == null && !failed_targetEntitySelector) {
                    f_targetEntitySelector = EntityAINearestAttackableTarget.class.getDeclaredField("field_75307_e");
                    f_targetEntitySelector.setAccessible(true);
                }
                return (Predicate<EntityLivingBase>) f_targetEntitySelector.get(aiTask);
            }
        } catch (Exception e) {
            if (errc_targetEntitySelector++ > 5) {
                MicWandsMod.LOG.error("Cannot access targetEntitySelector field", e);
                failed_targetEntitySelector = true;
            }
        }
        return null;
    }

    /**
     * 判断目标是否符合阵营条件
     */
    public boolean isHostileToFaction(EntityLiving mob, @Nullable Predicate<EntityLivingBase> selector) {
        if (selector == null) return true;
        return selector.apply(null) && (mob.getAttackTarget() == null ||
                !isSameFaction(mob, mob.getAttackTarget()));
    }

    // 在现有的事件处理方法附近添加
    @SubscribeEvent
    public void onEntityTame(AnimalTameEvent event) {
        EntityLivingBase entity = event.getAnimal();
        EntityPlayer owner = event.getTamer();
        FactionManager.getInstance(entity.world).addTamedEntity(entity, owner.getPersistentID());
    }

    /**
     * 判断生物是否属于任何阵营
     */
    private boolean isInFaction(EntityLivingBase entity) {
        // 添加空指针保护
        if (entity == null || entity.world == null)
            return false;
        FactionManager fm = FactionManager.getInstance(entity.world);
        return fm != null && fm.getPlayerFaction(entity.getPersistentID()) != null;
    }

    /**
     * 判断是否和平生物（村民、动物等）
     */
    private boolean isPeacefulCreature(EntityLivingBase target) {
        return target instanceof EntityVillager ||
                target instanceof EntityAnimal ||
                target instanceof EntityGolem;
    }

    // 新增攻击目标检测方法
    @SubscribeEvent
    public void onLivingSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (event.getEntity() instanceof EntityTameable) {
            EntityTameable tameable = (EntityTameable) event.getEntity();
            if (tameable.isTamed() && event.getTarget() != null) {
                // 通过FactionManager获取目标阵营
                boolean hasFaction = FactionManager.getInstance(event.getEntity().world)
                        .getPlayerFaction(event.getTarget().getPersistentID()) != null;
                if (hasFaction) {
                    // 清除攻击目标和相关AI
                    tameable.setAttackTarget(null);
                    tameable.targetTasks.taskEntries.removeIf(task ->
                            task.action instanceof EntityAIAttackMelee ||
                                    task.action instanceof EntityAIAttackRanged
                    );
                }
            }
        }
    }
}
