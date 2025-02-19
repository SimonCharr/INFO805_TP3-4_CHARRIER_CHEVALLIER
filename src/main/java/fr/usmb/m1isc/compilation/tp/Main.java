package fr.usmb.m1isc.compilation.tp;

import fr.usmb.m1isc.compilation.tp.ast.Node;
import java.io.FileReader;
import java.io.InputStreamReader;

public class Main {

	public static void main(String[] args) throws Exception {
		LexicalAnalyzer yy;
		if (args.length > 0) {
			yy = new LexicalAnalyzer(new FileReader(args[0]));
		} else {
			yy = new LexicalAnalyzer(new InputStreamReader(System.in));
		}
		@SuppressWarnings("deprecation")
		parser p = new parser(yy);

		try {
			Node result = (Node) p.parse().value;
			System.out.println("Arbre abstrait généré : ");
			System.out.println(result);

			// Génération du code
			CodeGenerator generator = new CodeGenerator();
			generator.generate(result);
			System.out.println("\nCode généré : ");
			System.out.println(generator.getCode());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
