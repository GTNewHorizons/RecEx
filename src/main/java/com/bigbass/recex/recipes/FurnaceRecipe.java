package com.bigbass.recex.recipes;

import com.bigbass.recex.recipes.ingredients.Item;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record FurnaceRecipe(Item input, Item output) {}
