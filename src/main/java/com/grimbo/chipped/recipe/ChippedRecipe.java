package com.grimbo.chipped.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.realmsclient.util.JsonUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.SerializationTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ChippedRecipe implements Recipe<Container> {
    private final Serializer serializer;
    private final ResourceLocation id;
    private final String group;
    private final List<Tag<Item>> tags;
    private final Block icon;

    public ChippedRecipe(Serializer serializer, ResourceLocation id, String group, List<Tag<Item>> tags, Block block) {
        this.serializer = serializer;
        this.id = id;
        this.group = group;
        this.tags = tags;
        this.icon = block;
    }

    public Stream<ItemStack> getResults(Container container) {
        ItemStack current = container.getItem(0);
        if (!current.isEmpty()) {
            Item item = current.getItem();
            return tags.stream()
                    .filter(item::is)
                    .flatMap(tag -> tag.getValues().stream())
                    .filter(value -> value != item)
                    .map(ItemStack::new);
        }
        return Stream.empty();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(icon);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return serializer;
    }

    @Override
    public RecipeType<?> getType() {
        return serializer.type;
    }

    @Override
    public boolean matches(Container inventory, Level worldIn) {
        ItemStack item = inventory.getItem(0);
        if (!item.isEmpty()) {
            for (Tag<Item> tag : tags) {
                if (item.getItem().is(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Tag<Item>> getTags() {
        return tags;
    }

    @Override
    public ItemStack assemble(Container container) {
        return getResultItem();
    }

    @Override
    public boolean canCraftInDimensions(int i, int j) {
        return true;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    public static class Serializer implements RecipeSerializer<ChippedRecipe> {

        private final RecipeType<?> type;
        private final Block icon;

        public Serializer(RecipeType<?> type, Block icon) {
            this.type = type;
            this.icon = icon;
        }

        public ChippedRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            String s = GsonHelper.getAsString(json, "group","");
            List<Tag<Item>> tags = new ArrayList<>();
            JsonArray tagArray = GsonHelper.getAsJsonArray(json, "tags");
            for (int i = 0; i < tagArray.size(); ++i) {
                String tagName = GsonHelper.convertToString(tagArray.get(i), "tags[" + i + "]");
                ResourceLocation id = new ResourceLocation(tagName);
                Tag<Item> tag = SerializationTags.getInstance().getItems().getTag(id);
                if(tag == null) {
                    throw new JsonSyntaxException("Unknown item tag '" + id + "'");
                }
                tags.add(tag);
            }
            return new ChippedRecipe(this, recipeId, s, tags, icon);
        }

        public ChippedRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            String s = buffer.readUtf(32767);
            int tagCount = buffer.readVarInt();
            List<Tag<Item>> tags = new ArrayList<>(tagCount);
            for (int i = 0; i < tagCount; i++) {
                int itemCount = buffer.readVarInt();
                Set<Item> items = new HashSet<>(itemCount);
                for (int j = 0; j < itemCount; j++) {
                    items.add(Item.byId(buffer.readVarInt()));
                }
                tags.add(Tag.fromSet(items));
            }
            return new ChippedRecipe(this, recipeId, s, tags, icon);
        }

        public void toNetwork(FriendlyByteBuf buffer, ChippedRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeVarInt(recipe.tags.size());
            for (Tag<Item> tag : recipe.tags) {
                List<Item> values = tag.getValues();
                buffer.writeVarInt(values.size());
                for (Item item : values) {
                    buffer.writeVarInt(Item.getId(item));
                }
            }
        }
    }
}