package dev.sovereigns.sim;

/** A ruler (later also heirs and councillors). Traits are 0..10. */
public final class Person {
    public final int id;
    public final String name, dynasty;
    public final int birthDay;
    public final int aggression, greed, pride, diplomacy;

    public Person(int id, String name, String dynasty, int birthDay, int aggression, int greed, int pride, int diplomacy) {
        this.id = id;
        this.name = name;
        this.dynasty = dynasty;
        this.birthDay = birthDay;
        this.aggression = aggression;
        this.greed = greed;
        this.pride = pride;
        this.diplomacy = diplomacy;
    }
}
