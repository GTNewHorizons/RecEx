package com.bigbass.recex.recipes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.bigbass.recex.RecipeExporterMod;
import com.bigbass.recex.recipes.gregtech.GregtechMachine;
import com.bigbass.recex.recipes.gregtech.GregtechRecipe;
import com.bigbass.recex.recipes.gregtech.RecipeUtil;
import com.bigbass.recex.recipes.ingredients.Fluid;
import com.bigbass.recex.recipes.ingredients.Item;
import com.bigbass.recex.recipes.ingredients.ItemOreDict;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bartworks.API.recipe.BartWorksRecipeMaps;
import ggfab.api.GGFabRecipeMaps;
import goodgenerator.api.recipe.GoodGeneratorRecipeMaps;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTLanguageManager;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.api.recipe.GTPPRecipeMaps;

public class RecipeExporter {

    private static RecipeExporter instance;

    private RecipeExporter() {}

    public static RecipeExporter getInst() {
        if (instance == null) {
            instance = new RecipeExporter();
        }

        return instance;
    }

    /**
     * <p>
     * Collects recipes into a master Hashtable (represents a JSON Object),
     * then serializes it and saves it to a datetime-stamped file.
     * </p>
     *
     * <p>
     * Recipes are stored in collections, often either List's or Hashtable's.
     * The Gson library will serialize objects based on their public fields.
     * The field name becomes the key, and the value is also serialized the same way.
     * Lists are serialized as JSON arrays.
     * </p>
     *
     * <p>
     * Schema for existing recipe sources should not be radically changed unless
     * truly necessary. Adding additional data is acceptable however.
     * </p>
     */
    public void run() {
        List<GregtechMachine> gtRecipes = getGregtechRecipes();
        List<ShapedRecipe> shapedRecies = getShapedRecipes();
        List<ShapelessRecipe> shapelessRecipes = getShapelessRecipes();
        List<OreDictShapedRecipe> oredictShapedRecipes = getOreDictShapedRecipes();
        List<FurnaceRecipe> furnaceRecipes = getFurnaceRecipes();

        emitJson(gtRecipes, shapedRecies, shapelessRecipes, oredictShapedRecipes, furnaceRecipes);
    }

    private void emitJson(List<GregtechMachine> gtRecipes, List<ShapedRecipe> shapedRecies,
        List<ShapelessRecipe> shapelessRecipes, List<OreDictShapedRecipe> oredictShapedRecipes,
        List<FurnaceRecipe> furnaceRecipes) {
        Hashtable<String, Object> root = new Hashtable<String, Object>();

        List<Object> sources = new ArrayList<Object>();

        HashMap<Object, Object> temp = new HashMap<>();
        temp.put("type", "gregtech");
        temp.put("machines", gtRecipes);
        sources.add(temp);

        temp = new HashMap<>();
        temp.put("type", "shaped");
        temp.put("recipes", shapedRecies);
        sources.add(temp);

        temp = new HashMap<>();
        temp.put("type", "shapeless");
        temp.put("recipes", shapelessRecipes);
        sources.add(temp);

        temp = new HashMap<>();
        temp.put("type", "shapedOreDict");
        temp.put("recipes", oredictShapedRecipes);
        sources.add(temp);

        temp = new HashMap<>();
        temp.put("type", "smelting");
        temp.put("recipes", furnaceRecipes);
        sources.add(temp);

        root.put("sources", sources);

        Gson gson = (new GsonBuilder()).create();
        try {
            saveData(gson.toJson(root));
        } catch (Exception e) {
            e.printStackTrace();
            RecipeExporterMod.log.error("Recipes failed to export!");
        }
    }

    // spotless:off
    private static final Comparator<net.minecraft.item.Item> COMPARE_ITEM = Comparator.comparingInt(i -> net.minecraft.item.Item.getIdFromItem(i));

    private static final Comparator<ItemStack> COMPARE_ITEM_STACKS =
        Comparator.comparing((ItemStack s) -> s.getItem(), COMPARE_ITEM)
        .thenComparingInt((ItemStack s) -> s.getItemDamage())
        .thenComparingInt((ItemStack s) -> s.stackSize)
        // Really bad, but idc about performance here because this will be used very rarely if ever
        .thenComparing((ItemStack s) -> s.stackTagCompound == null ? "" : s.stackTagCompound.toString());

