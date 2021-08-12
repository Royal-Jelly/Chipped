package com.grimbo.chipped.menus;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.grimbo.chipped.recipe.ChippedRecipe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

//Pulled from net.minecraft.inventory.container.ChippedContainer
public class ChippedMenu extends AbstractContainerMenu {

	private final ContainerLevelAccess access;
	private final DataSlot selectedRecipeIndex;
	private final Level level;
	private ChippedRecipe recipe;
	private Supplier<List<ItemStack>> results;
	private ItemStack input = ItemStack.EMPTY;
	private long lastSoundTime;
	final Slot inputSlot;
	final Slot resultSlot;
	private Runnable slotUpdateListener  = () -> {};
	public final Container container;

	private final ResultContainer resultContainer = new ResultContainer();

	//Stores dynamic container data
	private final MenuType<ChippedMenu> containerType;
	private final RecipeType<ChippedRecipe> recipeType;
	private final Block blockWorkbench;

	public ChippedMenu(int id, Inventory inventory, MenuType<ChippedMenu> menuType, RecipeType<ChippedRecipe> recipeType, Block block) {
		this(id, inventory, menuType, recipeType, ContainerLevelAccess.NULL, block);
	}

	public ChippedMenu(int id, Inventory inventory, MenuType<ChippedMenu> menuType, RecipeType<ChippedRecipe> recipeType, ContainerLevelAccess access, Block block) {
		super(menuType, id);
		this.selectedRecipeIndex = DataSlot.standalone();
		this.container = new SimpleContainer(1) {
			public void setChanged() {
				super.setChanged();
				ChippedMenu.this.slotsChanged(this);
				ChippedMenu.this.slotUpdateListener.run();
			}
		};
		containerType = menuType;
		this.access = access;
		this.level = inventory.player.level;
		this.recipeType = recipeType;
		this.blockWorkbench = block;
		this.inputSlot = this.addSlot(new Slot(this.container, 0, 20, 33));

		this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			public ItemStack onTake(Player player, ItemStack stack) {
				stack.onCraftedBy(player.level, player, stack.getCount());
				ChippedMenu.this.resultContainer.awardUsedRecipes(player);
				ItemStack itemstack = ChippedMenu.this.inputSlot.remove(1);
				if (!itemstack.isEmpty()) {
					ChippedMenu.this.setupResultSlot();
				}

				access.execute((level, pos) -> {
					long l = level.getGameTime();
					if (ChippedMenu.this.lastSoundTime != l) {
						level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
								SoundSource.BLOCKS, 1.0F, 1.0F);
						ChippedMenu.this.lastSoundTime = l;
					}

				});
				return super.onTake(player, stack);
			}
		});

		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 9; ++j) {
				this.addSlot(new Slot(inventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}

		for (int k = 0; k < 9; ++k) {
			this.addSlot(new Slot(inventory, k, 8 + k * 18, 142));
		}

		this.addDataSlot(this.selectedRecipeIndex);
	}

	public int getSelectedIndex() {
		return this.selectedRecipeIndex.get();
	}

	public List<ItemStack> getResults() {
		return results == null ? Collections.emptyList() : results.get();
	}

	@Environment(EnvType.CLIENT)
	public boolean hasInputItem() {
		return this.inputSlot.hasItem() && results != null;
	}

	@Override
	public boolean stillValid(Player player) { return stillValid(this.access, player, blockWorkbench); }

	public boolean clickMenuButton(Player player, int index) {
		if (this.isValidRecipeIndex(index)) {
			this.selectedRecipeIndex.set(index);
			this.setupResultSlot();
		}

		return true;
	}

	private boolean isValidRecipeIndex(int index) {
		return index >= 0 && index < getResults().size();
	}

	public void slotsChanged(Container inventory) {
		ItemStack itemstack = this.inputSlot.getItem();
		if (itemstack.getItem() != this.input.getItem()) {
			this.input = itemstack.copy();
			this.setupRecipeList(inventory, itemstack);
		}

	}

	private void setupRecipeList(Container inventory, ItemStack stack) {
		results = null;
		this.selectedRecipeIndex.set(-1);
		this.resultSlot.set(ItemStack.EMPTY);
		if (!stack.isEmpty()) {
			this.recipe = this.level.getRecipeManager().getRecipeFor(recipeType, inventory, this.level).orElse(null);
			if(recipe != null) {
				results = Suppliers.memoize(() -> recipe.getResults(container).collect(Collectors.toList()));
			}
		}

	}

	private void setupResultSlot() {
		if (recipe != null && results != null && !this.results.get().isEmpty() && this.isValidRecipeIndex(this.selectedRecipeIndex.get())) {
			this.resultContainer.setRecipeUsed(recipe);
			this.resultSlot.set(results.get().get(selectedRecipeIndex.get()).copy());
		} else {
			this.resultSlot.set(ItemStack.EMPTY);
		}

		this.broadcastChanges();
	}

	@Override
	public MenuType<?> getType() {
		return containerType;
	}

	@Environment(EnvType.CLIENT)
	public void registerUpdateListener(Runnable p_217071_1_) {
		this.slotUpdateListener = p_217071_1_;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
	}

	public ItemStack quickMoveStack(Player player, int i) {
		ItemStack itemStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(i);
		if (slot != null && slot.hasItem()) {
			ItemStack itemStack2 = slot.getItem();
			Item item = itemStack2.getItem();
			itemStack = itemStack2.copy();
			if (i == 1) {
				item.onCraftedBy(itemStack2, player.level, player);
				if (!this.moveItemStackTo(itemStack2, 2, 38, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(itemStack2, itemStack);
			} else if (i == 0) {
				if (!this.moveItemStackTo(itemStack2, 2, 38, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.level.getRecipeManager().getRecipeFor(recipeType, new SimpleContainer(itemStack2), this.level).isPresent()) {
				if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (i < 29) {
				if (!this.moveItemStackTo(itemStack2, 29, 38, false)) {
					return ItemStack.EMPTY;
				}
			} else if (i < 38 && !this.moveItemStackTo(itemStack2, 2, 29, false)) {
				return ItemStack.EMPTY;
			}

			if (itemStack2.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			}

			slot.setChanged();
			if (itemStack2.getCount() == itemStack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, itemStack2);
			this.broadcastChanges();
		}

		return itemStack;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.resultContainer.removeItemNoUpdate(1);
		this.access.execute((level, blockPos) -> this.clearContainer(player, player.level, this.container));
	}
}