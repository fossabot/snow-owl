/*
 * generated by Xtext 2.11.0
 */
package com.b2international.snowowl.snomed.parser.antlr;

import java.io.InputStream;
import org.eclipse.xtext.parser.antlr.IAntlrTokenFileProvider;

public class QLAntlrTokenFileProvider implements IAntlrTokenFileProvider {

	@Override
	public InputStream getAntlrTokenFile() {
		ClassLoader classLoader = getClass().getClassLoader();
		return classLoader.getResourceAsStream("com/b2international/snowowl/snomed/parser/antlr/internal/InternalQLParser.tokens");
	}
}
