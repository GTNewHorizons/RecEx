package com.bigbass.recex.recipes.ingredients;

public class Item implements IItem {

    /** amount */
    public int a;

    /** damage */
    public int m;

    /** uniqueIdentifier */
    public String id;

    /** localizedName */
    public String lN;

    /** nbt tag */
    public String nbt;

    public Item() {

    }

    public Item(int amount, int damage, String unlocalizedName, String displayName) {

        this.a = amount;
        this.m = damage;
        this.id = unlocalizedName;
        this.lN = displayName;
    }
}
