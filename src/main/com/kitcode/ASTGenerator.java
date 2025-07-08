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
	    LineNum.add(Integer.toString(indentation));
            Type.add(ruleName);
            Content.add(ctx.getText());
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
            writeDOT(writer);
            writer.write("}\n");
        }
        
        System.out.println("Generated AST for " + javaFile.getName() + " -> " + fileName);
    }
    
    private static void writeDOT(FileWriter writer) throws IOException {
        writeLabel(writer);
        int pos = 0;
        for(int i=1; i<LineNum.size();i++){
            pos=getPos(Integer.parseInt(LineNum.get(i))-1, i);
            writer.write((Integer.parseInt(LineNum.get(i))-1)+Integer.toString(pos)+"->"+LineNum.get(i)+i+"\n");
        }
    }
    
    private static void writeLabel(FileWriter writer) throws IOException {
        for(int i =0; i<LineNum.size(); i++){
            String escapedContent = Content.get(i).replace("\\", "\\\\")
                                                  .replace("\"", "\\\"")
                                                  .replace("\n", "\\n")
                                                  .replace("\r", "\\r")
                                                  .replace("\t", "\\t");
            writer.write(LineNum.get(i)+i+"[label=\""+Type.get(i)+"\\n "+escapedContent+" \"]\n");
        }
    }
    
    private static int getPos(int n, int limit){
        int pos = 0;
        for(int i=0; i<limit;i++){
            if(Integer.parseInt(LineNum.get(i))==n){
                pos = i;
            }
        }
        return pos;
    }
}
