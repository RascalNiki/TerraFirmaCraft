/*
 * Work under Copyright. Licensed under the EUPL.
 * See the project README.md and LICENSE.txt for more information.
 */

package net.dries007.tfc.objects.te;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import net.dries007.tfc.objects.blocks.BlockPowderKeg;
import net.dries007.tfc.objects.inventory.capability.IItemHandlerSidedCallback;
import net.dries007.tfc.objects.inventory.capability.ItemHandlerSidedWrapper;
import net.dries007.tfc.util.calendar.CalendarTFC;
import net.dries007.tfc.util.calendar.ICalendarFormatted;

import static net.dries007.tfc.objects.blocks.BlockPowderKeg.SEALED;

/**
 * @see BlockPowderKeg
 */
@ParametersAreNonnullByDefault
public class TEPowderKeg extends TETickableInventory implements IItemHandlerSidedCallback
{
    private boolean sealed;
    private long sealedCalendarTick;
    private int fuse = -1;

    private boolean isLit = false;
    private EntityLivingBase igniter;

    public TEPowderKeg()
    {
        super(new ItemStackHandler(12));
    }

    /**
     * Called when this TileEntity was created by placing a sealed keg Item.
     * Loads its data from the Item's NBTTagCompound without loading xyz coordinates.
     *
     * @param nbt The NBTTagCompound to load from.
     */
    public void readFromItemTag(NBTTagCompound nbt)
    {
        inventory.deserializeNBT(nbt.getCompoundTag("inventory"));
        sealedCalendarTick = nbt.getLong("sealedCalendarTick");
        sealed = true;
        markForSync();
    }

    /**
     * Called once per side when the TileEntity has finished loading.
     * On servers, this is the earliest point in time to safely access the TE's World object.
     */
    @Override
    public void onLoad()
    {
        if (!world.isRemote)
        {
            sealed = world.getBlockState(pos).getValue(SEALED);
        }
    }

    @Nonnull
    public String getSealedDate()
    {
        return ICalendarFormatted.getTimeAndDate(sealedCalendarTick, CalendarTFC.CALENDAR_TIME.getDaysInMonth());
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, EnumFacing side)
    {
        return !world.getBlockState(pos).getValue(SEALED) && isItemValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, EnumFacing side)
    {
        return !sealed;
    }

    public void onSealed()
    {
        // Update sealed tick info and sync to client
        sealedCalendarTick = CalendarTFC.CALENDAR_TIME.getTicks();
        sealed = true;
        markForSync();
    }

    public void onUnseal()
    {
        // Update sealed tick info and sync to client
        sealedCalendarTick = 0;
        sealed = false;
        markForSync();
    }

    public boolean isSealed()
    {
        return sealed;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        sealedCalendarTick = nbt.getLong("sealedCalendarTick");
        sealed = sealedCalendarTick > 0;
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        nbt.setLong("sealedCalendarTick", sealedCalendarTick);
        return super.writeToNBT(nbt);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing)
    {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing)
    {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
        {
            return (T) new ItemHandlerSidedWrapper(this, inventory, facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void onBreakBlock(World world, BlockPos pos, IBlockState state)
    {
        if (!state.getValue(SEALED))
        {
            // Not sealed, so empty contents normally
            super.onBreakBlock(world, pos, state);
        }
        else
        {
            // Need to create the full keg and drop it now
            ItemStack stack = getItemStack(state);
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack)
    {
        return stack.getItem() == Items.GUNPOWDER;
    }

    public void setIgniter(@Nullable EntityLivingBase igniterIn)
    {
        igniter = igniterIn;
    }

    /**
     * Called to get the NBTTagCompound that is put on keg Items.
     * This happens when a sealed keg was broken.
     *
     * @return An NBTTagCompound containing inventory and tank data.
     */
    private NBTTagCompound getItemTag()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("inventory", inventory.serializeNBT());
        nbt.setLong("sealedCalendarTick", sealedCalendarTick);
        return nbt;
    }

    public int getStrength()
    {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            count += inventory.getStackInSlot(i).getCount();
        }
        return count / 12;
    }

    public int getFuse()
    {
        return fuse;
    }

    public boolean isLit()
    {
        return isLit;
    }

    public void setLit(boolean lit)
    {
        world.playSound(null, pos.getX(), pos.getY() + 0.5D, pos.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.33F);
        isLit = lit;
        fuse = 80;
        markForSync();
    }

    @Override
    public void update()
    {
        if (isLit)
        {
            --fuse;

            if (fuse <= 0)
            {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);
                if (!this.world.isRemote)
                {
                    explode();
                }
            }
        }
        super.update();
    }

    public ItemStack getItemStack(IBlockState state)
    {
        ItemStack stack = new ItemStack(state.getBlock());
        stack.setTagCompound(getItemTag());
        return stack;
    }

    private void explode()
    {
        world.createExplosion(igniter, pos.getX(), pos.getY(), pos.getZ(), getStrength(), true);
    }
}
