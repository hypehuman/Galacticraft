package micdoodle8.mods.galacticraft.core.tile;

import micdoodle8.mods.galacticraft.api.item.IKeyable;
import micdoodle8.mods.galacticraft.core.Constants;
import micdoodle8.mods.galacticraft.core.GalacticraftCore;
import micdoodle8.mods.galacticraft.core.network.PacketSimple;
import micdoodle8.mods.galacticraft.core.util.GCCoreUtil;
import micdoodle8.mods.galacticraft.core.util.GCLog;
import micdoodle8.mods.miccore.Annotations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TileEntityTreasureChest extends TileEntityAdvanced implements ITickable, IInventory, IKeyable, ISidedInventory
{
    private NonNullList<ItemStack> stacks = NonNullList.withSize(27, ItemStack.EMPTY);
    public boolean adjacentChestChecked;
    public float lidAngle;
    public float prevLidAngle;
    public int numPlayersUsing;
    private int ticksSinceSync;
    private AxisAlignedBB renderAABB;

    protected ResourceLocation lootTable;
    protected long lootTableSeed;

    @Annotations.NetworkedField(targetSide = Side.CLIENT)
    public boolean locked = true;
    @Annotations.NetworkedField(targetSide = Side.CLIENT)
    public int tier = 1;

    public TileEntityTreasureChest()
    {
        this(1);
    }

    public TileEntityTreasureChest(int tier)
    {
        this.tier = tier;
    }

    @Override
    public int getSizeInventory()
    {
        return 27;
    }

    @Override
    public ItemStack getStackInSlot(int var1)
    {
        return this.stacks.get(var1);
    }

    @Override
    public ItemStack decrStackSize(int index, int count)
    {
        ItemStack itemstack = ItemStackHelper.getAndSplit(this.stacks, index, count);

        if (!itemstack.isEmpty())
        {
            this.markDirty();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeStackFromSlot(int index)
    {
        ItemStack oldstack = ItemStackHelper.getAndRemove(this.stacks, index);
        if (!oldstack.isEmpty())
        {
        	this.markDirty();
        }
    	return oldstack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack)
    {
        this.stacks.set(index, stack);

        if (stack.getCount() > this.getInventoryStackLimit())
        {
            stack.setCount(this.getInventoryStackLimit());
        }

        this.markDirty();
    }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack itemstack : this.stacks)
        {
            if (!itemstack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the name of this command sender (usually username, but possibly "Rcon")
     */
    @Override
    public String getName()
    {
        return GCCoreUtil.translate("container.treasurechest.name");
    }

    /**
     * Returns true if this thing is named
     */
    @Override
    public boolean hasCustomName()
    {
        return false;
    }

    public void setCustomName(String name)
    {
    }
    

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        this.locked = nbt.getBoolean("isLocked");
        this.tier = nbt.getInteger("tier");

        this.stacks = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(nbt, this.stacks);

        checkLootAndRead(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);
        nbt.setBoolean("isLocked", this.locked);
        nbt.setInteger("tier", this.tier);
        ItemStackHelper.saveAllItems(nbt, this.stacks);

        checkLootAndWrite(nbt);

        return nbt;
    }

    @Override
    public NBTTagCompound getUpdateTag()
    {
        return this.writeToNBT(new NBTTagCompound());
    }

    /**
     * Returns the maximum stack size for a inventory slot. Seems to always be 64, possibly will be extended. *Isn't
     * this more of a set than a get?*
     */
    @Override
    public int getInventoryStackLimit()
    {
        return 64;
    }

    /**
     * Do not make give this method the name canInteractWith because it clashes with Container
     */
    @Override
    public boolean isUsableByPlayer(EntityPlayer player)
    {
        return this.world.getTileEntity(this.pos) != this ? false : player.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void updateContainingBlockInfo()
    {
        super.updateContainingBlockInfo();
        this.adjacentChestChecked = false;
    }

    /**
     * Updates the JList with a new model.
     */
    @Override
    public void update()
    {
        int i = this.pos.getX();
        int j = this.pos.getY();
        int k = this.pos.getZ();
        ++this.ticksSinceSync;
        float f;

        if (this.locked)
        {
            this.numPlayersUsing = 0;
        }

        if (!this.world.isRemote && this.numPlayersUsing != 0 && (this.ticksSinceSync + i + j + k) % 200 == 0)
        {
            this.numPlayersUsing = 0;
            f = 5.0F;
            List list = this.world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB((double) ((float) i - f), (double) ((float) j - f), (double) ((float) k - f), (double) ((float) (i + 1) + f), (double) ((float) (j + 1) + f), (double) ((float) (k + 1) + f)));
            Iterator iterator = list.iterator();

            while (iterator.hasNext())
            {
                EntityPlayer entityplayer = (EntityPlayer) iterator.next();

                if (entityplayer.openContainer instanceof ContainerChest)
                {
                    IInventory iinventory = ((ContainerChest) entityplayer.openContainer).getLowerChestInventory();

                    if (iinventory == this || iinventory instanceof InventoryLargeChest && ((InventoryLargeChest) iinventory).isPartOfLargeChest(this))
                    {
                        ++this.numPlayersUsing;
                    }
                }
            }
        }

        this.prevLidAngle = this.lidAngle;
        f = 0.1F;
        double d2;

        if (this.numPlayersUsing > 0 && this.lidAngle == 0.0F)
        {
            double d1 = (double) i + 0.5D;
            d2 = (double) k + 0.5D;

            this.world.playSound(null, d1, (double)j + 0.5D, d2, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }

        if (((this.numPlayersUsing == 0 || this.locked) && this.lidAngle > 0.0F) || this.numPlayersUsing > 0 && this.lidAngle < 1.0F)
        {
            float f1 = this.lidAngle;

            if (this.numPlayersUsing == 0 || this.locked)
            {
                this.lidAngle -= f;
            }
            else
            {
                this.lidAngle += f;
            }

            if (this.lidAngle > 1.0F)
            {
                this.lidAngle = 1.0F;
            }

            float f2 = 0.5F;

            if (this.lidAngle < f2 && f1 >= f2)
            {
                d2 = (double) i + 0.5D;
                double d0 = (double) k + 0.5D;

                this.world.playSound(null, d2, (double)j + 0.5D, d0, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
            }

            if (this.lidAngle < 0.0F)
            {
                this.lidAngle = 0.0F;
            }
        }

        super.update();
    }

    @Override
    public boolean receiveClientEvent(int id, int type)
    {
        if (id == 1)
        {
            this.numPlayersUsing = type;
            return true;
        }
        else
        {
            return super.receiveClientEvent(id, type);
        }
    }

    @Override
    public void openInventory(EntityPlayer player)
    {
        if (!player.isSpectator())
        {
            if (this.numPlayersUsing < 0)
            {
                this.numPlayersUsing = 0;
            }

            ++this.numPlayersUsing;
            this.world.addBlockEvent(this.pos, this.getBlockType(), 1, this.numPlayersUsing);
            this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType(), false);
            this.world.notifyNeighborsOfStateChange(this.pos.down(), this.getBlockType(), false);
        }
    }

    @Override
    public void closeInventory(EntityPlayer player)
    {
        if (!player.isSpectator())
        {
//            --this.numPlayersUsing;
//            this.world.addBlockEvent(this.pos, this.getBlockType(), 1, this.numPlayersUsing);
//            this.world.notifyNeighborsOfStateChange(this.pos, this.getBlockType());
//            this.world.notifyNeighborsOfStateChange(this.pos.down(), this.getBlockType());
        }
    }

    /**
     * Returns true if automation is allowed to insert the given stack (ignoring stack size) into the given slot.
     */
    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack)
    {
        return true;
    }

    /**
     * invalidates a tile entity
     */
    @Override
    public void invalidate()
    {
        super.invalidate();
        this.updateContainingBlockInfo();
    }

    public String getGuiID()
    {
        return "minecraft:chest";
    }

    public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn)
    {
        return new ContainerChest(playerInventory, this, playerIn);
    }

    @Override
    public int getField(int id)
    {
        return 0;
    }

    @Override
    public void setField(int id, int value)
    {
    }

    @Override
    public int getFieldCount()
    {
        return 0;
    }

    @Override
    public void clear()
    {
        for (int i = 0; i < this.stacks.size(); ++i)
        {
            this.stacks.set(i, ItemStack.EMPTY);
        }
    }

    @Override
    public ITextComponent getDisplayName()
    {
        return (ITextComponent) (this.hasCustomName() ? new TextComponentString(this.getName()) : new TextComponentTranslation(this.getName(), new Object[0]));
    }

    @Override
    public double getPacketRange()
    {
        return 20.0D;
    }

    @Override
    public int getPacketCooldown()
    {
        return 3;
    }

    @Override
    public boolean isNetworkedTile()
    {
        return true;
    }

    @Override
    public int getTierOfKeyRequired()
    {
        return this.tier;
    }

    @Override
    public boolean onValidKeyActivated(EntityPlayer player, ItemStack key, EnumFacing face)
    {
        if (this.locked)
        {
            this.locked = false;

            if (this.world.isRemote)
            {
                // player.playSound("galacticraft.player.unlockchest", 1.0F,
                // 1.0F);
            }
            else
            {
                if (!player.capabilities.isCreativeMode)
                {
                    player.inventory.getCurrentItem().shrink(1);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onActivatedWithoutKey(EntityPlayer player, EnumFacing face)
    {
        if (this.locked)
        {
            if (player.world.isRemote)
            {
                GalacticraftCore.packetPipeline.sendToServer(new PacketSimple(PacketSimple.EnumSimplePacket.S_ON_FAILED_CHEST_UNLOCK, GCCoreUtil.getDimensionID(this.world), new Object[] { this.getTierOfKeyRequired() }));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean canBreak()
    {
        return false;
    }

    public static TileEntityTreasureChest findClosest(Entity entity, int tier)
    {
        double distance = Double.MAX_VALUE;
        TileEntityTreasureChest chest = null;
        for (final TileEntity tile : entity.world.loadedTileEntityList)
        {
            if (tile instanceof TileEntityTreasureChest && ((TileEntityTreasureChest) tile).getTierOfKeyRequired() == tier)
            {
                double dist = entity.getDistanceSq(tile.getPos().getX() + 0.5, tile.getPos().getY() + 0.5, tile.getPos().getZ() + 0.5);
                if (dist < distance)
                {
                    distance = dist;
                    chest = (TileEntityTreasureChest) tile;
                }
            }
        }

        if (chest != null)
        {
            GCLog.debug("Found chest to generate boss loot in: " + chest.pos);
        }
        else
        {
            GCLog.debug("Could not find chest to generate boss loot in!");
        }

        return chest;
    }

    protected boolean checkLootAndRead(NBTTagCompound compound)
    {
        if (compound.hasKey("LootTable", 8))
        {
            this.lootTable = new ResourceLocation(compound.getString("LootTable"));
            this.lootTableSeed = compound.getLong("LootTableSeed");
            return true;
        }
        else
        {
            return false;
        }
    }

    protected boolean checkLootAndWrite(NBTTagCompound compound)
    {
        if (this.lootTable != null)
        {
            compound.setString("LootTable", this.lootTable.toString());

            if (this.lootTableSeed != 0L)
            {
                compound.setLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
        else
        {
            return false;
        }
    }

    public void fillWithLoot(EntityPlayer player)
    {
        if (this.lootTable != null)
        {
            LootTable loottable = this.world.getLootTableManager().getLootTableFromLocation(this.lootTable);
            this.lootTable = null;
            Random random;

            if (this.lootTableSeed == 0L)
            {
                random = new Random();
            }
            else
            {
                random = new Random(this.lootTableSeed);
            }

            LootContext.Builder builder = new LootContext.Builder((WorldServer)this.world);

            if (player != null)
            {
                builder.withLuck(player.getLuck());
            }

            loottable.fillInventory(this, random, builder.build());
        }
    }

    public ResourceLocation getLootTable()
    {
        return this.lootTable;
    }

    public void setLootTable(ResourceLocation lootTable, long lootTableSeed)
    {
        this.lootTable = lootTable;
        this.lootTableSeed = lootTableSeed;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox()
    {
        if (this.renderAABB == null)
        {
            this.renderAABB = new AxisAlignedBB(pos, pos.add(1, 2, 1));
        }
        return this.renderAABB;
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared()
    {
        return Constants.RENDERDISTANCE_MEDIUM;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side)
    {
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction)
    {
        return false;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction)
    {
        return false;
    }
}