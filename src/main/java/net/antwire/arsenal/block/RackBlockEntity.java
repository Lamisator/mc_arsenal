package net.antwire.arsenal.block;

import net.antwire.arsenal.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** The weapons on a rack. Being a container, it spills them when broken, and hoppers can't reach in (no faces). */
public class RackBlockEntity extends BlockEntity implements Container {
	private NonNullList<ItemStack> items;

	public RackBlockEntity(BlockPos pos, BlockState state) {
		this(pos, state, state.getBlock() instanceof RackBlock rack ? rack.style.slots : 5);
	}

	public RackBlockEntity(BlockPos pos, BlockState state, int slots) {
		super(ModBlockEntities.RACK, pos, state);
		this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	/** The free slot closest to {@code want}, or -1. */
	int freeNear(int want) {
		return this.near(want, true);
	}

	/** The occupied slot closest to {@code want}, or -1. */
	int takenNear(int want) {
		return this.near(want, false);
	}

	private int near(int want, boolean free) {
		for (int d = 0; d < this.items.size(); d++) {
			for (int s : new int[] {want - d, want + d}) {
				if (s >= 0 && s < this.items.size() && this.items.get(s).isEmpty() == free) {
					return s;
				}
			}
		}
		return -1;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack s = ContainerHelper.removeItem(this.items, slot, count);
		this.setChanged();
		return s;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.setChanged();
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.items.size(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
