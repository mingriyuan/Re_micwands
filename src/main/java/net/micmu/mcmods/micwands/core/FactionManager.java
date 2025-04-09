package net.micmu.mcmods.micwands.core;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.server.MinecraftServer;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

// 在类声明上方添加事件总线订阅
@net.minecraftforge.fml.common.Mod.EventBusSubscriber
public class FactionManager {
    private static final String DATA_NAME = "MicWandsFactions";
    private final World world;
    private final FactionData data;

    private FactionManager(World world) {
        this.world = world;
        this.data = (FactionData) world.loadData(FactionData.class, DATA_NAME);
        if (this.data == null) {
            world.setData(DATA_NAME, new FactionData(DATA_NAME));
        }
    }

    public static FactionManager getInstance(World world) {
        return new FactionManager(world);
    }

    public void createFaction(String name, UUID founder) {
        data.factionMembers.put(name, new HashSet<UUID>());
        data.playerFactions.put(founder, name);
        data.markDirty();
    }

    // 新增显示名称设置方法
    private void setupEntityDisplayName(EntityLivingBase entity, String factionName) {
        if (entity instanceof EntityLiving) {
            EntityLiving mob = (EntityLiving) entity;
            String displayName = factionName + I18n.format("micwands.display.retinue");
            mob.setCustomNameTag(displayName);
            mob.setAlwaysRenderNameTag(true);

            NBTTagCompound nbt = mob.getEntityData();
            nbt.setString(WandsCore.NBT_KEY_S_FACTION_DISPLAY, factionName);
            if (!mob.world.isRemote) {
                mob.writeEntityToNBT(nbt);
            }
        }
    }

    // 在FactionManager类中添加新方法
    // 修改后的方法1：使用UUID作为阵营名
    public void addTamedMobToPlayerFaction(EntityLiving mob, EntityPlayer caster) {
        // 新增实体阵营存在性检查
        if (getPlayerFaction(mob.getUniqueID()) != null) {
            return;
        }
        UUID ownerUUID = caster.getUniqueID();
        String factionUUID = ownerUUID.toString();
        String factionName = caster.getName();

        if (getPlayerFaction(ownerUUID) == null) {
            createFaction(factionUUID, ownerUUID);
            data.playerNames.put(ownerUUID, factionName);
        }
        addEntityToFaction(mob.getUniqueID(), factionUUID);

        // 调用显示名称设置方法
        setupEntityDisplayName(mob, factionName);
    }

    // 新增：处理实体驯服时的阵营同步
    public void addTamedEntity(EntityLivingBase entity, UUID ownerId) {
        // 新增实体阵营存在性检查
        if (getPlayerFaction(entity.getPersistentID()) != null) {
            return;
        }
        String factionUUID = ownerId.toString();
        String factionName = null;

        if (getPlayerFaction(ownerId) == null) {
            createFaction(factionUUID, ownerId);
            EntityPlayer player = entity.world.getPlayerEntityByUUID(ownerId);
            if (player != null) {
                factionName = player.getName();
                data.playerNames.put(ownerId, factionName);
            }
        } else {
            factionName = data.playerNames.get(ownerId);
        }

        data.playerFactions.put(entity.getPersistentID(), factionUUID);
        data.markDirty();

        // 添加显示名称设置
        if (factionName != null && entity instanceof EntityLiving) {
            setupEntityDisplayName(entity, factionName);
        }
    }

    // 向阵营添加实体
    public void addEntityToFaction(UUID entityId, String factionName) {
        if (isFactionFull(factionName)) {
            return; // 已达上限则不添加
        }
        
        HashSet<UUID> factionSet = data.factionMembers.get(factionName);
        if (factionSet != null) {
            if (factionSet.add(entityId)) { // 仅在集合变化时标记脏数据
                data.playerFactions.put(entityId, factionName);
                data.markDirty();
            }
        }
    }


    public String getPlayerFaction(UUID playerId) {
        return data != null ? data.playerFactions.get(playerId) : null;  // 添加空安全检查
    }

    // 完善FactionData的NBT读写
    public static class FactionData extends WorldSavedData {
        public final Map<String, HashSet<UUID>> factionMembers = new HashMap<>();
        public final Map<UUID, String> playerFactions = new HashMap<>();
        public final Map<UUID, String> playerNames = new HashMap<>(); // 新增名称存储

