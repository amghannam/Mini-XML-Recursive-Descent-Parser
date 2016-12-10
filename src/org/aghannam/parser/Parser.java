/*
 * CS 575: Project #1
 * File: Parser.java
 */
package org.aghannam.parser;

import org.aghannam.lex.Token;
import org.aghannam.lex.Lexer.TokenType;

import java.util.List;
import java.util.Stack;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class implements a (predictive) recursive-descent parser for XML--, a
 * fictional subset of XML.
 * <p>
 * The parser recognizes the following unambiguous LL(1) grammar:
 * <p>
 * document ::= element EOF <br>
 * element ::= < elementPrefix <br>
 * elementPrefix ::= NAME attribute elementSuffix <br>
 * attribute ::= NAME = STRING attribute <br>
 * attribute ::= EPSILON <br>
 * elementSuffix ::= > elementOrData endTag <br>
 * elementSuffix ::= /> <br>
 * elementOrData ::= element elementOrData <br>
 * elementOrData ::= DATA elementOrData <br>
 * elementOrData ::= EPSILON <br>
 * endTag ::= &lt;/ NAME > <br>
 * <p>
 * The parser verifies if a given XML-- file is syntactically correct according
 * to the above grammar, in which case it prints out a leftmost derivation with
 * one grammar rule displayed per line. Essentially, the parser simulates a
 * deterministic pushdown automaton that is equivalent to the above grammar. Per
 * the grammar, the tokens are NAME, DATA, STRING, <, >, &lt;/, /&gt;, and =.
 * <p>
 * Within this class, each non-helper method corresponds to exactly one
 * non-terminal as displayed above. Similarly, each token (i.e. terminal)
 * corresponds to a call of the method named <code>match()</code>, which
 * consumes it and jumps to the next token. Finally, this parser relies on the
 * presence of the file Lexer.java, which provides it with the token stream. In
 * this case, the 'token stream' is simply an <code>ArrayList</code> of
 * <code>Token</code> objects sent to this parser as input.
 * 
 * @author Ahmed Ghannam (amalghannam@crimson.ua.edu)
 */
public class Parser {
	/**
	 * The token stream returned by the lexical analyzer.
	 */
	private ArrayList<Token> tokens;

	/**
	 * Used to hold tag names to ensure that they match where necessary.
	 */
	private Stack<String> tagNames;

	/**
	 * Used to hold attribute names to ensure no duplicates.
	 */
	private HashSet<String> attributeNames;

	/**
	 * The lookahead symbol containing the next token in the input stream.
	 */
	private Token lookahead;

	/**
	 * Parses an XML-- document using recursive descent and prints a leftmost
	 * derivation that corresponds to a parse tree generating the given input
	 * token sequence.
	 * 
	 * @param tokens
	 *            the token stream returned by the lexical analyzer
	 * @throws ParserException
	 *             if any syntax errors are encountered during the parsing
	 *             process
	 */
	public void parse(List<Token> tokens) throws ParserException {
		try {
			this.tokens = new ArrayList<Token>(tokens);
			this.tagNames = new Stack<String>();
			this.attributeNames = new HashSet<String>();
			this.lookahead = this.tokens.get(0); 

			// Begin parsing from document(), which represents the start symbol
			// of the grammar
			document();

			// Closure message: Either a success or a failure
			if (!predict(TokenType.EPSILON)) {
				throw new ParserException("Error - Unexpected symbol encountered at end of file.");
			} else {
				System.out.println("\nDocument parsed successfully!");
			}
		} catch (IndexOutOfBoundsException e) {
			// An IndexOutOfBoundsException may be triggered if the parser
			// detects something highly unusual within
			// the input token stream.
			System.err.println("Fatal error while parsing...");
			System.err.println("Process terminated.");
			System.exit(1);
		}
	}

