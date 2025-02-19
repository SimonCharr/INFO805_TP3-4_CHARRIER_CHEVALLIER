package fr.usmb.m1isc.compilation.tp.ast;

public class IdentNode extends Node {
    private final String name;

    public IdentNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
