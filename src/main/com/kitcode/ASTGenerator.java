package com.kitcode;

import antlr.Java8Lexer;
import antlr.Java8Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

public class ASTGenerator {

    static ArrayList<String> LineNum;
    static ArrayList<String> Type;
    static ArrayList<String> Content;
    static ArrayList<String> TypeName;
    static ArrayList<String> StartLine;
    static ArrayList<String> EndLine;

    private static String readFile(String fileName) throws IOException {
        File file = new File(fileName);
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, Charset.forName("UTF-8"));
    }

    public static void main(String args[]) throws IOException{
        File javaDir = new File("resource/java");
        File[] javaFiles = javaDir.listFiles((dir, name) -> name.endsWith(".java"));
        
        if (javaFiles == null || javaFiles.length == 0) {
            System.out.println("No Java files found in resource/java directory");
            return;
        }
        
        for (File javaFile : javaFiles) {
            processJavaFile(javaFile);
        }
    }

    private static void generateAST(RuleContext ctx, boolean verbose, int indentation) {
        boolean toBeIgnored = !verbose && ctx.getChildCount() == 1 && ctx.getChild(0) instanceof ParserRuleContext;

        if (!toBeIgnored) {
            String ruleName = Java8Parser.ruleNames[ctx.getRuleIndex()];
            int ruleIndex = ctx.getRuleIndex();
            
            // Get line numbers from the context
            int startLine = 1;
            int endLine = 1;
            if (ctx instanceof ParserRuleContext) {
                ParserRuleContext parserCtx = (ParserRuleContext) ctx;
                startLine = parserCtx.getStart() != null ? parserCtx.getStart().getLine() : 1;
                endLine = parserCtx.getStop() != null ? parserCtx.getStop().getLine() : startLine;
            }
            
            LineNum.add(Integer.toString(indentation));
            Type.add(Integer.toString(ruleIndex));
            TypeName.add(ruleName);
            Content.add(ctx.getText());
            StartLine.add(Integer.toString(startLine));
            EndLine.add(Integer.toString(endLine));
        }
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree element = ctx.getChild(i);
            if (element instanceof RuleContext) {
                generateAST((RuleContext) element, verbose, indentation + (toBeIgnored ? 0 : 1));
            }
        }
    }
    
    private static void processJavaFile(File javaFile) throws IOException {
        LineNum = new ArrayList<String>();
        Type = new ArrayList<String>();
        Content = new ArrayList<String>();
        TypeName = new ArrayList<String>();
        StartLine = new ArrayList<String>();
        EndLine = new ArrayList<String>();
        
        String inputString = readFile(javaFile.getPath());
        ANTLRInputStream input = new ANTLRInputStream(inputString);
        Java8Lexer lexer = new Java8Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Java8Parser parser = new Java8Parser(tokens);
        
        // Add error listener to detect parsing errors
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.err.println("Parsing error in " + javaFile.getName() + " at line " + line + ":" + charPositionInLine + " - " + msg);
            }
        });
        
        ParserRuleContext ctx = parser.compilationUnit();
        
        // Check if parsing was successful
        if (parser.getNumberOfSyntaxErrors() > 0) {
            System.err.println("Skipping " + javaFile.getName() + " due to syntax errors");
            return;
        }

        generateAST(ctx, false, 0);

        File graphsDir = new File("graphs");
        if (!graphsDir.exists()) {
            graphsDir.mkdirs();
        }
        
        String fileName = javaFile.getName().replace(".java", ".dot");
        String outputPath = "graphs/" + fileName;
        
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("digraph G {\n");
            writeDOT(writer, null);
            writer.write("}\n");
        }
        
        System.out.println("Generated AST for " + javaFile.getName() + " -> " + fileName);
    }

    public static boolean generateDotFromString(String inputString, String outputPath, String header) throws IOException {
        LineNum = new ArrayList<String>();
        Type = new ArrayList<String>();
        Content = new ArrayList<String>();
        TypeName = new ArrayList<String>();
        StartLine = new ArrayList<String>();
        EndLine = new ArrayList<String>();

        ANTLRInputStream input = new ANTLRInputStream(inputString);
        Java8Lexer lexer = new Java8Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Java8Parser parser = new Java8Parser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.err.println("Parsing error at line " + line + ":" + charPositionInLine + " - " + msg);
            }
        });

        ParserRuleContext ctx = parser.compilationUnit();
        if (parser.getNumberOfSyntaxErrors() > 0) {
            return false;
        }

        generateAST(ctx, false, 0);

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("digraph G {\n");
            writeDOT(writer, header);
            writer.write("}\n");
        }

        return true;
    }

    public static boolean parseOnly(String inputString, String sourceName) {
        ANTLRInputStream input = new ANTLRInputStream(inputString);
        Java8Lexer lexer = new Java8Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Java8Parser parser = new Java8Parser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                if (sourceName != null && !sourceName.isEmpty()) {
                    System.err.println("Parsing error in " + sourceName + " at line " + line + ":" + charPositionInLine + " - " + msg);
                } else {
                    System.err.println("Parsing error at line " + line + ":" + charPositionInLine + " - " + msg);
                }
            }
        });

        parser.compilationUnit();
        return parser.getNumberOfSyntaxErrors() == 0;
    }
    
    private static void writeDOT(FileWriter writer, String header) throws IOException {
        if (header != null && !header.isEmpty()) {
            writer.write("graph [comment=\"" + header + "\"]\n");
        }
        // Store original indentation levels before writeLabel modifies LineNum
        ArrayList<String> originalIndentations = new ArrayList<String>(LineNum);
        
        writeLabel(writer);
        
        // Generate edges based on the tree structure
        for(int i = 1; i < LineNum.size(); i++) {
            int currentLevel = Integer.parseInt(originalIndentations.get(i));
            
            // Find parent (previous node with level currentLevel-1)
            for(int j = i - 1; j >= 0; j--) {
                int parentLevel = Integer.parseInt(originalIndentations.get(j));
                if(parentLevel == currentLevel - 1) {
                    writer.write("\"" + LineNum.get(j) + "\" -> \"" + LineNum.get(i) + "\"\n");
                    break;
                }
            }
        }
    }
    
    private static void writeLabel(FileWriter writer) throws IOException {
        for(int i = 0; i < LineNum.size(); i++) {
            String escapedContent = Content.get(i).replace("\\", "\\\\")
                                                  .replace("\"", "\\\"")
                                                  .replace("\n", "\\n")
                                                  .replace("\r", "\\r")
                                                  .replace("\t", "\\t");
            
            // Generate a unique node ID using object hash-like approach
            String nodeId = Integer.toString(Math.abs((escapedContent + i).hashCode()));
            
            writer.write("\"" + nodeId + "\" [ label=\"" + escapedContent + "\" ");
            writer.write("type=" + Type.get(i) + " ");
            writer.write("typeName=" + TypeName.get(i) + " ");
            writer.write("startLineNumber=" + StartLine.get(i) + " ");
            writer.write("endLineNumber=" + EndLine.get(i) + " ");
            writer.write("]\n");
            
            // Store the nodeId for edge generation
            LineNum.set(i, nodeId);
        }
    }
    
}