	/**
	 * Represents the grammar rule 'document ::= element EOF'. This is the start
	 * symbol of the grammar.
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void document() throws ParserException {
		System.out.println("\ndocument ::= element EOF");
		element();
		match(TokenType.EOF);
	}

	/**
	 * Represents the grammar rule 'element ::= < elementPrefix'.
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void element() throws ParserException {
		System.out.println("element ::= < elementPrefix");
		if (!predict(TokenType.OPEN)) {
			throw new ParserException("Syntax error: Required start tag expected but not found.");
		}
		match(TokenType.OPEN);
		elementPrefix();
	}

	/**
	 * Represents the grammar rule 'elementPrefix ::= NAME attribute
	 * elementSuffix'.
	 * <p>
	 * Since this rule contains a name, this method calls
	 * <code>cacheTagName()</code> to store tag-opening names should a
	 * name-matching with an end tag be necessary. In addition, because it also
	 * potentially contains attributes, it delegates attribute name-checking to
	 * <code>attribute()</code> which is called from within this method and
	 * ensures that any attributes defined within the current tag have no
	 * duplicate names.
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void elementPrefix() throws ParserException {
		System.out.println("elementPrefix ::= NAME attribute elementSuffix");
		cacheTagName(this.tokens.get(0));
		match(TokenType.NAME);
		attribute();
		elementSuffix();
	}

	/**
	 * Represents the grammar rules 'attribute ::= NAME = STRING attribute' and
	 * 'attribute ::= EPSILON'
	 * <p>
	 * As the number of attributes appearing within a tag could be arbitrarily
	 * large, this method works by recursively calling itself after matching
	 * each attribute in order to account for this fact. If no more attributes
	 * appear, it prints out the EPSILON production and exits. Additionally, it
	 * supports special error-checking by verifying that no two attributes
	 * within a tag have the same name, which is done through a call of
	 * <code>checkDuplicateNames()</code>. (Note that the parser terminates if
	 * any attributes are found to have matching names!)
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void attribute() throws ParserException {
		if (predict(TokenType.NAME)) {
			checkDuplicateNames(this.tokens.get(0));
			System.out.println("attribute ::= NAME = STRING attribute");
			match(TokenType.NAME);
			match(TokenType.ASSIGN);
			match(TokenType.STRING);
			attribute();
		} else {
			// Before exiting, clear the current name cache to account for any
			// subsequent iterations
			this.attributeNames.clear();
			System.out.println("attribute ::= EPSILON");
		}
	}

	/**
	 * Represents the grammar rules 'elementSuffix ::= > elementOrData endTag'
	 * and 'elementSuffix ::= />'.
	 * <p>
	 * It is through this method that the parser figures out if the current tag
	 * is an empty tag, in which case the most recently cached name is discarded
	 * (since no name matching is necessary at this point).
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void elementSuffix() throws ParserException {
		if (predict(TokenType.CLOSE)) {
			System.out.println("elementSuffix ::= > elementOrData endTag");
			match(TokenType.CLOSE);
			elementOrData();
			endTag();
		} else if (predict(TokenType.SLGT)) {
			System.out.println("elementSuffix ::= />");
			// As the next token is />, then this must be an empty tag; no name
			// matching necessary
			if (!tagNames.isEmpty()) {
				tagNames.pop();
			}
			match(TokenType.SLGT);
		} else {
			throw new ParserException("Syntax error: An unexpected symbol has been encountered!");
		}
	}

	/**
	 * Represents the grammar rules 'elementOrData ::= element elementOrData,'
	 * 'elementOrData ::= DATA elementOrData,' and 'elementOrData ::= EPSILON'.
	 * <p>
	 * The logic behind this method is that, within any given element in an
	 * XML-- document, there could be any arbitrary number of elements as well
	 * as data. As such, it recursively calls itself as long as there are more
	 * elements or data to parse. Otherwise, the EPSILON terminator rule is
	 * applied to indicate completion.
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void elementOrData() throws ParserException {
		if (predict(TokenType.OPEN)) {
			System.out.println("elementOrData ::= element elementOrData");
			element();
			elementOrData();
		} else if (predict(TokenType.DATA)) {
			System.out.println("elementOrData ::= DATA elementOrData");
			match(TokenType.DATA);
			elementOrData();
		} else {
			System.out.println("elementOrData ::= EPSILON");
		}
	}

	/**
	 * Represents the grammar rule 'endTag ::= &lt;/ NAME >'.
	 * <p>
	 * Since this is an end tag, name-matching is performed here to verify that
	 * the name cached at the corresponding start tag is identical. If a
	 * mismatch is detected, the parser displays the appropriate error message
	 * and terminates.
	 * 
	 * @throws ParserException
	 *             if a syntax error is encountered
	 */
	private void endTag() throws ParserException {
		matchNoAdvance(TokenType.LTSL);
		System.out.println("endTag ::= </ NAME >");
		matchTagName(this.tokens.get(1));
		match(TokenType.LTSL);
		match(TokenType.NAME);
		match(TokenType.CLOSE);
	}

