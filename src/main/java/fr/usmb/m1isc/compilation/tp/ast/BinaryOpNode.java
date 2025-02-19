package fr.usmb.m1isc.compilation.tp.ast;

public class BinaryOpNode extends Node {
    private final String operator;
    private final Node left;
    private final Node right;

    public BinaryOpNode(String operator, Node left, Node right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String getOperator() {
        return operator;
    }

    public Node getLeft() {
        return left;
    }

    public Node getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "(" + operator + " " + left + " " + right + ")";
    }
}
