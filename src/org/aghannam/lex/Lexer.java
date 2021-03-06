/*
 * CS 575: Project #1
 * File: Lexer.java
 */
package org.aghannam.lex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This class implements a regex-based lexical analyzer for XML--.
 * <p>
 * The Extended BNF grammar for XML-- is as follows:
 * <p>
 * document ::= element <br>
 * element ::= start_tag (element | DATA)* end_tag | empty_tag <br>
 * start_tag ::= < NAME attribute* > <br>
 * end_tag ::= &lt;/ NAME > <br>
 * empty_tag ::= < NAME attribute* /> <br>
 * attribute ::= NAME = STRING <br>
 * <p>
 * Accordingly, the tokens that are recognized by this lexer are NAME, DATA,
 * STRING, <, >, &lt;/, /&gt;, and =. Essentially, the lexer simulates a
 * finite-state machine that has a final state for recognizing each kind of
 * token. The output of this lexer is a list of recognized tokens, which is then
 * fed into the parser as input for syntax-checking. (This means the parser does
 * not need to call this lexer for each token, as all the tokens are returned at
 * once as a single list.)
 * <p>
 * It is also worth noting that this is NOT a hand-coded lexer; instead, regular
 * expressions are used for matching the tokens. These regular expressions are
 * defined and stored in an enum which holds the patterns for each type of
 * token.
 * 
 * @author Ahmed Ghannam (amalghannam@crimson.ua.edu)
 */
public class Lexer {
	/* Comment and whitespace definitions. Will be skipped. */
	private static final String COMMENT_PATTERN = "<!--.*?-->";
	private static final String WHITESPACE_PATTERN = "[ \t\n\r]+";

	/* Auxiliary regex definitions constituting some of the tokens */
	private static final String INITIAL = "[a-zA-Z]|_|:"; 
	private static final String INVALID = "\\*|\\+|\\[|\\]|\\(|\\)|\\{|\\}|\\$|@|#|;|\\?|,|!|%|\\^|`|\\||~";
	private static final String OTHER = "(" + INITIAL + "|[0-9]|-|\\.|" + INVALID + ")+";
	private static final String ORDINARY = "[^<>\"'&]";
	private static final String SPECIAL = "&lt;|&gt;|&quot;|&apos;|&amp;";
	private static final String REFERENCE = "&#[0-9]+;|&#x([0-9]|[a-fA-F])+;";
	private static final String CHAR = ORDINARY + "|" + SPECIAL + "|" + REFERENCE;

	/* The actual tokens */
	private static final String NAME_PATTERN = "(?<!>)(" + OTHER + ")+";
	private static final String STRING_PATTERN = "(\"(" + CHAR + "|')*\")|'(" + CHAR + "|\")*'";
	private static final String DATA_PATTERN = "(?<=>)(" + CHAR + ")+(?<!=)";
	private static final String OPEN_PATTERN = "</?(?!!)"; // matches both < and
															// </, but ignores
															// <!
	private static final String CLOSE_PATTERN = "/?(?<!-)>"; // matches both >
																// and />, but
																// ignores ->
	private static final String ASSIGN_PATTERN = "(?<!=)=(?!=)?(?=['\"(\\s)*])";
	private static final String EOF_PATTERN = "&\\$";

	/* Some special error detection patterns */
	private static final String AMPERSAND_PATTERN = "(&)+(?=.*?)";
	private static final String TEMP_ASSIGN_PATTERN = "(=)+";
	private static final String FORWARD_SLASHES_OPEN_PATTERN = "(?<=</)(.?)*[/]+";
	private static final String FORWARD_SLASHES_CLOSE_PATTERN = "[/]+(?=(.*?)/>)";

	public static enum TokenType {
		NAME(NAME_PATTERN), STRING(STRING_PATTERN), DATA(DATA_PATTERN), OPEN(OPEN_PATTERN), CLOSE(CLOSE_PATTERN), LTSL(
				null), SLGT(null), ASSIGN(ASSIGN_PATTERN), COMMENT(COMMENT_PATTERN), EPSILON(null), EOF(
						EOF_PATTERN), AMPERSAND(AMPERSAND_PATTERN), FWCSLASHES(
								FORWARD_SLASHES_CLOSE_PATTERN), FWOSLASHES(
										FORWARD_SLASHES_OPEN_PATTERN), TEMP(TEMP_ASSIGN_PATTERN);

		public final String pattern;

		private TokenType(String pattern) {
			this.pattern = pattern;
		}
	}

	// The XML-- document to tokenize shall be stored in this string
	private String document;

	/**
	 * Constructs a new lexer, given the input document.
	 * 
	 * @param document
	 *            the XML-- document to parse
	 */
	public Lexer(String document) {
		this.document = document;
	}

