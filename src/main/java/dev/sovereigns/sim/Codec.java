package dev.sovereigns.sim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Binary form of a {@link World}, so the strategy layer saves without Minecraft classes.
 * Bump {@link #SCHEMA} on any change and keep reading older versions (branch on {@code version}).
 */
public final class Codec {
    public static final int SCHEMA = 1;

    private Codec() {}

    public static byte[] write(World w) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeLong(w.seed);
            out.writeInt(w.day);
            out.writeInt(w.nextId);
            out.writeInt(w.regions.size());
            for (long r : w.regions) out.writeLong(r);
            out.writeInt(w.provinces.size());
            for (Province p : w.provinces.values()) {
                out.writeInt(p.cx);
                out.writeInt(p.cz);
                out.writeByte(p.land.ordinal());
                out.writeInt(p.siteX);
                out.writeInt(p.siteZ);
                out.writeInt(p.roughness);
                out.writeBoolean(p.nearWater);
                out.writeUTF(p.name);
                out.writeInt(p.owner);
                out.writeDouble(p.population);
                out.writeInt(p.development);
                out.writeDouble(p.food);
                out.writeDouble(p.stability);
                out.writeDouble(p.buildPoints);
                out.writeBoolean(p.starving);
            }
            out.writeInt(w.states.size());
            for (State s : w.states.values()) {
                out.writeInt(s.id);
                out.writeUTF(s.name);
                out.writeByte(s.culture.ordinal());
                out.writeInt(s.color);
                out.writeInt(s.foundedDay);
                out.writeLong(s.capital);
                out.writeInt(s.liege);
                out.writeInt(s.ruler);
                out.writeDouble(s.treasury);
                out.writeDouble(s.army);
                out.writeInt(s.lastTick);
                out.writeBoolean(s.alive);
            }
            out.writeInt(w.people.size());
            for (Person p : w.people.values()) {
                out.writeInt(p.id);
                out.writeUTF(p.name);
                out.writeUTF(p.dynasty);
                out.writeInt(p.birthDay);
                out.writeByte(p.aggression);
                out.writeByte(p.greed);
                out.writeByte(p.pride);
                out.writeByte(p.diplomacy);
            }
            out.writeInt(w.relations.size());
            for (var e : w.relations.entrySet()) {
                out.writeLong(e.getKey());
                out.writeInt(e.getValue());
            }
            out.writeInt(w.chronicle.size());
            for (World.Entry e : w.chronicle) {
                out.writeInt(e.day());
                out.writeInt(e.state());
                out.writeUTF(e.text());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static World read(int version, byte[] data) {
        if (version < 1 || version > SCHEMA) throw new IllegalArgumentException("Unknown Sovereigns save schema " + version);
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            World w = new World(in.readLong());
            w.day = in.readInt();
            w.nextId = in.readInt();
            for (int i = in.readInt(); i > 0; i--) w.regions.add(in.readLong());
            Land[] lands = Land.values();
            for (int i = in.readInt(); i > 0; i--) {
                Province p = new Province(in.readInt(), in.readInt(), lands[in.readByte()], in.readInt(), in.readInt(),
                        in.readInt(), in.readBoolean());
                p.name = in.readUTF();
                p.owner = in.readInt();
                p.population = in.readDouble();
                p.development = in.readInt();
                p.food = in.readDouble();
                p.stability = in.readDouble();
                p.buildPoints = in.readDouble();
                p.starving = in.readBoolean();
                w.provinces.put(p.key(), p);
            }
            Culture[] cultures = Culture.values();
            for (int i = in.readInt(); i > 0; i--) {
                State s = new State(in.readInt(), in.readUTF(), cultures[in.readByte()], in.readInt(), in.readInt());
                s.capital = in.readLong();
                s.liege = in.readInt();
                s.ruler = in.readInt();
                s.treasury = in.readDouble();
                s.army = in.readDouble();
                s.lastTick = in.readInt();
                s.alive = in.readBoolean();
                w.states.put(s.id, s);
            }
            for (int i = in.readInt(); i > 0; i--) {
                Person p = new Person(in.readInt(), in.readUTF(), in.readUTF(), in.readInt(),
                        in.readByte(), in.readByte(), in.readByte(), in.readByte());
                w.people.put(p.id, p);
            }
            for (int i = in.readInt(); i > 0; i--) w.relations.put(in.readLong(), in.readInt());
            for (int i = in.readInt(); i > 0; i--) w.chronicle.addLast(new World.Entry(in.readInt(), in.readInt(), in.readUTF()));
            return w;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
