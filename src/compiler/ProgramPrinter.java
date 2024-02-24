package compiler;

import gen.MiniJavaListener;
import gen.MiniJavaParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;

public class ProgramPrinter implements MiniJavaListener {

    public class SymbolTable {
        Hashtable<String, String> table;
        public SymbolTable parent;
        public ArrayList<SymbolTable> children;
        String scopeName;
        int[] appearance;
        public SymbolTable(SymbolTable parent, String scopeName, int line, int column) {
            this.table = new Hashtable<>();
            this.parent = parent;
            this.scopeName = scopeName;
            this.appearance = new int[]{line, column};
            this.children = new ArrayList<>();
        }

        public String toString() {
            String tree = new String();
            tree += this.scopeName + " : [" + this.appearance[0] + ", " + this.appearance[1] + "] ";
            int size = tree.length();
            for (int i = 60; i > size; i--)
                tree += "#";
            tree += "\n";
            for (String value : this.table.values()) {
                if (value.startsWith("Class"))
                    tree += "\033[30m";
                else if (value.startsWith("Method"))
                    tree += "\033[32m";
                else if (value.startsWith("Interface"))
                    tree += "\033[37m";
                else if (value.startsWith("Field"))
                    tree += "\033[93m";
                else if (value.startsWith("Parameter"))
                    tree += "\033[35m";
                else if (value.startsWith("LocalVar"))
                    tree += "\033[34m";
                String[] parts = value.split("\\)\\s\\(");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].replace("(", "").replace(")", "").replace("  ", " ");
                }
                tree += "------------------------------------------------------------\n" + parts[0] + "\n";
                for (int i = 1; i < parts.length; i++)
                    tree += "\t" + parts[i] + "\n";
                tree += "------------------------------------------------------------\033[0m\n";
            }
            tree += "############################################################\n\n\n";
            for (SymbolTable child : this.children)
                tree += child.toString();
            return tree;
        }

        private void checkDefined() {
            for (String key : this.table.keySet()) {
                String val = this.table.get(key);
                while (val.contains("?")) {
                    int endIdx = val.indexOf(", isDefined = ?");
                    int startIdx = val.substring(0, endIdx).lastIndexOf("= ") + 2;
                    String type = val.substring(startIdx, endIdx);
                    boolean found = false;
                    for (String s : defined) {
                        if (s.equals(type)) {
                            found = true;
                            break;
                        }
                    }
                    val = val.substring(0, val.indexOf("?")) + ((found)?"true":"false") + val.substring(val.indexOf("?") + 1);
                }
                this.table.put(key, val);
            }
            for (SymbolTable child: this.children) {
                child.checkDefined();
            }
        }

        private void checkParameters() {
            String parameter = "";
            for (String key : this.table.keySet()) {
                String val = this.table.get(key);
                if (val.startsWith("Parameter:")) {
                    if (!parameter.isEmpty())
                        parameter += ", ";
                    String type = val.substring(val.indexOf("(type: ") + 7);
                    parameter += "[" + type.substring(0, type.indexOf(")"));
                    String index = val.substring(val.indexOf("(index: ") + 8);
                    parameter += ", " + index.substring(0, index.indexOf(")")) + "]";
                }
            }
            if (!parameter.isEmpty()) {
                parameter = "(parametersType: " + parameter + " )";
                this.parent.table.put("method_" + this.scopeName, this.parent.table.get("method_" + this.scopeName) + parameter);
            }
            for (SymbolTable child: this.children) {
                child.checkParameters();
            }
        }
    }

    private ArrayList<String> javaCode;
    private int indent;
    private String ret;
    public String name;
    private String sum_type;
    boolean deadCode;
    public SymbolTable symbolTable;
    private SymbolTable currentTable;
    ArrayList<String> defined;
    ArrayList<String[]> is_used;

    public ProgramPrinter() {
        javaCode = new ArrayList<>();
        indent = 0;
        ret = new String();
        name = new String();
        sum_type = new String();
        symbolTable = new SymbolTable(null, "Program", 0, 0);
        defined = new ArrayList<>();
        defined.add("String");
        defined.add("Object");
        is_used = new ArrayList<String[]>();
        deadCode = false;
    }

    public boolean getJavaCode() {
        try {
            FileWriter fileWriter = new FileWriter(".\\test\\" + name + ".java");
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            for (String element : javaCode) {
                bufferedWriter.write(element + "\n");
            }

            bufferedWriter.close();
            System.out.println("\n||||||||||||||||||Code created successfully|||||||||||||||||\n");
            return true;
        } catch (Exception e) {
            System.out.println("Error writing to file: " + e.getMessage());
            return false;
        }
    }

    public String checkInheritanceError(Hashtable<String, String> classes) {
        for (String key: classes.keySet()) {
            if (key.startsWith("class")) {
                ArrayList<String> visited = new ArrayList<>();
                String node = classes.get(key);
                visited.add(node.substring(14, node.indexOf(")")));
                do {
                    String extended = node.substring(node.indexOf("extends:") + 9);
                    if (extended.contains("|"))
                        extended = extended.substring(0, extended.indexOf("|"));
                    else
                        extended = extended.substring(0, extended.indexOf(")"));
                    for (String sup: visited) {
                        if (extended.equals(sup)) {
                            visited.add(extended);
                            String error = "Error410 : Invalid inheritance ";
                            for (String str: visited) {
                                error += "[" + str + "] -> ";
                            }
                            return error.substring(0, error.length() - 4);
                        }
                    }
                    visited.add(extended);
                    node = classes.get("class_" + extended);
                } while (node != null && node.contains("extends:"));
            }
        }
        return "";
    }

    public String checkImplementationError(SymbolTable main) {
        String error_imp = new String();
        for (String key: main.table.keySet()) {
            if (key.startsWith("class") && main.table.get(key).contains("implements:")) {
                String implemented = main.table.get(key);
                implemented = implemented.substring(implemented.indexOf("implements:") + 11);
                implemented = implemented.substring(0, implemented.indexOf(")"));
                String[] imps = implemented.split("\\,");
                ArrayList<String> must_have = new ArrayList<>();
                ArrayList<String> eq_inf = new ArrayList<>();
                ArrayList<Integer> eq_acc = new ArrayList<>();
                for (SymbolTable find_inf: main.children) {
                    for (String inf: imps) {
                        if (find_inf.scopeName.equals(inf.replace(" ", ""))) {
                            for (String h: find_inf.table.values()) {
                                if (h.startsWith("Method")) {
                                    must_have.add(h.substring(h.indexOf("name: ") + 6, h.indexOf(")")));
                                    eq_inf.add(find_inf.scopeName);
                                    eq_acc.add((h.charAt(h.indexOf("ACCESS_MODIFIER_P") + 17) == 'U')?1:0);
                                }
                            }
                        }
                    }
                }
                for (SymbolTable find_class: main.children) {
                    if (find_class.scopeName.equals(key.substring(6))) {
                        for (String h : find_class.table.values()) {
                            if (h.startsWith("Method")) {
                                String method = h.substring(h.indexOf("name: ") + 6, h.indexOf(")"));
                                if (must_have.contains(method)) {
                                    if (h.charAt(h.indexOf("ACCESS_MODIFIER_P") + 17) == 'R' &&
                                            eq_acc.get(must_have.indexOf(method)) == 1) {
                                        for (SymbolTable child: find_class.children) {
                                            if (child.scopeName.equals(method)) {
                                                error_imp += "Error320: in line [ " + child.appearance[0] + " : " +
                                                        child.appearance[1] + " ], the access level cannot be more" +
                                                        " restrictive than the overridden method's access level\n";
                                            }
                                        }
                                    }
                                    eq_acc.remove(must_have.indexOf(method));
                                    eq_inf.remove(must_have.indexOf(method));
                                    must_have.remove(method);
                                }
                            }
                        }
                    }
                }
                if (!must_have.isEmpty()) {
                    error_imp += "Error420: Class [" + key.substring(6) + "] must implement all abstract methods :\n" +
                            "\t Method(s) " + must_have + " From " + eq_inf + " Interface(s)\n";
                }
            }
        }
        return error_imp;
    }

    public ArrayList<String> checkForErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add(checkInheritanceError(symbolTable.table));
        errors.add(checkImplementationError(symbolTable));
        return errors;
    }

    public void checkUsed() {
        for (int j = 0; j < is_used.size(); j++ ) {
            for (int i = 0; i < javaCode.size(); i++) {
                if (i != Integer.parseInt(is_used.get(j)[1]) && javaCode.get(i).contains(is_used.get(j)[0]))
                    is_used.remove(is_used.get(j));
            }
        }
        for (int i = is_used.size() - 1; i >= 0; i--)
            javaCode.remove(Integer.parseInt(is_used.get(i)[1]));
        boolean remove_flag = false;
        for (int i = javaCode.size() - 1; i >= 0; i--) {
            if (javaCode.get(i).startsWith("*/"))
                remove_flag = true;
            if (javaCode.get(i).startsWith("/*")) {
                remove_flag = false;
                javaCode.remove(i);
            }
            if (remove_flag)
                javaCode.remove(i);
        }
    }

    @Override
    public void enterProgram(MiniJavaParser.ProgramContext ctx) {
        /* Symbol Table */
        currentTable = symbolTable;
    }

    @Override
    public void exitProgram(MiniJavaParser.ProgramContext ctx) {
        /* Symbol Table */
        currentTable = symbolTable.parent;
        symbolTable.checkDefined();
        symbolTable.checkParameters();
        ArrayList<String> errors = this.checkForErrors();
        for (String e: errors) {
            System.out.println("\033[31m" + e + "\033[0m");
        }
        this.checkUsed();
    }

    @Override
    public void enterMainClass(MiniJavaParser.MainClassContext ctx) {
        /* Symbol Table */
        defined.add(ctx.className.getText());
        SymbolTable temp = new SymbolTable(currentTable, ctx.className.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable.table.put("class_" + ctx.className.getText(), "Class: (name: " + ctx.className.getText() + ")");
        currentTable = temp;
        /* Java Code Generation */
        name = ctx.className.getText();
        javaCode.add("public class " + ctx.className.getText() + " {");
        indent++;
    }

    @Override
    public void exitMainClass(MiniJavaParser.MainClassContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        indent--;
        javaCode.add("}");
    }

    @Override
    public void enterMainMethod(MiniJavaParser.MainMethodContext ctx) {
        /* Symbol Table */
        SymbolTable temp = new SymbolTable(currentTable, "mainMethod", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        String parameterType = "", dType = ctx.dataType.getText();
        if (dType.contains("[]")) {
            parameterType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            parameterType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            parameterType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            parameterType += "[classType = " + kind + ", isDefined = ?]";
        }

        currentTable.table.put("method_main", "Method: (name: main) (returnType: void) " +
                "(accessModifier: ACCESS_MODIFIER_PUBLIC) (parametersType: [" + parameterType + " , index: 1] )");
        currentTable = temp;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "public static void main(" + dType + " " + ctx.args.getText() + ") {");
        indent++;
    }

    @Override
    public void exitMainMethod(MiniJavaParser.MainMethodContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        indent--;
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "}");
    }

    @Override
    public void enterClassDeclaration(MiniJavaParser.ClassDeclarationContext ctx) {
        /* Symbol Table */
        defined.add(ctx.className.getText());
        SymbolTable temp = new SymbolTable(currentTable, ctx.className.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        String ex = "", im = "";
        int startIndex = ctx.getText().indexOf("implements") + "implements".length();
        int endIndex = ctx.getText().indexOf("{");
        if (ctx.extended != null)
            ex = "extends: " + ctx.extended.getText();
        if (ctx.implemented != null) {
            im = "implements: " + ctx.getText().substring(startIndex, endIndex).replace(",", ", ");
        }
        currentTable.table.put("class_" + ctx.className.getText(), "Class: (name: " + ctx.className.getText() + ") " +
                (((im + ex).isEmpty())?"":"(" + ((im.isEmpty() || ex.isEmpty())?ex + im:ex + "|" + im) + ")" ));
        currentTable = temp;
        /* Java Code Generation */
        javaCode.add("class " + ctx.className.getText() +
                ((ctx.extended == null)?"":" extends " + ctx.extended.getText()) +
                ((ctx.implemented == null)?"":" implements " + ctx.getText().substring(startIndex, endIndex)) + " {");
        indent++;
    }

    @Override
    public void exitClassDeclaration(MiniJavaParser.ClassDeclarationContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        indent--;
        javaCode.add("}");
    }

    @Override
    public void enterInterfaceDeclaration(MiniJavaParser.InterfaceDeclarationContext ctx) {
        /* Symbol Table */
        defined.add(ctx.interfaceName.getText());
        SymbolTable temp = new SymbolTable(currentTable, ctx.interfaceName.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable.table.put("interface_" + ctx.interfaceName.getText(),
                "Interface: (name: " + ctx.interfaceName.getText() + ")");
        currentTable = temp;
        /* Java Code Generation */
        javaCode.add("interface " + ctx.interfaceName.getText() + " {");
        indent++;
    }

    @Override
    public void exitInterfaceDeclaration(MiniJavaParser.InterfaceDeclarationContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        indent--;
        javaCode.add("}");
    }

    @Override
    public void enterInterfaceMethodDeclaration(MiniJavaParser.InterfaceMethodDeclarationContext ctx) {
        /* Symbol Table */
        SymbolTable temp = new SymbolTable(currentTable, ctx.methodName.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        String returnType = "", dType = ctx.retType.getText(), access = "(accessModifier: ACCESS_MODIFIER_PUBLIC) ";
        if (dType.contains("[]")) {
            returnType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            returnType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            returnType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            returnType += "[classType = " + kind + ", isDefined = ?]";
        }
        if (ctx.accModifier != null)
            access = "(accessModifier: ACCESS_MODIFIER_" + ctx.accModifier.getText().toUpperCase() + ") ";
        currentTable.table.put("method_" + ctx.methodName.getText(),
                "Method: (name: " + ctx.methodName.getText() + ") " +
                "(returnType: " + returnType + ") " + access);
        currentTable = temp;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        String type = ctx.retType.getText();
        boolean isArray = type.contains("[]");
        if (isArray) type = type.substring(0, type.indexOf("["));
        if (type.equals("number")) type = "int";
        javaCode.add((spacing +
                ((ctx.accModifier == null)?"public":ctx.accModifier.getText()) + " abstract " +
                type + ((isArray)?"[] ":" ") +
                ctx.methodName.getText() + "();"));
    }

    @Override
    public void exitInterfaceMethodDeclaration(MiniJavaParser.InterfaceMethodDeclarationContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
    }

    @Override
    public void enterFieldDeclaration(MiniJavaParser.FieldDeclarationContext ctx) {
        /* Symbol Table */
        String dataType = "", dType = ctx.dataType.getText(), access = "(accessModifier: ACCESS_MODIFIER_PUBLIC) ";
        if (dType.contains("[]")) {
            dataType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            dataType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            dataType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            dataType += "[classType = " + kind + ", isDefined = ?]";
        }
        if (ctx.accModifier != null)
            access = "(accessModifier: ACCESS_MODIFIER_" + ctx.accModifier.getText().toUpperCase() + ") ";
        currentTable.table.put("var_" + ctx.varName.getText(),
                "Field: (name: " + ctx.varName.getText() + ") " + ((ctx.isFinal == null)?"":"(Final) ") +
                        "(type: " + dataType + ") " + access);
        sum_type = dataType;
        if (sum_type.contains("[classType = ")) {
            sum_type = sum_type.substring(sum_type.indexOf("[classType = ") + 13);
            sum_type = sum_type.substring(0, sum_type.indexOf(","));
        } else if (sum_type.contains("array of")) {
            sum_type = sum_type.substring(9);
        }
        /* Java Code Generation */
        is_used.add(new String[]{ctx.varName.getText(), String.valueOf(javaCode.size())});
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        String type = ctx.dataType.getText();
        boolean isArray = type.contains("[]");
        if (isArray) type = type.substring(0, type.indexOf("["));
        if (type.equals("number")) type = "int";
        javaCode.add(spacing +
                ((ctx.accModifier == null)?"":(ctx.accModifier.getText()) + " ") +
                ((ctx.isFinal == null)?"":"final ") +
                type + ((isArray)?"[] ":" ") +
                ctx.varName.getText() +
                ((ctx.value == null)?"":(" = $")) + ";");
    }

    @Override
    public void exitFieldDeclaration(MiniJavaParser.FieldDeclarationContext ctx) {
        /* Symbol Table */
        sum_type = new String();
        /* Java Code Generation */
    }

    @Override
    public void enterLocalDeclaration(MiniJavaParser.LocalDeclarationContext ctx) {
        /* Symbol Table */
        String dataType = "", dType = ctx.dataType.getText(), access = "(accessModifier: ACCESS_MODIFIER_PUBLIC) ";
        if (dType.contains("[]")) {
            dataType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            dataType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            dataType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            dataType += "[classType = " + kind + ", isDefined = ?]";
        }
        currentTable.table.put("var_" + ctx.varName.getText(),
                "LocalVar: (name: " + ctx.varName.getText() + ") " +
                        "(type: " + dataType + ") " + access);
        /* Java Code Generation */
        is_used.add(new String[]{ctx.varName.getText(), String.valueOf(javaCode.size())});
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        String type = ctx.dataType.getText();
        boolean isArray = type.contains("[]");
        if (isArray) type = type.substring(0, type.indexOf("["));
        if (type.equals("number")) type = "int";
        javaCode.add(spacing + type + ((isArray)?"[] ":" ") + ctx.varName.getText() + ";");
    }

    @Override
    public void exitLocalDeclaration(MiniJavaParser.LocalDeclarationContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterMethodDeclaration(MiniJavaParser.MethodDeclarationContext ctx) {
        /* Symbol Table */
        SymbolTable temp = new SymbolTable(currentTable, ctx.methodName.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        String returnType = "", dType = ctx.retType.getText(), access = "(accessModifier: ACCESS_MODIFIER_PUBLIC) ";
        if (dType.contains("[]")) {
            returnType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            returnType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            returnType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            returnType += "[classType = " + kind + ", isDefined = ?]";
        }
        if (ctx.accModifier != null)
            access = "(accessModifier: ACCESS_MODIFIER_" + ctx.accModifier.getText().toUpperCase() + ") ";
        currentTable.table.put("method_" + ctx.methodName.getText(),
                "Method: (name: " + ctx.methodName.getText() + ") " +
                        "(returnType: " + returnType + ") " + access);
        currentTable = temp;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        if (ctx.override != null) {
            javaCode.add(spacing + "@Override");
        }
        String type = ctx.retType.getText();
        boolean isArray = type.contains("[]");
        if (isArray) type = type.substring(0, type.indexOf("["));
        if (type.equals("number")) type = "int";
        javaCode.add(spacing + ((ctx.accModifier == null)?"public":ctx.accModifier.getText()) + " " +
                type + ((isArray)?"[] ":" ") + ctx.methodName.getText() + "() {");
        indent++;
    }

    @Override
    public void exitMethodDeclaration(MiniJavaParser.MethodDeclarationContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        indent--;
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "}");
    }

    @Override
    public void enterParameterList(MiniJavaParser.ParameterListContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitParameterList(MiniJavaParser.ParameterListContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterParameter(MiniJavaParser.ParameterContext ctx) {
        /* Symbol Table */
        String dataType = "", dType = ctx.dataType.getText(), access = "(accessModifier: ACCESS_MODIFIER_PUBLIC) ";
        if (dType.contains("[]")) {
            dataType = "array of ";
        }
        if (dType.length() > 1 &&(dType.equals("number") || dType.substring(0, dType.length() - 2).equals("number"))) {
            dataType += "int";
        } else if (dType.length() > 1 &&(dType.equals("boolean") || dType.substring(0, dType.length() - 2).equals("boolean"))) {
            dataType += "boolean";
        } else {
            String kind = dType.replace("[]", "");
            dataType += "[classType = " + kind + ", isDefined = ?]";
        }
        currentTable.table.put("var_" + ctx.varName.getText(),
                "Parameter: (name: " + ctx.varName.getText() + ") " +
                        "(type: " + dataType + ") " + "(index: " + (currentTable.table.size() + 1) + ")");
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int index = code.indexOf(")");
        String type = ctx.dataType.getText();
        boolean isArray = type.contains("[]");
        if (isArray) type = type.substring(0, type.indexOf("["));
        if (type.equals("number")) type = "int";
        javaCode.set(javaCode.size() - 1, code.substring(0, index) + ((index - code.lastIndexOf("(") <= 1) ? "" : ", ") +
                type + ((isArray)?"[] ":" ") + ctx.varName.getText() + code.substring(index));
    }

    @Override
    public void exitParameter(MiniJavaParser.ParameterContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterMethodBody(MiniJavaParser.MethodBodyContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        ret = "$";
    }

    @Override
    public void exitMethodBody(MiniJavaParser.MethodBodyContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        if (ctx.returnValue != null) {
            String spacing = "";
            for (int i = 0; i < indent; i++)
                spacing += "    ";
            javaCode.add(spacing + "return " + ret + ";");
        }
    }

    @Override
    public void enterType(MiniJavaParser.TypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitType(MiniJavaParser.TypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterBooleanType(MiniJavaParser.BooleanTypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitBooleanType(MiniJavaParser.BooleanTypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterReturnType(MiniJavaParser.ReturnTypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitReturnType(MiniJavaParser.ReturnTypeContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterAccessModifier(MiniJavaParser.AccessModifierContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitAccessModifier(MiniJavaParser.AccessModifierContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterNestedStatement(MiniJavaParser.NestedStatementContext ctx) {
        /* Symbol Table */
        SymbolTable temp = new SymbolTable(currentTable, "NestedStatement", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable = temp;
        /* Java Code Generation */
    }

    @Override
    public void exitNestedStatement(MiniJavaParser.NestedStatementContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
    }

    @Override
    public void enterIfElseStatement(MiniJavaParser.IfElseStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        try {
            Object result = engine.eval(ctx.condition.getText());
            if (result instanceof Boolean) {
                boolean live = ((Boolean) result).booleanValue();
                deadCode = !live || deadCode;;
            }
        } catch (ScriptException e) {}
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "if ($) {");
        indent++;
    }

    @Override
    public void exitIfElseStatement(MiniJavaParser.IfElseStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        deadCode = false;
        indent--;
    }

    @Override
    public void enterWhileStatement(MiniJavaParser.WhileStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        try {
            Object result = engine.eval(ctx.condition.getText());
            if (result instanceof Boolean) {
                boolean live = ((Boolean) result).booleanValue();
                deadCode = !live || deadCode;;
            }
        } catch (ScriptException e) {}
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "while ($) {");
        indent++;
    }

    @Override
    public void exitWhileStatement(MiniJavaParser.WhileStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        indent--;
        deadCode = false;
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "}");
    }

    @Override
    public void enterPrintStatement(MiniJavaParser.PrintStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "System.out.println($);");
    }

    @Override
    public void exitPrintStatement(MiniJavaParser.PrintStatementContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterVariableAssignmentStatement(MiniJavaParser.VariableAssignmentStatementContext ctx) {
        /* Symbol Table */
        sum_type = currentTable.table.get("var_" + ctx.target.getText());
        if (sum_type == null)
            sum_type = currentTable.parent.table.get("var_" + ctx.target.getText());
        if (sum_type != null && !sum_type.isEmpty()) {
            sum_type = sum_type.substring(sum_type.indexOf("type: ") + 6);
            if (sum_type.contains("array of "))
                sum_type = sum_type.substring(9);
            if (sum_type.contains("[classType = ")) {
                sum_type = sum_type.substring(sum_type.indexOf("[classType = ") + 13);
                sum_type = sum_type.substring(0, sum_type.indexOf(","));
            } else {
                sum_type = sum_type.substring(0, sum_type.indexOf(")"));
            }
        }
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "$ = $;");
    }

    @Override
    public void exitVariableAssignmentStatement(MiniJavaParser.VariableAssignmentStatementContext ctx) {
        /* Symbol Table */
        sum_type = new String();
        /* Java Code Generation */
    }

    @Override
    public void enterArrayAssignmentStatement(MiniJavaParser.ArrayAssignmentStatementContext ctx) {
        /* Symbol Table */
        sum_type = currentTable.table.get("var_" + ctx.target.getText());
        if (sum_type != null && !sum_type.isEmpty()) {
            sum_type = sum_type.substring(sum_type.indexOf("type: array of ") + 15);
            if (sum_type.contains("[classType = ")) {
                sum_type = sum_type.substring(sum_type.indexOf("[classType = ") + 13);
                sum_type = sum_type.substring(0, sum_type.indexOf(" ,") - 1);
            } else {
                sum_type = sum_type.substring(0, sum_type.indexOf(")"));
            }
        }
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + ctx.target.getText() + "[$] = $;");
    }

    @Override
    public void exitArrayAssignmentStatement(MiniJavaParser.ArrayAssignmentStatementContext ctx) {
        /* Symbol Table */
        sum_type = new String();
        /* Java Code Generation */
    }

    @Override
    public void enterLocalVarDeclaration(MiniJavaParser.LocalVarDeclarationContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void exitLocalVarDeclaration(MiniJavaParser.LocalVarDeclarationContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterExpressioncall(MiniJavaParser.ExpressioncallContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String spacing = "";
        for (int i = 0; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "$;");
    }

    @Override
    public void exitExpressioncall(MiniJavaParser.ExpressioncallContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterIfBlock(MiniJavaParser.IfBlockContext ctx) {
        /* Symbol Table */
        if (deadCode)
            javaCode.add("/*");
        SymbolTable temp = new SymbolTable(currentTable, "If", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable = temp;
        /* Java Code Generation */
    }

    @Override
    public void exitIfBlock(MiniJavaParser.IfBlockContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 1; i < indent; i++)
            spacing += "    ";
        if (deadCode)
            javaCode.add("*/");
        javaCode.add(spacing + "}");
    }

    @Override
    public void enterElseBlock(MiniJavaParser.ElseBlockContext ctx) {
        /* Symbol Table */
        SymbolTable temp = new SymbolTable(currentTable, "Else", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable = temp;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 1; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "else {");
    }

    @Override
    public void exitElseBlock(MiniJavaParser.ElseBlockContext ctx) {
        /* Symbol Table */
        currentTable = currentTable.parent;
        /* Java Code Generation */
        String spacing = "";
        for (int i = 1; i < indent; i++)
            spacing += "    ";
        javaCode.add(spacing + "}");
    }

    @Override
    public void enterWhileBlock(MiniJavaParser.WhileBlockContext ctx) {
        /* Symbol Table */
        if (deadCode)
            javaCode.add("/*");
        SymbolTable temp = new SymbolTable(currentTable, "While", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        currentTable.children.add(temp);
        currentTable = temp;
        /* Java Code Generation */
    }

    @Override
    public void exitWhileBlock(MiniJavaParser.WhileBlockContext ctx) {
        /* Symbol Table */
        if (deadCode)
            javaCode.add("*/");
        currentTable = currentTable.parent;
        /* Java Code Generation */
    }

    @Override
    public void enterLtExpression(MiniJavaParser.LtExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$ < $" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$ < $" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitLtExpression(MiniJavaParser.LtExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterObjectInstantiationExpression(MiniJavaParser.ObjectInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "new " +
                    ctx.getText().substring(3) + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "new " + ctx.getText().substring(3) + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitObjectInstantiationExpression(MiniJavaParser.ObjectInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterArrayInstantiationExpression(MiniJavaParser.ArrayInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "new " +
                    ctx.getText().substring(3, ctx.getText().indexOf("[")) + "[$]" +
                    code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "new " + ctx.getText().substring(3, ctx.getText().indexOf("[")) +
                    "[$]" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitArrayInstantiationExpression(MiniJavaParser.ArrayInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterPowExpression(MiniJavaParser.PowExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        boolean folded = false;
        try {
            Object result = engine.eval(ctx.getText());
            if (result instanceof Number) {
                int x = ((Number) result).intValue();
                if (idx != -1) {
                    javaCode.set(javaCode.size() - 1, code.substring(0, idx) + x + code.substring(idx + 1));
                } else if (ret.contains(("$"))) {
                    idx = ret.indexOf("$");
                    ret = ret.substring(0, idx) + x + ret.substring(idx + 1);
                }
                folded = true;
            }
        } catch (ScriptException e) {}
        if (!folded) {
            if (idx != -1) {
                javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "Math.pow($, $)" + code.substring(idx + 1));
            } else if (ret.contains(("$"))) {
                idx = ret.indexOf("$");
                ret = ret.substring(0, idx) + "Math.pow($, $)" + ret.substring(idx + 1);
            }
        }
    }

    @Override
    public void exitPowExpression(MiniJavaParser.PowExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */

    }

    @Override
    public void enterIdentifierExpression(MiniJavaParser.IdentifierExpressionContext ctx) {
        /* Symbol Table */
        String type = currentTable.table.get("var_" + ctx.getText());
        if (type == null)
            type = currentTable.parent.table.get("var_" + ctx.getText());
        if (type != null) {
            type = type.substring(type.indexOf("type: ") + 6);
            if (type.contains("array of "))
                type = type.substring(9);
            if (type.contains("[classType = ")) {
                type = type.substring(type.indexOf("[classType = ") + 13);
                type = type.substring(0, type.indexOf(","));
            } else {
                type = type.substring(0, type.indexOf(")"));
            }
        }
        if (type != null) {
            if (sum_type != null && !sum_type.isEmpty() && !sum_type.equals(type)) {
                System.out.println("\033[31m" + "Error 230 : in line [" + ctx.start.getLine() + ": " + ctx.start.getCharPositionInLine() +
                        "], Incompatible types : [" + type + "] can not be " +
                        "converted to [" + sum_type + "]" + "\033[31m");
            }
        }
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + ctx.getText() + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + ctx.getText() + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitIdentifierExpression(MiniJavaParser.IdentifierExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterMethodCallExpression(MiniJavaParser.MethodCallExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        int parameterCount = 0;
        String parameters = "";
        if (ctx.getText().indexOf(")") - ctx.getText().indexOf("(") > 1) {
            String[] parts = ctx.getText().split(",");
            parameterCount = parts.length;
            parameters = "$";
        }
        for (int i = 1; i < parameterCount; i++)
            parameters += ", $";
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$." +
                    ctx.getText().substring(ctx.getText().indexOf(".") + 1, ctx.getText().indexOf("(") + 1) +
                    parameters + ")" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$." +
                    ctx.getText().substring(ctx.getText().indexOf(".") + 1, ctx.getText().indexOf("(") + 1) +
                    parameters + ")" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitMethodCallExpression(MiniJavaParser.MethodCallExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterNotExpression(MiniJavaParser.NotExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "!" +
                    ctx.getText().substring(2) + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "!" + ctx.getText().substring(2) + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitNotExpression(MiniJavaParser.NotExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterBooleanLitExpression(MiniJavaParser.BooleanLitExpressionContext ctx) {
        /* Symbol Table */
        if (sum_type != null && !sum_type.isEmpty() && !sum_type.equals("boolean") && !sum_type.equals("String")) {
            System.out.println("\033[31m" + "Error 230 : in line [" + ctx.start.getLine() + ": " + ctx.start.getCharPositionInLine() +
                    "], Incompatible types : [boolean] can not be " +
                    "converted to [" + sum_type + "]" + "\033[0m");
        }
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + ctx.getText() + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + ctx.getText() + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitBooleanLitExpression(MiniJavaParser.BooleanLitExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterParenExpression(MiniJavaParser.ParenExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "($)" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "($)" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitParenExpression(MiniJavaParser.ParenExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterIntLitExpression(MiniJavaParser.IntLitExpressionContext ctx) {
        /* Symbol Table */
        if (sum_type != null && !sum_type.isEmpty() && !sum_type.equals("int") && !sum_type.equals("String")) {
            System.out.println("\033[31m" + "Error 230 : in line [" + ctx.start.getLine() + ": " + ctx.start.getCharPositionInLine() +
                    "], Incompatible types : [int] can not be " +
                    "converted to [" + sum_type + "]" + "\033[0m");
        }
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + ctx.getText() + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + ctx.getText() + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitIntLitExpression(MiniJavaParser.IntLitExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterStringLitExpression(MiniJavaParser.StringLitExpressionContext ctx) {
        /* Symbol Table */
        if (sum_type != null && !sum_type.isEmpty() && !sum_type.equals("String")) {
            System.out.println("\033[31m" + "Error 230 : in line [" + ctx.start.getLine() + ": " + ctx.start.getCharPositionInLine() +
                    "], Incompatible types : [String] can not be " +
                    "converted to [" + sum_type + "]"+ "\033[31m");
        }
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + ctx.getText() + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + ctx.getText() + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitStringLitExpression(MiniJavaParser.StringLitExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterNullLitExpression(MiniJavaParser.NullLitExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "null" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "null" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitNullLitExpression(MiniJavaParser.NullLitExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterAndExpression(MiniJavaParser.AndExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$ && $" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$ && $" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitAndExpression(MiniJavaParser.AndExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterArrayAccessExpression(MiniJavaParser.ArrayAccessExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$[$]" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$[$]" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitArrayAccessExpression(MiniJavaParser.ArrayAccessExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterAddExpression(MiniJavaParser.AddExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        boolean folded = false;
        try {
            Object result = engine.eval(ctx.getText());
            if (result instanceof Number) {
                int x = ((Number) result).intValue();
                if (idx != -1) {
                    javaCode.set(javaCode.size() - 1, code.substring(0, idx) + x + code.substring(idx + 1));
                } else if (ret.contains(("$"))) {
                    idx = ret.indexOf("$");
                    ret = ret.substring(0, idx) + x + ret.substring(idx + 1);
                }
                folded = true;
            }
        } catch (ScriptException e) {}
        if (!folded) {
            if (idx != -1) {
                javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$ + $" + code.substring(idx + 1));
            } else if (ret.contains(("$"))) {
                idx = ret.indexOf("$");
                ret = ret.substring(0, idx) + "$ + $" + ret.substring(idx + 1);
            }
        }
    }

    @Override
    public void exitAddExpression(MiniJavaParser.AddExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterThisExpression(MiniJavaParser.ThisExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "this" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "this" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitThisExpression(MiniJavaParser.ThisExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterFieldCallExpression(MiniJavaParser.FieldCallExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$." +
                    ctx.getText().substring(ctx.getText().indexOf(".") + 1) + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$." +
                    ctx.getText().substring(ctx.getText().indexOf(".") + 1) + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitFieldCallExpression(MiniJavaParser.FieldCallExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterArrayLengthExpression(MiniJavaParser.ArrayLengthExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$.length" + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + "$.length" + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitArrayLengthExpression(MiniJavaParser.ArrayLengthExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterIntarrayInstantiationExpression(MiniJavaParser.IntarrayInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        if (idx != -1) {
            javaCode.set(javaCode.size() - 1, code.substring(0, idx) +
                    ctx.getText().replace(",", ", ") + code.substring(idx + 1));
        } else if (ret.contains(("$"))) {
            idx = ret.indexOf("$");
            ret = ret.substring(0, idx) + ctx.getText().replace(",", ", ") + ret.substring(idx + 1);
        }
    }

    @Override
    public void exitIntarrayInstantiationExpression(MiniJavaParser.IntarrayInstantiationExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterSubExpression(MiniJavaParser.SubExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        boolean folded = false;
        try {
            Object result = engine.eval(ctx.getText());
            if (result instanceof Number) {
                int x = ((Number) result).intValue();
                if (idx != -1) {
                    javaCode.set(javaCode.size() - 1, code.substring(0, idx) + x + code.substring(idx + 1));
                } else if (ret.contains(("$"))) {
                    idx = ret.indexOf("$");
                    ret = ret.substring(0, idx) + x + ret.substring(idx + 1);
                }
                folded = true;
            }
        } catch (ScriptException e) {}
        if (!folded) {
            if (idx != -1) {
                javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$ - $" + code.substring(idx + 1));
            } else if (ret.contains(("$"))) {
                idx = ret.indexOf("$");
                ret = ret.substring(0, idx) + "$ - $" + ret.substring(idx + 1);
            }
        }
    }

    @Override
    public void exitSubExpression(MiniJavaParser.SubExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void enterMulExpression(MiniJavaParser.MulExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
        String code = javaCode.get(javaCode.size() - 1);
        int idx = code.indexOf("$");
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        boolean folded = false;
        try {
            Object result = engine.eval(ctx.getText());
            if (result instanceof Number) {
                int x = ((Number) result).intValue();
                if (idx != -1) {
                    javaCode.set(javaCode.size() - 1, code.substring(0, idx) + x + code.substring(idx + 1));
                } else if (ret.contains(("$"))) {
                    idx = ret.indexOf("$");
                    ret = ret.substring(0, idx) + x + ret.substring(idx + 1);
                }
                folded = true;
            }
        } catch (ScriptException e) {}
        if (!folded) {
            if (idx != -1) {
                javaCode.set(javaCode.size() - 1, code.substring(0, idx) + "$ * $" + code.substring(idx + 1));
            } else if (ret.contains(("$"))) {
                idx = ret.indexOf("$");
                ret = ret.substring(0, idx) + "$ * $" + ret.substring(idx + 1);
            }
        }
    }

    @Override
    public void exitMulExpression(MiniJavaParser.MulExpressionContext ctx) {
        /* Symbol Table */
        /* Java Code Generation */
    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {
    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {
    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {
    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {
    }
}
