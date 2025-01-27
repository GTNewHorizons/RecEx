package com.bigbass.recex.recipes.ingredients;

public class Fluid {

    /** amount */
    public int a;

    /** uniqueIdentifier */
    public String id;

    /** localizedName */
    public String lN;

    public Fluid() {

    }

    public Fluid(int amount, String id, String fluidName) {
        this.a = amount;
        this.id = id;
        this.lN = fluidName;
    }
}