	/*
	 * From this point onward, we only have helper methods.
	 */

	/**
	 * Advances by one token and sets the lookahead to be the next token in the
	 * input stream.
	 * <p>
	 * This method is called frequently by <code>match()</code> to indicate that
	 * a legal token has been consumed and the parser is ready to receive the
	 * next one, if any.
	 */
	private void nextToken() {
		this.tokens.remove(0);

		if (this.tokens.isEmpty()) {
			lookahead = new Token(TokenType.EPSILON, "");
		} else {
			lookahead = this.tokens.get(0);
		}
	}

	/**
	 * Examines the lookahead token in order to deduce the production to be used
	 * to expand a non-terminal.
	 * 
	 * @param type
	 *            the type of the predicted token against which to check the
	 *            lookahead
	 * @return <code>true</code> if the predicted token is equivalent to the
	 *         current lookahead token, <code>false</code> otherwise
	 */
	private boolean predict(TokenType type) {
		return lookahead.getType() == type;
	}

	/**
	 * Consumes the current token (i.e. terminal) and advances to the next token
	 * in the token stream.
	 * <p>
	 * For syntax checking purposes, this method also calls
	 * <code>matchNoAdvance()</code> to ensure that a token is legal before it
	 * can be consumed.
	 * 
	 * @param type
	 *            the type of the token to be consumed
	 * @throws ParserException
	 *             if an unexpected token is encountered
	 */
	private void match(TokenType type) throws ParserException {
		matchNoAdvance(type);
		nextToken();
	}

	/**
	 * Checks whether or not the current token is a legal symbol, without moving
	 * onto the next one.
	 * <p>
	 * This method exists solely for verification purposes and cannot be used
	 * for consuming tokens.
	 * 
	 * @param type
	 *            the type of the token predicted
	 * @throws ParserException
	 *             if an unexpected token is encountered
	 */
	private void matchNoAdvance(TokenType type) throws ParserException {
		if (!predict(type)) {
			throw new ParserException("Syntax error: An unexpected symbol has been encountered!");
		}
	}

	/**
	 * Temporarily caches the current tag name for a possible future match with
	 * an end tag name.
	 * <p>
	 * If the current tag turns out to be an empty tag (i.e. no name appears at
	 * the end to match against), then the cached name is simply discarded and
	 * no matching is performed. (This method, however, does not explicitly
	 * check whether the current tag is an empty tag--that conclusion is
	 * inferred by the parser, per the grammar.)
	 * 
	 * @param openName
	 *            the token representing the name at the beginning of this tag
	 */
	private void cacheTagName(Token openName) throws ParserException {
		matchNoAdvance(TokenType.NAME);
		this.tagNames.push(openName.getLexeme());
	}

	/**
	 * Verifies that a start tag and its corresponding end tag have identical
	 * names.
	 * <p>
	 * This method uses the name previously cached by
	 * <code>cacheTagName()</code> to do the matching. Should a mismatch be
	 * detected, the parser immediately terminates and does not continue parsing
	 * the rest of the document. Case sensitivity counts.
	 * 
	 * @param endName
	 *            the token representing the name at the end of the current tag
	 */
	private void matchTagName(Token endName) {
		String openName = this.tagNames.pop();
		// Here, the use of equals() in the condition automatically handles the
		// case sensitivity requirement
		if (!openName.equals(endName.getLexeme())) {
			System.out.println("Error - End tag name mismatch. Expected '" + openName + "' but found '"
					+ endName.getLexeme() + "'.");
			System.out.println("Parsing terminated...");
			System.exit(2);
		}
	}

	/**
	 * Verifies that no two attributes within a tag share the same name.
	 * <p>
	 * By design, attributes may have the same name as long as they are in
	 * different tags. However, attributes within the same tag must each have a
	 * unique name. This method serves to enforce this rule. Should a duplicate
	 * name be detected for a given tag's attributes, the parser immediately
	 * terminates and does not continue parsing the rest of the document.
	 * 
	 * @param attributeName
	 *            the token representing the attribute name to check
	 */
	private void checkDuplicateNames(Token attributeName) {
		// A HashSet automatically returns true if an item is unique, in which
		// case it can safely be added
		if (!attributeNames.add(attributeName.getLexeme())) {
			System.out.println("Error - Detected duplicate attribute name within current tag!");
			System.out.println("Parsing terminated...");
			System.exit(3);
		}
	}
}