    private static final Comparator<FluidStack> COMPARE_FLUID_STACKS =
        Comparator.comparingInt((FluidStack s) -> s.getFluid() == null ? -1 : net.minecraftforge.fluids.FluidRegistry.getFluidID(s.getFluid()))
        .thenComparingInt((FluidStack s) -> s.amount)
        // Also really bad, but idc about performance because no one uses fluid stack nbt
        .thenComparing((FluidStack s) -> s.tag == null ? "" : s.tag.toString());

    private static <T> Comparator<T[]> makeArrayComparator(Comparator<T> base) {
        return (a, b) -> {
            if(a.length != b.length) {
                return Integer.compare(a.length, b.length);
            }

            for(int i = 0; i < a.length; i++) {
                T left = a[i], right = b[i];

                if (left == null && right == null) {
                    continue;
                }

                if (left == null || right == null) {
                    return left == null ? -1 : 1;
                }

                int result = base.compare(left, right);

                if (result != 0) return result;
            }

            return 0;
        };
    }

    private static <T> Comparator<List<T>> makeListComparator(Comparator<T> base) {
        return (a, b) -> {
            if(a.size() != b.size()) {
                return Integer.compare(a.size(), b.size());
            }

            for(int i = 0; i < a.size(); i++) {
                T left = a.get(i), right = b.get(i);

                if (left == null && right == null) {
                    continue;
                }

                if (left == null || right == null) {
                    return left == null ? -1 : 1;
                }

                int result = base.compare(left, right);

                if (result != 0) return result;
            }

            return 0;
        };
    }

    private static final Comparator<ItemStack[]> COMPARE_ITEM_STACK_ARRAY = makeArrayComparator(COMPARE_ITEM_STACKS);
    private static final Comparator<FluidStack[]> COMPARE_FLUID_STACK_ARRAY = makeArrayComparator(COMPARE_FLUID_STACKS);
    private static final Comparator<List<ItemStack>> COMPARE_ITEM_STACK_LIST = makeListComparator(COMPARE_ITEM_STACKS);
    // private static final Comparator<List<FluidStack>> COMPARE_FLUID_STACK_LIST = makeListComparator(COMPARE_FLUID_STACKS);

    // super cursed and probably stupidly slow, but it works
    private static final Comparator<GTRecipe> COMPARE_RECIPE =
        Comparator.comparingInt((GTRecipe r) -> r.mEUt)
        .thenComparingInt((GTRecipe r) -> r.mDuration)
        .thenComparingInt((GTRecipe r) -> r.mSpecialValue)
        .thenComparing((GTRecipe r) -> r.mInputs, COMPARE_ITEM_STACK_ARRAY)
        .thenComparing((GTRecipe r) -> r.mFluidInputs, COMPARE_FLUID_STACK_ARRAY)
        .thenComparing((GTRecipe r) -> r.mOutputs, COMPARE_ITEM_STACK_ARRAY)
        .thenComparing((GTRecipe r) -> r.mFluidOutputs, COMPARE_FLUID_STACK_ARRAY);

    // spotless:on

