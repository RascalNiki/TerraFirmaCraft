/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.recipes.ingredients;

import java.util.function.Predicate;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import net.dries007.tfc.util.Helpers;

public interface FluidIngredient extends Predicate<FluidStack>
{
    /* Internal Access Only */
    BiMap<ResourceLocation, Serializer<?>> REGISTRY = HashBiMap.create();

    ResourceLocation FLUID_KEY = Helpers.identifier("fluid");
    ResourceLocation TAG_KEY = Helpers.identifier("tag");

    SimpleFluidIngredient.Serializer FLUID = register(FLUID_KEY, new SimpleFluidIngredient.Serializer());
    TagFluidIngredient.Serializer TAG = register(TAG_KEY, new TagFluidIngredient.Serializer());

    static <V extends FluidIngredient, T extends FluidIngredient.Serializer<V>> T register(ResourceLocation key, T serializer)
    {
        if (REGISTRY.containsKey(key))
        {
            throw new IllegalArgumentException("Duplicate key: " + key);
        }
        REGISTRY.put(key, serializer);
        return serializer;
    }

    static FluidIngredient fromJson(JsonObject json)
    {
        FluidIngredient.Serializer<?> serializer;
        if (json.has("type"))
        {
            final String type = JSONUtils.getAsString(json, "type");
            serializer = REGISTRY.get(new ResourceLocation(type));
            if (serializer == null)
            {
                throw new JsonParseException("Unknown fluid ingredient type: " + type);
            }
        }
        else if (json.has("fluid"))
        {
            serializer = FLUID;
        }
        else if (json.has("tag"))
        {
            serializer = TAG;
        }
        else
        {
            throw new JsonParseException("Fluid ingredient must have one of 'type', 'fluid', or 'tag' entries");
        }
        return serializer.fromJson(json);
    }

    static FluidIngredient fromNetwork(PacketBuffer buffer)
    {
        final FluidIngredient.Serializer<?> serializer = REGISTRY.get(buffer.readResourceLocation());
        return serializer.fromNetwork(buffer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void toNetwork(PacketBuffer buffer, FluidIngredient ingredient)
    {
        buffer.writeResourceLocation(REGISTRY.inverse().get(ingredient.getSerializer()));
        ((Serializer) ingredient.getSerializer()).toNetwork(buffer, ingredient);
    }

    /**
     * Test the ingredient against the provided fluid stack, including amounts.
     */
    @Override
    boolean test(FluidStack fluidStack);

    /**
     * Test the ingredient against the provided fluid stack, ignoring amounts.
     */
    boolean testIgnoreAmount(Fluid fluid);

    FluidIngredient.Serializer<?> getSerializer();

    interface Serializer<T extends FluidIngredient>
    {
        T fromJson(JsonObject json);

        T fromNetwork(PacketBuffer buffer);

        void toNetwork(PacketBuffer buffer, T ingredient);
    }
}