	/**
	 * Scans and tokenizes an XML-- document into a stream of tokens. For each
	 * recognized token, its type and value are returned.
	 * 
	 * @return a list of recognized tokens
	 * @throws LexerException
	 *             if a scanning error occurs
	 */
	public List<Token> getTokenStream() throws LexerException {
		// Verify that we have a valid document
		if (document.isEmpty()) {
			throw new LexerException("Failed to scan the specified XML-- file. It may be empty or nonexistent.");
		}

		// Represents the stream of tokens to return to the parser
		List<Token> tokens = new ArrayList<Token>();

		// Lexer logic begins here
		StringBuilder tokenPatternBuffer = new StringBuilder();

		for (TokenType type : TokenType.values()) {
			tokenPatternBuffer.append(String.format("|(?<%s>%s)", type.name(), type.pattern));
		}

		Pattern p = Pattern.compile(new String(tokenPatternBuffer.substring(1)));
		Matcher m = p.matcher(document);

		// Begin matching tokens
		while (m.find()) {
			if (m.group().matches(COMMENT_PATTERN) || m.group().matches(WHITESPACE_PATTERN)) {
				continue;
			} else if (m.group(TokenType.NAME.name()) != null) {
				if (m.group().contains("*") || m.group().contains("+") || m.group().contains("[")
						|| m.group().contains("]") || m.group().contains("(") || m.group().contains(")")
						|| m.group().contains("{") || m.group().contains("}") || m.group().contains("$")
						|| m.group().contains("@") || m.group().contains("#") || m.group().contains(";")
						|| m.group().contains("?") || m.group().contains(",") || m.group().contains("!")
						|| m.group().contains("%") || m.group().contains("^") || m.group().contains("|")
						|| m.group().contains("`") || m.group().contains("~")) {
					throw new LexerException("Scanner error: Illegal character in token '" + m.group() + "'.");
				} else {
					tokens.add(new Token(TokenType.NAME, m.group(TokenType.NAME.name())));
					continue;
				}
			} else if (m.group(TokenType.STRING.name()) != null) {
				tokens.add(new Token(TokenType.STRING, m.group(TokenType.STRING.name())));
				continue;
			} else if (m.group(TokenType.DATA.name()) != null) {
				if (m.group().contains(" ") || m.group().contains("\t") || m.group().contains("\n")
						|| m.group().contains("\r")) {
					/*
					 * Since DATA may contain whitespace, split as necessary to
					 * remove any whitespace and keep the individual DATA
					 * tokens.
					 */
					String[] dataParts = m.group().split("\\s+");
					for (int i = 0; i < dataParts.length; i++) {
						if (dataParts[i].isEmpty()) {
							continue;
						} else {
							tokens.add(new Token(TokenType.DATA, dataParts[i]));
						}
					}
					continue;
				} else {
					tokens.add(new Token(TokenType.DATA, m.group(TokenType.DATA.name())));
					continue;
				}
			} else if (m.group(TokenType.OPEN.name()) != null) {
				if (m.group().equals("</")) {
					tokens.add(new Token(TokenType.LTSL, m.group(TokenType.OPEN.name())));
					continue;
				} else {
					tokens.add(new Token(TokenType.OPEN, m.group(TokenType.OPEN.name())));
					continue;
				}
			} else if (m.group(TokenType.CLOSE.name()) != null) {
				if (m.group().equals("/>")) {
					tokens.add(new Token(TokenType.SLGT, m.group(TokenType.CLOSE.name())));
					continue;
				} else {
					tokens.add(new Token(TokenType.CLOSE, m.group(TokenType.CLOSE.name())));
					continue;
				}
			} else if (m.group(TokenType.ASSIGN.name()) != null) {
				tokens.add(new Token(TokenType.ASSIGN, m.group(TokenType.ASSIGN.name())));
				continue;
			} else if (m.group(TokenType.AMPERSAND.name()) != null) {
				throw new LexerException("Scanner error: Cannot scan illegal usage of special symbol '&'.");
			} else if (m.group(TokenType.FWOSLASHES.name()) != null) {
				throw new LexerException("Scanner error: Too many forward slashes in end tag.");
			} else if (m.group(TokenType.FWCSLASHES.name()) != null) {
				throw new LexerException("Scanner error: Too many forward slashes in empty tag.");
			} else if (m.group(TokenType.TEMP.name()) != null) {
				tokens.add(new Token(TokenType.DATA, m.group(TokenType.TEMP.name())));
				continue;
			} else {
				break;
			}
		}
		// Finally, append EOF to the end of the token stream
		tokens.add(new Token(TokenType.EOF, "&$"));

		return tokens;
	}
}
