package fr.usmb.m1isc.compilation.tp.ast;

public class UnaryOpNode extends Node {
    private final String operator;
    private final Node expression;

    public UnaryOpNode(String operator, Node expression) {
        this.operator = operator;
        this.expression = expression;
    }

    public String getOperator() {
        return operator;
    }

    public Node getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "(" + operator + " " + expression + ")";
    }
}
