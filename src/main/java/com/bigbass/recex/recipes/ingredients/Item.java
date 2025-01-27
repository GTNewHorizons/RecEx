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

    public Item(int amount, int damage, String id, String displayName, String nbt) {
        this.a = amount;
        this.m = damage;
        this.id = id;
        this.lN = displayName;
        this.nbt = nbt;
    }
}
