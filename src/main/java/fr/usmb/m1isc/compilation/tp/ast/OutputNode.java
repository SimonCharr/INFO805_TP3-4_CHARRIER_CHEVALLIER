package fr.usmb.m1isc.compilation.tp.ast;

public class OutputNode extends Node {
    private final Node expression;

    public OutputNode(Node expression) {
        this.expression = expression;
    }

    public Node getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "(OUTPUT " + expression + ")";
    }
}