        public FactionData(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            NBTTagCompound factions = nbt.getCompoundTag("factions");
            for (String key : factions.getKeySet()) {
                NBTTagList members = factions.getTagList(key, 10);
                HashSet<UUID> set = new HashSet<>();
                for (NBTBase entry : members) {
                    set.add(NBTUtil.getUUIDFromTag((NBTTagCompound) entry));
                }
                factionMembers.put(key, set);
            }

            NBTTagCompound playerMap = nbt.getCompoundTag("playerMap");
            for (String uuidStr : playerMap.getKeySet()) {
                playerFactions.put(UUID.fromString(uuidStr), playerMap.getString(uuidStr));
            }

            NBTTagCompound nameMap = nbt.getCompoundTag("playerNames");
            for (String uuidStr : nameMap.getKeySet()) {
                playerNames.put(UUID.fromString(uuidStr), nameMap.getString(uuidStr));
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            NBTTagCompound factions = new NBTTagCompound();
            for (Map.Entry<String, HashSet<UUID>> entry : factionMembers.entrySet()) {
                NBTTagList list = new NBTTagList();
                for (UUID id : entry.getValue()) {
                    list.appendTag(NBTUtil.createUUIDTag(id));
                }
                factions.setTag(entry.getKey(), list);
            }

            NBTTagCompound playerMap = new NBTTagCompound();
            for (Map.Entry<UUID, String> entry : playerFactions.entrySet()) {
                playerMap.setString(entry.getKey().toString(), entry.getValue());
            }

            compound.setTag("factions", factions);
            compound.setTag("playerMap", playerMap);
            return compound;
        }

        // 新增实体阵营查询方法（原注释位置缺少实现）
        public String getEntityFaction(UUID entityId) {
            return playerFactions.getOrDefault(entityId, null);
        }
    }
        // 正确放置在外层类中的方法
    public String getEntityFaction(UUID entityId) {
        return data != null ? data.getEntityFaction(entityId) : null;
    }
    /**
     * 检查两个实体是否属于同一阵营
     */
    public boolean isSameFaction(EntityLivingBase entity1, EntityLivingBase entity2) {
        // 基础空指针检查
        if (entity1 == null || entity2 == null || entity1.world == null || entity1.world.isRemote) {
            return false;
        }
    
        // 获取双方阵营
        String faction1 = getPlayerFaction(entity1.getPersistentID());
        String faction2 = getPlayerFaction(entity2.getPersistentID());
    
        // 处理玩家和生物的关系
        return faction1 != null && faction2 != null && faction1.equals(faction2);
    }
    
    /**
     * 处理同阵营实体间的伤害事件
     */
    @SubscribeEvent
    public void onEntityDamage(LivingAttackEvent event) {
        Entity source = event.getSource().getTrueSource();
        if (source instanceof EntityLivingBase
                && event.getEntity() instanceof EntityLivingBase
                && source.world != null) {
            EntityLivingBase attacker = (EntityLivingBase) source;
            EntityLivingBase target = (EntityLivingBase) event.getEntity();
            if (isSameFaction(attacker, target)) {
                event.setCanceled(true);
                // 新增仇恨清除逻辑
                if (attacker instanceof EntityLiving) {
                    ((EntityLiving) attacker).setAttackTarget(null);
                    ((EntityLiving) attacker).setRevengeTarget(null);
                }
                if (target instanceof EntityLiving) {
                    ((EntityLiving) target).setAttackTarget(null);
                    ((EntityLiving) target).setRevengeTarget(null);
                }
            }
        }
    }
    
    // 修改事件处理方法，添加空指针检查
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        // 排除玩家实体
        if (entity instanceof EntityPlayer || entity.world.isRemote) 
            return;

        FactionManager fm = FactionManager.getInstance(entity.world);
        if (fm.data == null) return;

        UUID entityId = entity.getPersistentID();
        String faction = fm.getEntityFaction(entityId);
        
        if (faction != null) {
            // 从阵营数据中移除实体
            fm.data.factionMembers.get(faction).remove(entityId);
            fm.data.playerFactions.remove(entityId);
            fm.data.markDirty();
            
            // 获取在线玩家（服务端侧）
            MinecraftServer server = entity.world.getMinecraftServer();
            if (server != null) {
                EntityPlayer founder = server.getPlayerList().getPlayerByUUID(UUID.fromString(faction));
                if (founder != null) {
                    founder.sendMessage(new TextComponentTranslation("msg.micwands.faction.mob_death", entity.getName()));
                    founder.sendStatusMessage(new TextComponentTranslation("msg.micwands.faction.mob_death"), true);
                }
            }
        }
    }

    //处理进加入阵营的亡灵生物
    public void setupUndeadEquipment(EntityLiving mob, EntityPlayer caster) {
        // 去除燃烧状态
        mob.extinguish();
        
        // 生成装甲掉落物并装备链甲
        for (EntityEquipmentSlot slot : new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        }) {
            ItemStack oldArmor = mob.getItemStackFromSlot(slot);
            if (!oldArmor.isEmpty()) {
                InventoryHelper.spawnItemStack(mob.world, mob.posX, mob.posY, mob.posZ, oldArmor);
            }
            // 装备链甲逻辑保持不变
            Item chainmailItem = Items.AIR;
            switch (slot) {
                case HEAD: chainmailItem = Items.CHAINMAIL_HELMET; break;
                case CHEST: chainmailItem = Items.CHAINMAIL_CHESTPLATE; break;
                case LEGS: chainmailItem = Items.CHAINMAIL_LEGGINGS; break;
                case FEET: chainmailItem = Items.CHAINMAIL_BOOTS; break;
            }
            // 创建带附魔的链甲
            ItemStack chainmailStack = new ItemStack(chainmailItem);
            chainmailStack.addEnchantment(Enchantments.THORNS, 2);       // 荆棘 II
            chainmailStack.addEnchantment(Enchantments.PROTECTION, 5);   // 保护 V
            mob.setItemStackToSlot(slot, chainmailStack);
        }
    }

    /**
     * 检查是否可以添加新成员
     */
    public boolean canAddMoreMembers(String factionName) {
        return getFactionMemberCount(factionName) < ModConfig.factionMaxMembers;
    }

    /**
     * 获取阵营当前成员数量
     */
    public int getFactionMemberCount(String factionName) {
        if (data == null || factionName == null) 
            return 0;
        HashSet<UUID> members = data.factionMembers.get(factionName);
        return members != null ? members.size() : 0;
    }

    /**
     * 检查是否达到成员上限
     */
    public boolean isFactionFull(String factionName) {
        return getFactionMemberCount(factionName) >= ModConfig.factionMaxMembers;
    }
}