package fr.usmb.m1isc.compilation.tp;

import fr.usmb.m1isc.compilation.tp.ast.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Générateur de code pour la machine à registres (vm-0.9.jar par ex.)
 * Étendu pour gérer :
 * - Opérateurs booléens (NOT, AND, OR)
 * - Opérateurs de comparaison (==, >, >=)
 * - Boucles WHILE
 * - Conditionnelles IF-THEN-ELSE
 * - MOD (reste de division)
 */
public class CodeGenerator {

    // Contient le code généré final
    private StringBuilder code;
    // Ensemble des variables rencontrées (pour le segment DATA)
    private Set<String> variables;
    // Compteur pour générer des labels uniques (pour IF, WHILE, etc.)
    private int labelCounter = 0;

    public CodeGenerator() {
        code = new StringBuilder();
        variables = new HashSet<>();
    }

    /**
     * Méthode principale de génération :
     * 1. On génère le code du programme dans un buffer temporaire (segment CODE).
     * 2. On insère en préfixe le segment DATA en fonction des variables
     * rencontrées.
     */
    public void generate(Node node) {
        StringBuilder codeBuffer = new StringBuilder();
        generateCode(node, codeBuffer);

        // Segment DATA
        code.append("DATA SEGMENT\n");
        for (String var : variables) {
            code.append("\t").append(var).append(" DD\n");
        }
        code.append("DATA ENDS\n");

        // Segment CODE
        code.append("CODE SEGMENT\n");
        code.append(codeBuffer);
        code.append("CODE ENDS\n");
    }

    /**
     * Retourne le code assembleur complet sous forme de chaîne.
     */
    public String getCode() {
        return code.toString();
    }