    private static ItemStack[] clean(ItemStack[] stacks) {
        int len = 0;

        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) len++;
        }

        ItemStack[] out = new ItemStack[len];
        int next = 0;

        for (int i = 0; i < stacks.length; i++) {
            ItemStack x = stacks[i];

            if (x != null) {
                out[next++] = x.copy();
            }
        }

        Arrays.sort(out, COMPARE_ITEM_STACKS);

        return out;
    }

    private static FluidStack[] clean(FluidStack[] stacks) {
        int len = 0;

        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) len++;
        }

        FluidStack[] out = new FluidStack[len];
        int next = 0;

        for (int i = 0; i < stacks.length; i++) {
            FluidStack x = stacks[i];

            if (x != null) {
                out[next++] = x.copy();
            }
        }

        Arrays.sort(out, COMPARE_FLUID_STACKS);

        return out;
    }

    private static GTRecipe cloneAndSort(GTRecipe recipe) {
        GTRecipe out = new GTRecipe(null, null, null, null, null, 0, 0);

        out.mSpecialItems = recipe.mSpecialItems;
        out.mChances = recipe.mChances;
        out.mDuration = recipe.mDuration;
        out.mSpecialValue = recipe.mSpecialValue;
        out.mEUt = recipe.mEUt;
        out.mNeedsEmptyOutput = recipe.mNeedsEmptyOutput;
        out.isNBTSensitive = recipe.isNBTSensitive;
        out.mCanBeBuffered = recipe.mCanBeBuffered;
        out.mFakeRecipe = recipe.mFakeRecipe;
        out.mEnabled = recipe.mEnabled;
        out.mHidden = recipe.mHidden;

        out.mInputs = clean(recipe.mInputs);
        out.mOutputs = clean(recipe.mOutputs);

        out.mFluidInputs = clean(recipe.mFluidInputs);
        out.mFluidOutputs = clean(recipe.mFluidOutputs);

        return out;
    }

    /**
     * <p>
     * Unlike vanilla recipes, the current schema here groups recipes from each machine together.
     * This is a minor file size improvement. Rather than specifying the machine's name in every recipe,
     * the machine name is only listed once for the entire file.
     * </p>
     *
     * <p>
     * This format does not impede the process of loading the recipes into NEP.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<GregtechMachine> getGregtechRecipes() {
        List<RecipeMap<RecipeMapBackend>> maps = new ArrayList<>();
        List<Class<?>> recipeMapClasses = Arrays.asList(
            RecipeMaps.class,
            GTPPRecipeMaps.class,
            BartWorksRecipeMaps.class,
            GoodGeneratorRecipeMaps.class,
            GGFabRecipeMaps.class);

        for (Class<?> recipeMapClass : recipeMapClasses) {
            for (Field field : recipeMapClass.getDeclaredFields()) {
                if (field.getType() == RecipeMap.class) {
                    try {
                        maps.add((RecipeMap<RecipeMapBackend>) field.get(null));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        List<GregtechMachine> machines = new ArrayList<GregtechMachine>();

        for (RecipeMap<RecipeMapBackend> map : maps) {
            GregtechMachine mach = new GregtechMachine();

            // machine name retrieval
            mach.n = GTLanguageManager.getTranslation(map.unlocalizedName);
            if (mach.n == null || mach.n.isEmpty()) {
                mach.n = map.unlocalizedName;
            }

            RecipeExporterMod.log.info("Processing recipe map " + mach.n);

            List<GTRecipe> recipes = map.getAllRecipes()
                .stream()
                .map(RecipeExporter::cloneAndSort)
                .sorted(COMPARE_RECIPE)
                .collect(Collectors.toList());

            RecipeExporterMod.log.info("Finished sorting recipes for map " + mach.n);

            for (GTRecipe rec : recipes) {
                GregtechRecipe gtr = new GregtechRecipe();
                gtr.en = rec.mEnabled;
                gtr.dur = rec.mDuration;
                gtr.eut = rec.mEUt;
                if (rec.mSpecialValue != 0) gtr.sp = rec.mSpecialValue;

                // item inputs
                for (ItemStack stack : rec.mInputs) {
                    Item item = RecipeUtil.formatGregtechItemStack(stack);

                    if (item == null) {
                        continue;
                    }

                    gtr.iI.add(item);
                }

                // item outputs
                for (ItemStack stack : rec.mOutputs) {
                    Item item = RecipeUtil.formatGregtechItemStack(stack);

                    if (item == null) {
                        continue;
                    }

                    gtr.iO.add(item);
                }

                // fluid inputs
                for (FluidStack stack : rec.mFluidInputs) {
                    Fluid fluid = RecipeUtil.formatGregtechFluidStack(stack);

                    if (fluid == null) {
                        continue;
                    }

                    gtr.fI.add(fluid);
                }

                // fluid outputs
                for (FluidStack stack : rec.mFluidOutputs) {
                    Fluid fluid = RecipeUtil.formatGregtechFluidStack(stack);

                    if (fluid == null) {
                        continue;
                    }

                    gtr.fO.add(fluid);
                }

                mach.recs.add(gtr);
            }
            machines.add(mach);
        }

        return machines;
    }

    private List<ShapedRecipe> getShapedRecipes() {
        List<ShapedRecipe> retRecipes = new ArrayList<ShapedRecipe>();

        // spotless:off
        List<ShapedRecipes> recipes = CraftingManager.getInstance()
            .getRecipeList()
            .stream()
            .filter(r -> r instanceof ShapedRecipes)
            .map(r -> (ShapedRecipes)r)
            .sorted(Comparator.comparing((ShapedRecipes r) -> r.recipeItems, COMPARE_ITEM_STACK_ARRAY)
                .thenComparing((ShapedRecipes r) -> r.getRecipeOutput(), COMPARE_ITEM_STACKS))
            .collect(Collectors.toList());
        // spotless:on

        for (ShapedRecipes original : recipes) {
            ShapedRecipe rec = new ShapedRecipe();

            for (ItemStack stack : original.recipeItems) {
                Item item = RecipeUtil.formatRegularItemStack(stack);
                rec.iI.add(item);
            }

            rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());

            retRecipes.add(rec);
        }

        return retRecipes;
    }

    private List<ShapelessRecipe> getShapelessRecipes() {
        List<ShapelessRecipe> retRecipes = new ArrayList<ShapelessRecipe>();

        // spotless:off
        List<ShapelessRecipes> recipes = CraftingManager.getInstance()
            .getRecipeList()
            .stream()
            .filter(r -> r instanceof ShapelessRecipes)
            .map(r -> (ShapelessRecipes)r)
            .map(r -> new ShapelessRecipes(
                r.getRecipeOutput(),
                r.recipeItems.stream()
                    .sorted(COMPARE_ITEM_STACKS)
                    .collect(Collectors.toList())
            ))
            .sorted(
                Comparator.comparing((ShapelessRecipes r) -> r.recipeItems, COMPARE_ITEM_STACK_LIST)
                    .thenComparing((ShapelessRecipes r) -> r.getRecipeOutput(), COMPARE_ITEM_STACKS)
            )
            .collect(Collectors.toList());
        // spotless:on

        for (ShapelessRecipes original : recipes) {
            ShapelessRecipe rec = new ShapelessRecipe();

            rec.iI = original.recipeItems.stream()
                .sorted(COMPARE_ITEM_STACKS)
                .map(RecipeUtil::formatRegularItemStack)
                .toArray(Item[]::new);

            rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());

            retRecipes.add(rec);
        }

        return retRecipes;
    }

    private static class OredictInput {

        public final Class<?> type;
        public final Comparator<?> comparator;

        public <T> OredictInput(Class<T> type, Comparator<T> comparator) {
            this.type = type;
            this.comparator = comparator;
        }
    }

    // spotless:off
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final OredictInput[] OREDICT_INPUTS = new OredictInput[] {
        new OredictInput(ItemStack.class, COMPARE_ITEM_STACKS),
        new OredictInput(String.class, (String a, String b) -> a.compareTo(b)),
        new OredictInput(String[].class, makeArrayComparator((String a, String b) -> a.compareTo(b))),
        new OredictInput(net.minecraft.item.Item.class, COMPARE_ITEM),
        new OredictInput(List.class, makeListComparator((Comparator)COMPARE_ITEM_STACKS)),
        new OredictInput(ItemStack[].class, COMPARE_ITEM_STACK_ARRAY),
    };
    // spotless:on

    private static int getInputType(Object x) {
        if (x == null) {
            return -1;
        }

        for (int i = 0; i < OREDICT_INPUTS.length; i++) {
            if (OREDICT_INPUTS[i].type.isAssignableFrom(x.getClass())) {
                return i;
            }
        }

        return -1;
    }

    private static final Comparator<Object> COMPARE_OREDICT_INPUT = (Object a, Object b) -> {
        int ai = getInputType(a), bi = getInputType(b);

        if (ai != bi || ai == -1 || bi == -1) {
            return Integer.compare(ai, bi);
        }

        @SuppressWarnings("unchecked")
        Comparator<Object> c = (Comparator<Object>) OREDICT_INPUTS[ai].comparator;

        return c == null ? 0 : c.compare(a, b);
    };

    private static final Comparator<Object[]> COMPARE_OREDICT_INPUT_LIST = makeArrayComparator(COMPARE_OREDICT_INPUT);

    private List<OreDictShapedRecipe> getOreDictShapedRecipes() {

        List<OreDictShapedRecipe> retRecipes = new ArrayList<OreDictShapedRecipe>();

        // spotless:off
        List<ShapedOreRecipe> recipes = CraftingManager.getInstance()
            .getRecipeList()
            .stream()
            .filter(r -> r instanceof ShapedOreRecipe)
            .map(r -> (ShapedOreRecipe)r)
            .sorted(
                Comparator.comparing(ShapedOreRecipe::getInput, COMPARE_OREDICT_INPUT_LIST)
                    .thenComparing((ShapedOreRecipe r) -> r.getRecipeOutput(), COMPARE_ITEM_STACKS)
            )
            .collect(Collectors.toList());
        // spotless:on

        for (ShapedOreRecipe original : recipes) {
            OreDictShapedRecipe rec = new OreDictShapedRecipe();

            for (Object input : original.getInput()) {
                if (input instanceof ItemStack) {
                    rec.iI.add(RecipeUtil.formatRegularItemStack((ItemStack) input));
                } else if (input instanceof String) {
                    ItemOreDict item = RecipeUtil.parseOreDictionary((String) input);
                    if (item != null) {
                        rec.iI.add(item);
                        RecipeExporterMod.log.info("input instanceof String : " + item.dns + ", " + item.ims);
                    }
                } else if (input instanceof String[]) {
                    ItemOreDict item = RecipeUtil.parseOreDictionary((String[]) input);
                    if (item != null) {
                        rec.iI.add(item);
                        RecipeExporterMod.log.info("input instanceof String[] : " + item.dns + ", " + item.ims);
                    }
                } else if (input instanceof net.minecraft.item.Item) {
                    rec.iI.add(RecipeUtil.formatRegularItemStack(new ItemStack((net.minecraft.item.Item) input)));
                } else if (input instanceof Block) {
                    rec.iI.add(RecipeUtil.formatRegularItemStack(new ItemStack((Block) input, 1, Short.MAX_VALUE)));
                } else if (input instanceof ArrayList<?>) {
                    ArrayList<?> list = (ArrayList<?>) input;
                    if (list != null && list.size() > 0) {
                        ItemOreDict item = new ItemOreDict();
                        for (Object listObj : list) {
                            if (listObj instanceof ItemStack) {
                                ItemStack stack = (ItemStack) listObj;
                                item.ims.add(RecipeUtil.formatRegularItemStack(stack));

                                int[] ids = OreDictionary.getOreIDs(stack);
                                for (int id : ids) {
                                    String name = OreDictionary.getOreName(id);
                                    if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Unknown")) {
                                        boolean isDuplicate = false;
                                        for (String existing : item.dns) {
                                            if (existing.equalsIgnoreCase(name)) {
                                                isDuplicate = true;
                                                break;
                                            }
                                        }
                                        if (!isDuplicate) {
                                            item.dns.add(name);
                                        }
                                    }
                                }
                            }
                        }

                        if (!item.ims.isEmpty()) {
                            rec.iI.add(item);
                        }
                    }
                } else if (input != null) {
                    try {
                        RecipeExporterMod.log.warn(
                            "OreDict Input Type not parsed! " + input.getClass()
                                .getTypeName()
                                + " | "
                                + input.getClass()
                                    .getName());
                    } catch (NullPointerException e) {}
                }
            }

            rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());

            retRecipes.add(rec);
        }

        return retRecipes;
    }

    private List<FurnaceRecipe> getFurnaceRecipes() {
        return FurnaceRecipes.smelting()
            .getSmeltingList()
            .entrySet()
            .stream()
            .map(
                entry -> new FurnaceRecipe(
                    RecipeUtil.formatRegularItemStack(entry.getKey()),
                    RecipeUtil.formatRegularItemStack(entry.getValue())))
            .collect(Collectors.toList());
    }

    private void saveData(String json) {
        final File saveFile = getSaveFile(".json");

        try {
            FileWriter writer = new FileWriter(saveFile);
            writer.write(json);
            writer.close();

            RecipeExporterMod.log.info("Recipes have been exported.");
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            RecipeExporterMod.log.error("Recipes failed to save!");
            return;
        }

        final String zipPath = saveFile.getPath()
            .replace(".json", ".zip");
        final ZipFile zipFile = new ZipFile(new File(zipPath));
        final ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.FASTEST);

        try {
            zipFile.addFile(saveFile, zipParameters);
            RecipeExporterMod.log.info("Recipes have been compressed.");
        } catch (Exception e) {
            e.printStackTrace();
            RecipeExporterMod.log.warn("Recipe compression may have failed!");
        }
    }

    private File getSaveFile(String ext) {
        String dateTime = ZonedDateTime.now(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss"));
        File file = new File(RecipeExporterMod.clientConfigDir.getParent() + "/RecEx-Records/" + dateTime + ext);
        if (!file.exists()) {
            file.getParentFile()
                .mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return file;
    }
}
