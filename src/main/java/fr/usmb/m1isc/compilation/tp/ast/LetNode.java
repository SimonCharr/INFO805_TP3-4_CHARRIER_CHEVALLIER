package fr.usmb.m1isc.compilation.tp.ast;

public class LetNode extends Node {
    private final String identifier;
    private final Node expression;

    public LetNode(String identifier, Node expression) {
        this.identifier = identifier;
        this.expression = expression;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Node getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "(LET " + identifier + " " + expression + ")";
    }
}