    /**
     * Fonction récursive qui parcourt l'AST et génère le code assembleur
     * correspondant, en l'écrivant dans le buffer 'buf'.
     */
    private void generateCode(Node node, StringBuilder buf) {
        if (node == null) {
            return;
        }
        // -----------------
        // 1) let ident = expr
        // -----------------
        if (node instanceof LetNode) {
            LetNode let = (LetNode) node;
            // Ajouter la variable dans l'ensemble
            variables.add(let.getIdentifier());
            // Générer code pour l'expression => résultat dans eax
            generateCode(let.getExpression(), buf);
            // Stocker la valeur (eax) dans la variable
            buf.append("\tmov ").append(let.getIdentifier()).append(", eax\n");

            // -----------------
            // 2) expression binaire
            // -----------------
        } else if (node instanceof BinaryOpNode) {
            BinaryOpNode bin = (BinaryOpNode) node;
            String op = bin.getOperator();

            // ---- CAS SPÉCIAL: Séquence d'instructions (opérateur ";") ----
            if (";".equals(op)) {
                generateCode(bin.getLeft(), buf);
                generateCode(bin.getRight(), buf);
                return;
            }

            // ---- CAS SPÉCIAL: WHILE ----
            if ("WHILE".equals(op)) {
                generateWhile(bin.getLeft(), bin.getRight(), buf);
                return;
            }

            // ---- CAS SPÉCIAL: IF ----
            if ("IF".equals(op)) {
                // Dans votre grammaire, vous stockez le THEN/ELSE dans
                // BinaryOpNode("THEN_ELSE", thenPart, elsePart).
                // On le récupère donc:
                if (bin.getRight() instanceof BinaryOpNode) {
                    BinaryOpNode thenElse = (BinaryOpNode) bin.getRight();
                    generateIf(bin.getLeft(), thenElse.getLeft(), thenElse.getRight(), buf);
                }
                return;
            }

            // ---- CAS GÉNÉRAL: Opérateur arithmétique, booléen, comparaison, etc. ----
            // 1. Générer code gauche => eax
            generateCode(bin.getLeft(), buf);
            // 2. push eax (sauvegarde)
            buf.append("\tpush eax\n");
            // 3. Générer code droit => eax
            generateCode(bin.getRight(), buf);
            // 4. pop ebx => valeur gauche
            buf.append("\tpop ebx\n");

            // 5. Selon l'opérateur, on produit l'instruction adéquate
            switch (op) {
                // --- ARITHMÉTIQUE ---
                case "+":
                    buf.append("\tadd eax, ebx\n");
                    break;
                case "-":
                    // Attention à l'ordre: left - right => (ebx - eax)
                    buf.append("\tsub ebx, eax\n");
                    buf.append("\tmov eax, ebx\n");
                    break;
                case "*":
                    // multiplication => mul eax, ebx (selon la syntaxe de votre VM)
                    buf.append("\tmul eax, ebx\n");
                    break;
                case "/":
                    // division => div ebx, eax (ou l'inverse selon la VM)
                    // On met le résultat dans eax
                    buf.append("\tdiv ebx, eax\n");
                    buf.append("\tmov eax, ebx\n");
                    break;
                case "MOD":
                case "%":
                    // Reste de division, style "a mod b"
                    // Ex. selon l'approche de l'exemple:
                    // ecx = left
                    // div ecx, right => ecx = left / right
                    // mul ecx, right => ecx = ecx * right
                    // sub left, ecx => remainder
                    // mov eax, left
                    buf.append("\tmov ecx, ebx\n"); // ecx = left
                    buf.append("\tdiv ecx, eax\n"); // ecx = left / right
                    buf.append("\tmul ecx, eax\n"); // ecx = ecx * right
                    buf.append("\tsub ebx, ecx\n"); // remainder dans ebx
                    buf.append("\tmov eax, ebx\n");
                    break;

                // --- COMPARAISONS ---
                case "==":
                // On veut (ebx == eax) => 1 ou 0 dans eax
                // sub ebx, eax => si 0 => eq
                // jnz => false
                {
                    String eqFalse = newLabel("eq_false");
                    String eqEnd = newLabel("eq_end");
                    buf.append("\tsub ebx, eax\n");
                    buf.append("\tjnz ").append(eqFalse).append("\n"); // si ≠0 => pas égaux
                    buf.append("\tmov eax, 1\n");
                    buf.append("\tjmp ").append(eqEnd).append("\n");
                    buf.append(eqFalse).append(":\n");
                    buf.append("\tmov eax, 0\n");
                    buf.append(eqEnd).append(":\n");
                }
                    break;
                case ">":
                // on veut (ebx > eax) => si (ebx - eax) > 0 => true
                {
                    String gtFalse = newLabel("gt_false");
                    String gtEnd = newLabel("gt_end");
                    buf.append("\tsub ebx, eax\n");
                    buf.append("\tjle ").append(gtFalse).append("\n");
                    buf.append("\tmov eax, 1\n");
                    buf.append("\tjmp ").append(gtEnd).append("\n");
                    buf.append(gtFalse).append(":\n");
                    buf.append("\tmov eax, 0\n");
                    buf.append(gtEnd).append(":\n");
                }
                    break;
                case ">=":
                // (ebx >= eax) => si (ebx - eax) >= 0 => true
                {
                    String geFalse = newLabel("ge_false");
                    String geEnd = newLabel("ge_end");
                    buf.append("\tsub ebx, eax\n");
                    buf.append("\tjl ").append(geFalse).append("\n");
                    buf.append("\tmov eax, 1\n");
                    buf.append("\tjmp ").append(geEnd).append("\n");
                    buf.append(geFalse).append(":\n");
                    buf.append("\tmov eax, 0\n");
                    buf.append(geEnd).append(":\n");
                }
                    break;
                case "<":
                // On veut (ebx < eax) => si (ebx - eax) < 0 => true
                // ou alors on inverse l’ordre. Il faut être cohérent.
                // Suppose qu'on évalue left => ebx et right => eax
                {
                    String ltTrue = newLabel("lt_true");
                    String ltEnd = newLabel("lt_end");
                    buf.append("\tsub ebx, eax\n");
                    // si (ebx - eax) < 0 => on jump sur ltTrue
                    buf.append("\tjge ").append(ltTrue).append("\n");
                    // => false
                    buf.append("\tmov eax, 1\n");
                    buf.append("\tjmp ").append(ltEnd).append("\n");
                    buf.append(ltTrue).append(":\n");
                    buf.append("\tmov eax, 0\n");
                    buf.append(ltEnd).append(":\n");
                }
                    break;

                // --- BOOLÉENS ---
                case "AND":
                // if (ebx != 0 && eax != 0) => 1 else 0
                {
                    String andFalse = newLabel("and_false");
                    String andEnd = newLabel("and_end");
                    // Test left
                    buf.append("\tcmp ebx, 0\n");
                    buf.append("\tjz ").append(andFalse).append("\n");
                    // Test right
                    buf.append("\tcmp eax, 0\n");
                    buf.append("\tjz ").append(andFalse).append("\n");
                    // Les deux != 0 => vrai
                    buf.append("\tmov eax, 1\n");
                    buf.append("\tjmp ").append(andEnd).append("\n");
                    // Faux:
                    buf.append(andFalse).append(":\n");
                    buf.append("\tmov eax, 0\n");
                    // Fin
                    buf.append(andEnd).append(":\n");
                }
                    break;
                case "OR":
                // if (ebx != 0 || eax != 0) => 1 else 0
                {
                    String orTrue = newLabel("or_true");
                    String orEnd = newLabel("or_end");
                    buf.append("\tcmp ebx, 0\n");
                    buf.append("\tjnz ").append(orTrue).append("\n");
                    buf.append("\tcmp eax, 0\n");
                    buf.append("\tjnz ").append(orTrue).append("\n");
                    // Les deux == 0 => faux
                    buf.append("\tmov eax, 0\n");
                    buf.append("\tjmp ").append(orEnd).append("\n");
                    // Sinon => vrai
                    buf.append(orTrue).append(":\n");
                    buf.append("\tmov eax, 1\n");
                    buf.append(orEnd).append(":\n");
                }
                    break;

                default:
                    System.err.println("Opérateur non supporté : " + op);
                    break;
            }

            // -----------------
            // 3) expression unaire
            // -----------------
        } else if (node instanceof UnaryOpNode) {
            UnaryOpNode un = (UnaryOpNode) node;
            String op = un.getOperator();
            // Générer code fils => eax
            generateCode(un.getExpression(), buf);

            // Gestion du "-" unaire ou "NOT"
            if ("-".equals(op)) {
                buf.append("\tneg eax\n");
            } else if ("NOT".equals(op)) {
                // NOT booléen => si eax == 0 => eax=1, sinon eax=0
                String labelFalse = newLabel("not_false");
                String labelEnd = newLabel("not_end");
                // cmp eax, 0 => jz => c'est 0 => c'est "faux" => donc not => vrai
                buf.append("\tcmp eax, 0\n");
                buf.append("\tjnz ").append(labelFalse).append("\n");
                // eax == 0 => devient 1
                buf.append("\tmov eax, 1\n");
                buf.append("\tjmp ").append(labelEnd).append("\n");
                // sinon => eax=0
                buf.append(labelFalse).append(":\n");
                buf.append("\tmov eax, 0\n");
                buf.append(labelEnd).append(":\n");
            }

            // -----------------
            // 4) Constante numérique
            // -----------------
        } else if (node instanceof NumberNode) {
            NumberNode num = (NumberNode) node;
            buf.append("\tmov eax, ").append(num.getValue()).append("\n");

            // -----------------
            // 5) Identifiant
            // -----------------
        } else if (node instanceof IdentNode) {
            IdentNode id = (IdentNode) node;
            variables.add(id.getName());
            buf.append("\tmov eax, ").append(id.getName()).append("\n");

            // -----------------
            // 6) Input (lecture)
            // -----------------
        } else if (node instanceof InputNode) {
            buf.append("\tinput eax\n");

            // -----------------
            // 7) Output (écriture)
            // -----------------
        } else if (node instanceof OutputNode) {
            OutputNode out = (OutputNode) node;
            generateCode(out.getExpression(), buf);
            buf.append("\toutput eax\n");

            // -----------------
            // 8) NilNode => pas d'action
            // -----------------
        } else if (node instanceof NilNode) {
            // Pas de code

            // -----------------
            // 9) Autres ?
            // -----------------
        } else {
            System.err.println("Type de nœud non géré : " + node.getClass());
        }
    }

