import com.sun.corba.se.impl.orbutil.ObjectWriter;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Borys Minaiev on 14.03.15.
 */
public class Indexer {

    private static int totalNumberOfFiles = 0;
    private static int numberOfWords = 0;
    private static Map<String, List<Integer>> occurrences = new HashMap<String, List<Integer>>();
    private static List<String> documents = new ArrayList<String>();


    private static void processWord(final String word) {
        numberOfWords++;
        int docId = documents.size() - 1;
        List<Integer> list = occurrences.get(word);
        if (list == null) {
            list = new ArrayList<Integer>();
            occurrences.put(word, list);
        }
        if (list.size() > 0 && list.get(list.size() - 1).equals(docId)) {
            return;
        }
        list.add(docId);
    }

    private static void processLine(final String line) {
        String[] tokens = line.split("[^a-zA-Z]+");
        for (String token : tokens) {
            if (token.length() > 0) {
                processWord(token.toLowerCase());
            }
        }
    }

    private static void processDocument(final Node d) {
        NodeList list = d.getChildNodes();
        if (d.getNodeName().equals("p") || d.getNodeName().equals("head")) {
            processLine(list.item(0).getNodeValue());
        }
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            processDocument(node);
        }
    }

    public static void findAllFiles(File f) {
        if (f.isDirectory()) {
            File[] all = f.listFiles();
            for (File nextF : all) {
                findAllFiles(nextF);
            }
        } else {
            if (!f.getName().endsWith(".xml")) {
                return;
            }
            System.out.println(f.getAbsolutePath());
            totalNumberOfFiles++;
            documents.add(f.getAbsolutePath());
            try {
                FileReader reader = new FileReader(f);
                DOMParser parser = new DOMParser();
                try {
                    parser.parse(new InputSource(reader));
                    Document d = parser.getDocument();
                    processDocument(d);
                } catch (SAXException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
            }
        }
    }

    private static void rmDir(File f) {
        if (f.isDirectory()) {
            File[] all = f.listFiles();
            for (File nextF : all) {
                rmDir(nextF);
            }
        }
        f.delete();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usege: Indexer path_to_corpus");
            System.exit(1);
        }
        long START = System.currentTimeMillis();
        File f = new File(args[0]);
        System.out.println("start indexing...");
        findAllFiles(f);
        System.out.println("indexing finished");
        System.out.println("total number of files = " + totalNumberOfFiles);
        System.out.println("total number of termins = " + numberOfWords);
        System.out.println("total number of tokens = " + occurrences.size());
        System.out.println("time used = " + (System.currentTimeMillis() - START) + " ms");

        File file = new File("./index/");
        if (file.mkdir()) {
//            System.err.println("folder has been created");
        } else {
            rmDir(file);
            file.mkdir();
//            System.err.println("folder has not been created");
        }
        try {
            PrintWriter writer = new PrintWriter("./index/index.txt");
            writer.println(totalNumberOfFiles);
            for (int i = 0; i < totalNumberOfFiles; i++) {
                writer.println(documents.get(i));
            }
            writer.close();
            for (char c = 'a'; c <= 'z'; c++) {
                PrintWriter out = new PrintWriter("./index/" + c + ".txt");
                for (Map.Entry<String, List<Integer>> entry : occurrences.entrySet()) {
                    if (entry.getKey().charAt(0) == c) {
                        out.println(entry.getKey());
                        List<Integer> res = entry.getValue();
                        out.print(res.size());
                        for (int x : res) {
                            out.print(" " + x);
                        }
                        out.println();
                    }
                }
                out.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
