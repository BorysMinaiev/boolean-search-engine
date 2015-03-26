import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Scanner;

/**
 * Created by Borys Minaiev on 26.03.15.
 */
public class Searcher {

    // grammar:
    // init = C
    // A = word | not A | (C)
    // B = A (and B)
    // C = B (or C)

    private static final int MAX_RESULTS = 10;
    private static final int LINES_FOR_SNIPPED = 3;
    private static BitSet wholeIndex;
    private static final int MAX_DIST_BETWEEN_TOKENS = 4;

    private static BitSet parseA() {
        String curToken = getCurrentToken();
        if ("not".equals(curToken)) {
            it++;
            BitSet result = (BitSet) wholeIndex.clone();
            result.xor(parseA());
            return result;
        }
        if ("(".equals(curToken)) {
            it++;
            BitSet result = parseC();
            it++;
            return result;
        }
        it++;
        return getBitSetForWord(curToken);
    }

    private static BitSet getBitSetForWord(final String word) {
        if (word == null || word.length() == 0) {
            return new BitSet();
        }
        char first = word.charAt(0);
        BitSet result = new BitSet();
        try {
            FileReader reader = new FileReader("index/" + first + ".txt");
            Scanner scanner = new Scanner(reader);
            while (scanner.hasNext()) {
                String curWord = scanner.next();
                int cnt = scanner.nextInt();
                boolean write = word.equals(curWord);
                for (int i = 0; i < cnt; i++) {
                    int pos = scanner.nextInt();
                    if (write) {
                        result.set(pos);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static BitSet parseB() {
        BitSet result = parseA();
        if ("and".equals(getCurrentToken())) {
            it++;
            BitSet another = parseB();
            result.and(another);
        }
        return result;
    }

    private static BitSet parseC() {
        BitSet result = parseB();
        if ("or".equals(getCurrentToken())) {
            it++;
            BitSet another = parseC();
            result.or(another);
        }
        return result;
     }

    private static ArrayList<String> parseTokens(String s) {
        ArrayList<String> res = new ArrayList<String>();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '(' || c == ')') {
                res.add(s.substring(i, i + 1));
                i++;
            } else {
                if (Character.isAlphabetic(c)) {
                    int j = i;
                    while (j < s.length() && Character.isAlphabetic(s.charAt(j))) {
                        j++;
                    }
                    res.add(s.substring(i, j));
                    i = j;
                } else {
                    i++;
                }
            }
        }
        return res;
    }

    private static int it;
    private static ArrayList<String> tokens;

    private static String getCurrentToken() {
        return it == tokens.size() ? null : tokens.get(it).toLowerCase();
    }

    private static void answerForQuery(String query) {
        System.out.println("looking for \"" + query + "\"");
        tokens = parseTokens(query);
        it = 0;
        BitSet result = parseC();
        System.out.println("found " + result.cardinality() + " results");
        System.out.println("top result:");
        int cntResults = 0;
        for (int i = 0; i < filesLocation.size(); i++) {
            if (result.get(i)) {
                cntResults++;
                System.out.println(filesLocation.get(i));
                printSnippedForFile(filesLocation.get(i));
            }
            if (cntResults == MAX_RESULTS) {
                break;
            }
        }
    }

    private static ArrayList<String> processLine(final String line, HashSet<String> interestingTokens) {
        ArrayList<String> res = new ArrayList<String>();
        String[] tokens = line.split("[^a-zA-Z]+");
        boolean[] isTokenInteresting = new boolean[tokens.length];
        int it = 0;
        for (String token : tokens) {
            if (token.length() > 0) {
                if (interestingTokens.contains(token.toLowerCase())) {
                    isTokenInteresting[it] = true;
                    for (int k = -MAX_DIST_BETWEEN_TOKENS; k < MAX_DIST_BETWEEN_TOKENS; k++) {
                        if (it + k >= 0 && it + k < isTokenInteresting.length) {
                            isTokenInteresting[it + k] = true;
                        }
                    }
                    interestingTokens.remove(token.toLowerCase());
                }
            }
            it++;
        }
        for (int i = 0; i < tokens.length;) {
            if (isTokenInteresting[i]) {
                int j = i;
                String currentString = "...";
                while (j < tokens.length && isTokenInteresting[j]) {
                    currentString = currentString + " " + tokens[j];
                    j++;
                }
                currentString += " ...";
                res.add(currentString);
                i = j;
            } else {
                i++;
            }
        }
        return res;
    }
    private static ArrayList<String> processDocument(Node d, HashSet<String> tokens) {
        ArrayList<String> res = new ArrayList<String>();
        NodeList list = d.getChildNodes();
        if (d.getNodeName().equals("p") || d.getNodeName().equals("head")) {
            res.addAll(processLine(list.item(0).getNodeValue(), tokens));
        }
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            res.addAll(processDocument(node, tokens));
        }
        return res;
    }

    private static void printSnippedForFile(String fileName) {
        HashSet<String> interestingTokens = new HashSet<String>();
        for (String s : tokens) {
            if ("not".equals(s) || "or".equals(s) || "and".equals(s)) {
                continue;
            }
            interestingTokens.add(s);
        }
        try {
            FileReader reader = new FileReader(fileName);
            DOMParser parser = new DOMParser();
            try {
                parser.parse(new InputSource(reader));
                Document d = parser.getDocument();
                ArrayList<String> res = processDocument(d, interestingTokens);
                for (int i = 0; i < Math.min(res.size(), LINES_FOR_SNIPPED); i++) {
                    System.out.println(res.get(i));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<String> filesLocation;

    private static void readIndex() {
        filesLocation = new ArrayList<String>();
        try {
            FileReader reader = new FileReader("index/index.txt");
            Scanner scanner = new Scanner(reader);
            int indexSize = scanner.nextInt();
            wholeIndex = new BitSet();
            for (int i = 0; i < indexSize; i++) {
                wholeIndex.set(i);
                filesLocation.add(scanner.next());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        readIndex();
        Scanner scanner = new Scanner(System.in);
//        String query = "caesar and to and (not Treaty or treaty)";
        String query = scanner.next();
        answerForQuery(query);
    }
}