    /**
     * Génération du code pour un while :
     * while (cond) do body
     */
    private void generateWhile(Node cond, Node body, StringBuilder buf) {
        String startLabel = newLabel("start_while");
        String endLabel = newLabel("end_while");

        buf.append(startLabel).append(":\n");
        // Générer le code de la condition => eax
        generateCode(cond, buf);
        // si eax == 0 => on sort
        buf.append("\tcmp eax, 0\n");
        buf.append("\tjz ").append(endLabel).append("\n");
        // sinon on exécute le corps
        generateCode(body, buf);
        // retour au début
        buf.append("\tjmp ").append(startLabel).append("\n");
        buf.append(endLabel).append(":\n");
    }

    /**
     * Génération du code pour un if :
     * if (cond) then (thenPart) else (elsePart)
     */
    private void generateIf(Node cond, Node thenPart, Node elsePart, StringBuilder buf) {
        String elseLabel = newLabel("else");
        String endLabel = newLabel("end_if");

        // Évaluer la condition => eax
        generateCode(cond, buf);
        // si eax == 0 => on va dans le else
        buf.append("\tcmp eax, 0\n");
        buf.append("\tjz ").append(elseLabel).append("\n");

        // Générer le then
        generateCode(thenPart, buf);
        // Saut inconditionnel vers la fin
        buf.append("\tjmp ").append(endLabel).append("\n");

        // Étiquette else
        buf.append(elseLabel).append(":\n");
        generateCode(elsePart, buf);

        // Étiquette fin
        buf.append(endLabel).append(":\n");
    }

    /**
     * Génère un label unique du type prefix_i, où i s'incrémente à chaque appel.
     */
    private String newLabel(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

}
