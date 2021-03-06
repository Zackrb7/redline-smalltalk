/* Redline Smalltalk, Copyright (c) James C. Ladd. All rights reserved. See LICENSE in the root of this distribution. */
package st.redline.compiler;

import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import org.objectweb.asm.*;
import st.redline.classloader.*;

import java.math.*;
import java.util.*;

public class SmalltalkGeneratingVisitor extends SmalltalkBaseVisitor<Void> implements SmalltalkVisitor<Void>, Opcodes {

    public static final String DEFAULT_IMPORTED_PACKAGE = "st.redline.kernel";

    private static final String[] SIGNATURES = {
            "(Ljava/lang/String;)Lst/redline/core/PrimObject;",
            "(Lst/redline/core/PrimObject;Ljava/lang/String;)Lst/redline/core/PrimObject;",
            "(Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Ljava/lang/String;)Lst/redline/core/PrimObject;",
            "(Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Ljava/lang/String;)Lst/redline/core/PrimObject;",
            "(Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Ljava/lang/String;)Lst/redline/core/PrimObject;",
            "(Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Ljava/lang/String;)Lst/redline/core/PrimObject;"
    };
    private static final Map<String, Integer> OPCODES = new HashMap<String, Integer>();
    private static final int BYTECODE_VERSION;
    static {
        int compareTo18 = new BigDecimal(System.getProperty("java.specification.version")).compareTo(new BigDecimal("1.8"));
        if (compareTo18 >= 0) {
            BYTECODE_VERSION = V1_8;
        } else {
            throw new RuntimeException("Java 1.8 or above required.");
        }
    }

    private final Stack<SmalltalkVisitor<Void>> visitors = new Stack<SmalltalkVisitor<Void>>();
    private final Source source;
    private byte[] classBytes = null;

    public SmalltalkGeneratingVisitor(Source source) {
        this.source = source;
        makeClassGeneratorCurrentVisitor();
    }

    private void makeClassGeneratorCurrentVisitor() {
        pushCurrentVisitor(new ClassGeneratorVisitor());
    }

    public Void visitScript(@NotNull SmalltalkParser.ScriptContext ctx) {
        currentVisitor().visitScript(ctx);
        return null;
    }

    private void pushCurrentVisitor(SmalltalkVisitor<Void> visitor) {
        visitors.push(visitor);
    }

    private SmalltalkVisitor<Void> currentVisitor() {
        return visitors.peek();
    }

    private SmalltalkVisitor<Void> popCurrentVisitor() {
        return visitors.pop();
    }

    private void log(String output) {
        System.out.println(output);
        System.out.flush();
    }

    private String className() {
        return source.className();
    }

    private String sourceFileExtension() {
        return source.fileExtension();
    }

    private String fullClassName() {
        return source.fullClassName();
    }

    private String packageName() {
        return source.packageName();
    }

    private String superclassName() {
        return "st/redline/core/PrimObject";
    }

    private String contextName() {
        return "st/redline/core/PrimContext";
    }

    public byte[] generatedClassBytes() {
        return classBytes;
    }

    private int opcodeValue(String opcode) {
        if (!OPCODES.containsKey(opcode))
            throw new IllegalStateException("Unknown OPCODE '" + opcode + "'.");
        return OPCODES.get(opcode);
    }

    public void pop(MethodVisitor mv) {
        mv.visitInsn(POP);
    }

    public void pushBoolean(MethodVisitor mv, boolean value) {
        mv.visitInsn(value ? ICONST_1 : ICONST_0);
    }

    public void pushLiteral(MethodVisitor mv, String literal) {
        mv.visitLdcInsn(literal);
    }

    public void pushDuplicate(MethodVisitor mv) {
        mv.visitInsn(DUP);
    }

    public void pushThis(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 0);
    }

    public void pushReceiver(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 1);
    }

    public void pushSuper(MethodVisitor mv, int line) {
        visitLine(mv, line);
        pushReceiver(mv);
    }

    public void pushContext(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 2);
    }

    public void pushNull(MethodVisitor mv) {
        mv.visitInsn(ACONST_NULL);
    }

    public void pushTemporary(MethodVisitor mv, int index) {
        pushContext(mv);
        pushNumber(mv, index);
        mv.visitMethodInsn(INVOKEVIRTUAL, contextName(), "temporaryAt", "(I)Lst/redline/core/PrimObject;", false);
    }

    public void storeTemporary(MethodVisitor mv, int index) {
        pushNumber(mv, index);
        pushContext(mv);
        mv.visitMethodInsn(INVOKESTATIC, contextName(), "temporaryPutAt", "(Lst/redline/core/PrimObject;IL" + contextName() + ";)V", false);
    }

    public void pushArgument(MethodVisitor mv, int index) {
        pushContext(mv);
        pushNumber(mv, index);
        mv.visitMethodInsn(INVOKEVIRTUAL, contextName(), "argumentAt", "(I)Lst/redline/core/PrimObject;", false);
    }

    public void pushReference(MethodVisitor mv, String name) {
        pushReceiver(mv);
        pushLiteral(mv, name);
        mv.visitMethodInsn(INVOKEVIRTUAL, superclassName(), "reference", "(Ljava/lang/String;)Lst/redline/core/PrimObject;", false);
    }

    public void pushNil(MethodVisitor mv) {
        pushReceiver(mv);
        mv.visitMethodInsn(INVOKEVIRTUAL, superclassName(), "referenceNil", "()Lst/redline/core/PrimObject;", false);
    }

    public void pushTrue(MethodVisitor mv) {
        pushReceiver(mv);
        mv.visitMethodInsn(INVOKEVIRTUAL, superclassName(), "referenceTrue", "()Lst/redline/core/PrimObject;", false);
    }

    public void pushFalse(MethodVisitor mv) {
        pushReceiver(mv);
        mv.visitMethodInsn(INVOKEVIRTUAL, superclassName(), "referenceFalse", "()Lst/redline/core/PrimObject;", false);
    }

    public void invokePerform(MethodVisitor mv, String selector, int argumentCount, boolean sendToSuper) {
        pushLiteral(mv, selector);
        String methodName = (sendToSuper) ? "superPerform" : "perform";
        mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", methodName, SIGNATURES[argumentCount], false);
    }

    public void visitLine(MethodVisitor mv, int line) {
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(line, l0);
    }

    public static void pushNumber(MethodVisitor mv, int value) {
        switch (value) {
            case 0: mv.visitInsn(ICONST_0); break;
            case 1: mv.visitInsn(ICONST_1); break;
            case 2: mv.visitInsn(ICONST_2); break;
            case 3: mv.visitInsn(ICONST_3); break;
            case 4: mv.visitInsn(ICONST_4); break;
            case 5: mv.visitInsn(ICONST_5); break;
            default:
                if (value > 5 && value < 128)
                    mv.visitIntInsn(BIPUSH, value);
                else // SIPUSH not supported yet.
                    throw new IllegalStateException("push of integer value " + value + " not yet supported.");
        }
    }

    public void pushNewObject(MethodVisitor mv, String type, String value, int line) {
        visitLine(mv, line);
        pushReceiver(mv);
        pushLiteral(mv, value);
        mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", type, "(Ljava/lang/Object;)Lst/redline/core/PrimObject;", false);
    }

    private void pushNewBlock(MethodVisitor mv, String className, String name, String sig, int line, boolean answerBlock, String answerBlockClassName) {
        pushNewLambda(mv, className, name, sig, line);
        if (!answerBlock) {
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "smalltalkBlock", "(Ljava/lang/Object;)Lst/redline/core/PrimObject;", false);
        } else {
            pushLiteral(mv, answerBlockClassName);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "smalltalkBlockAnswer", "(Ljava/lang/Object;Ljava/lang/String;)Lst/redline/core/PrimObject;", false);
        }
    }

    private void pushNewMethod(MethodVisitor mv, String className, String name, String sig, int line) {
        pushNewLambda(mv, className,name, sig, line);
        mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "smalltalkMethod", "(Ljava/lang/Object;)Lst/redline/core/PrimObject;", false);
    }

    private void pushNewLambda(MethodVisitor mv, String className, String name, String sig, int line) {
        visitLine(mv, line);
        pushReceiver(mv);
        mv.visitInvokeDynamicInsn("apply", "()Lst/redline/core/LambdaBlock;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"), new Object[]{Type.getType(sig), new Handle(Opcodes.H_INVOKESTATIC, className, name, sig), Type.getType(sig)});
    }

    // ------------------------------

    private class ClassGeneratorVisitor extends SmalltalkBaseVisitor<Void> implements SmalltalkVisitor<Void>, Opcodes {

        protected final String LAMBDA_BLOCK_SIG = "(Lst/redline/core/PrimObject;Lst/redline/core/PrimObject;Lst/redline/core/PrimContext;)Lst/redline/core/PrimObject;";
        protected MethodVisitor mv;
        private final String SEND_MESSAGES_SIG = "(Lst/redline/core/PrimObject;Lst/redline/core/PrimContext;)Lst/redline/core/PrimObject;";
        private final ClassWriter cw;
        private HashMap<String, ExtendedTerminalNode> temporaries;
        private HashMap<String, ExtendedTerminalNode> arguments;
        private Stack<KeywordRecord> keywords = new Stack<>();
        protected int blockNumber = 0;
        private boolean referencedJVM = false;
        private boolean sendToSuper = false;
        private List<BlockAnswerRecord> tryCatchRecords;
        private Label tryStartLabel;
        private Label tryEndLabel;

        public ClassGeneratorVisitor() {
            this(new ClassWriter(ClassWriter.COMPUTE_FRAMES));
        }

        public ClassGeneratorVisitor(ClassWriter classWriter) {
            this.cw = classWriter;
        }

        public ClassGeneratorVisitor(ClassWriter classWriter, MethodVisitor methodVisitor) {
            cw = classWriter;
            mv = methodVisitor;
        }

        public Void visitScript(SmalltalkParser.ScriptContext ctx) {
            openJavaClass();
            createPackageNameMethod();
            createImportForMethod();
            openSendMessagesMethod();
            ctx.sequence().accept(currentVisitor());
            closeSendMessagesMethod();
            closeJavaClass();
            return null;
        }

        private void closeSendMessagesMethod() {
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void createPackageNameMethod() {
            mv = cw.visitMethod(ACC_PROTECTED, "packageName", "()Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitLdcInsn(packageName());
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        private void createImportForMethod() {
            if (DEFAULT_IMPORTED_PACKAGE.equals(packageName()))
                return;
            mv = cw.visitMethod(ACC_PROTECTED, "importFor", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "classLoader", "()Lst/redline/classloader/SmalltalkClassLoader;", false);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "packageName", "()Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/classloader/SmalltalkClassLoader", "importForBy", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }

        private void openSendMessagesMethod() {
            mv = cw.visitMethod(ACC_PROTECTED, "sendMessages", SEND_MESSAGES_SIG, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 1);
        }

        private void openJavaClass() {
            System.out.println("\nopenJavaClass: " + fullClassName());
            cw.visit(BYTECODE_VERSION, ACC_PUBLIC + ACC_SUPER, fullClassName(), null, superclassName(), null);
            cw.visitSource(className() + sourceFileExtension(), null);
            cw.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
            makeJavaClassInitializer();
        }

        private void closeJavaClass() {
            System.out.println("closeJavaClass: " + fullClassName());
            cw.visitEnd();
            classBytes = cw.toByteArray();
        }

        private void makeJavaClassInitializer() {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(0, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, superclassName(), "<init>", "()V", false);

            // import current package
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "packageName", "()Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", "importAll", "(Ljava/lang/String;)V", false);

            // create a Context
            mv.visitTypeInsn(NEW, contextName());
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, contextName(), "<init>", "(Lst/redline/core/PrimObject;)V", false);
            mv.visitVarInsn(ASTORE, 1);

            // set the class of this as self.
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 0); // receiver
            mv.visitMethodInsn(INVOKEVIRTUAL, fullClassName(), "selfClass", "(Lst/redline/core/PrimObject;)V", false);

            // call sendMessages with parameters: this & context
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 0); // receiver
            mv.visitVarInsn(ALOAD, 1); // context
            mv.visitMethodInsn(INVOKEVIRTUAL, fullClassName(), "sendMessages", SEND_MESSAGES_SIG, false);
            mv.visitInsn(POP);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        public Void visitSequence(SmalltalkParser.SequenceContext ctx) {
            log("visitSequence");
            SmalltalkParser.TempsContext temps = ctx.temps();
            if (temps != null)
                temps.accept(currentVisitor());
            SmalltalkParser.StatementsContext statements = ctx.statements();
            if (statements != null)
                statements.accept(currentVisitor());
            return null;
        }

        public Void visitTemps(@NotNull SmalltalkParser.TempsContext ctx) {
            initializeTemporaryVariableMap();
            addToTemporaryVariableMap(ctx.IDENTIFIER());
            addTemporariesToContext();
            return null;
        }

        private void addTemporariesToContext() {
            visitLine(mv, lineNumberOfFirstTemporary());
            pushContext(mv);
            pushNumber(mv, temporaries.size());
            mv.visitMethodInsn(INVOKEVIRTUAL, contextName(), "initTemporaries", "(I)V", false);
        }

        private int lineNumberOfFirstTemporary() {
            return temporaries.entrySet().iterator().next().getValue().getLine();
        }

        private boolean isTemporary(String key) {
            return temporaries != null && temporaries.containsKey(key);
        }

        private int indexOfTemporary(String key) {
            return temporaries.get(key).getIndex();
        }

        private void addToTemporaryVariableMap(List<TerminalNode> nodes) {
            for (TerminalNode node : nodes)
                addToTemporaryVariableMap(node);
        }

        private void addToTemporaryVariableMap(TerminalNode node) {
            addToTemporaryVariableMap(node.getText(), temporaries.size(), node);
        }

        private void addToTemporaryVariableMap(String key, int index, TerminalNode node) {
            if (temporaries.containsKey(key))
                throw new RuntimeException("Temporary '" + key + "' already defined.");
            temporaries.put(key, new ExtendedTerminalNode(node, index));
        }

        protected int countOf(String input, char ch) {
            int count = 0;
            for (int i = 0, l = input.length(); i < l; i++)
                if (input.charAt(i) == ch)
                    count++;
            return count;
        }

        private boolean haveKeyword() {
            return !keywords.isEmpty();
        }

        protected void initializeKeyword() {
            keywords.push(new KeywordRecord());
        }

        protected void addToKeyword(String keyword) {
            keywords.peek().keyword.append(keyword);
        }

        private void addArgumentToKeyword(String firstArgument) {
            assert keywords.peek().firstArgument.length() == 0;
            keywords.peek().firstArgument.append(firstArgument);
        }

        protected String removeKeyword() {
            return keywords.pop().keyword.toString();
        }

        private KeywordRecord peekKeyword() {
            return keywords.peek();
        }

        private void initializeTemporaryVariableMap() {
            temporaries = new HashMap<String, ExtendedTerminalNode>();
        }

        private boolean isArgument(String key) {
            return arguments != null && arguments.containsKey(key);
        }

        private int indexOfArgument(String key) {
            return arguments.get(key).getIndex();
        }

        protected void initializeArgumentsMap() {
            arguments = new HashMap<>();
        }

        protected void addArgumentToMap(ExtendedTerminalNode node) {
            log("addArgumentToMap " + node.getText());
            assert !arguments.containsKey(node.getText());
            String key = node.getText();
            if (key.startsWith(":"))  // <- block arguments are prefixed with ':'
                key = key.substring(1);
            arguments.put(key, node);
        }

        public Void visitStatementExpressions(@NotNull SmalltalkParser.StatementExpressionsContext ctx) {
            log("visitStatementExpressions");
            ctx.expressions().accept(currentVisitor());
            return null;
        }

        public Void visitStatementExpressionsAnswer(@NotNull SmalltalkParser.StatementExpressionsAnswerContext ctx) {
            log("visitStatementExpressionsAnswer");
            ctx.expressions().accept(currentVisitor());
            ctx.answer().accept(currentVisitor());
            return null;
        }

        public Void visitStatementAnswer(@NotNull SmalltalkParser.StatementAnswerContext ctx) {
            log("visitStatementAnswer");
            SmalltalkParser.AnswerContext answer = ctx.answer();
            visitLine(mv, answer.CARROT().getSymbol().getLine());
            SmalltalkParser.ExpressionContext expression = answer.expression();
            expression.accept(currentVisitor());
            mv.visitInsn(ARETURN);
            return null;
        }

        public Void visitAnswer(@NotNull SmalltalkParser.AnswerContext ctx) {
            log("visitAnswer");
            TerminalNode carrot = ctx.CARROT();
            visitLine(mv, carrot.getSymbol().getLine());
            SmalltalkParser.ExpressionContext expression = ctx.expression();
            expression.accept(currentVisitor());
            mv.visitInsn(ARETURN);
            return null;
        }

        public Void visitExpression(@NotNull SmalltalkParser.ExpressionContext ctx) {
            log("visitExpression");
            referencedJVM = false;
            removeJVMGeneratorVisitor();
            SmalltalkParser.BinarySendContext binarySend = ctx.binarySend();
            if (binarySend != null)
                return binarySend.accept(currentVisitor());
            SmalltalkParser.KeywordSendContext keywordSend = ctx.keywordSend();
            if (keywordSend != null)
                return keywordSend.accept(currentVisitor());
            SmalltalkParser.CascadeContext cascade = ctx.cascade();
            if (cascade != null)
                return cascade.accept(currentVisitor());
            SmalltalkParser.AssignmentContext assignment = ctx.assignment();
            if (assignment != null)
                return assignment.accept(currentVisitor());
            SmalltalkParser.PrimitiveContext primitiveContext = ctx.primitive();
            if (primitiveContext != null)
                return primitiveContext.accept(currentVisitor());
            throw new RuntimeException("visitExpression no alternative found.");
        }

        private void removeJVMGeneratorVisitor() {
            if (currentVisitor() instanceof JVMGeneratorVisitor)
                popCurrentVisitor();
        }

        public Void visitPrimitive(@NotNull SmalltalkParser.PrimitiveContext ctx) {
            log("visitPrimitive");
            throw new RuntimeException("Smalltalk <primitive> should be replaced with JVM primitive: id.");
        }

        public Void visitUnarySend(@NotNull SmalltalkParser.UnarySendContext ctx) {
            log("visitUnarySend");
            ctx.operand().accept(currentVisitor());
            SmalltalkParser.UnaryTailContext unaryTail = ctx.unaryTail();
            if (unaryTail != null)
                return unaryTail.accept(currentVisitor());
            return null;
        }

        public Void visitUnaryTail(@NotNull SmalltalkParser.UnaryTailContext ctx) {
            log("visitUnaryTail");
            ctx.unaryMessage().accept(currentVisitor());
            SmalltalkParser.UnaryTailContext unaryTail = ctx.unaryTail();
            if (unaryTail != null)
                return unaryTail.accept(currentVisitor());
            return null;
        }

        public Void visitUnarySelector(@NotNull SmalltalkParser.UnarySelectorContext ctx) {
            log("visitUnarySelector " + ctx.IDENTIFIER().getSymbol().getText());
            TerminalNode selectorNode = ctx.IDENTIFIER();
            visitLine(mv, selectorNode.getSymbol().getLine());
            invokePerform(mv, selectorNode.getSymbol().getText(), 0, sendToSuper);
            sendToSuper = false;
            return null;
        }

        public Void visitBinarySend(@NotNull SmalltalkParser.BinarySendContext ctx) {
            log("visitBinarySend");
            ctx.unarySend().accept(currentVisitor());
            SmalltalkParser.BinaryTailContext binaryTail = ctx.binaryTail();
            if (binaryTail != null)
                return binaryTail.accept(currentVisitor());
            return null;
        }

        public Void visitBinaryTail(@NotNull SmalltalkParser.BinaryTailContext ctx) {
            log("visitBinaryTail");
            ctx.binaryMessage().accept(currentVisitor());
            SmalltalkParser.BinaryTailContext binaryTail = ctx.binaryTail();
            if (binaryTail != null)
                return binaryTail.accept(currentVisitor());
            return null;
        }

        public Void visitKeywordSend(@NotNull SmalltalkParser.KeywordSendContext ctx) {
            log("visitKeywordSend");
            ctx.binarySend().accept(currentVisitor());
            if (referencedJVM)
                pushCurrentVisitor(new JVMGeneratorVisitor(cw, mv));
            ctx.keywordMessage().accept(currentVisitor());
            return null;
        }

        public Void visitCascade(@NotNull SmalltalkParser.CascadeContext ctx) {
            log("visitCascade");
            SmalltalkParser.BinarySendContext binarySend = ctx.binarySend();
            if (binarySend != null)
                binarySend.accept(currentVisitor());
            SmalltalkParser.KeywordSendContext keywordSend = ctx.keywordSend();
            if (keywordSend != null)
                keywordSend.accept(currentVisitor());
            for (SmalltalkParser.MessageContext message : ctx.message())
                message.accept(currentVisitor());
            return null;
        }

        public Void visitAssignment(@NotNull SmalltalkParser.AssignmentContext ctx) {
            log("visitAssignment");
            SmalltalkParser.ExpressionContext expression = ctx.expression();
            if (expression == null)
                throw new RuntimeException("visitAssignment expression expected.");
            expression.accept(currentVisitor());
            SmalltalkParser.VariableContext variable = ctx.variable();
            if (variable == null)
                throw new RuntimeException("visitAssignment variable expected.");
            TerminalNode identifierNode = variable.IDENTIFIER();
            String identifier = identifierNode.getSymbol().getText();
            visitLine(mv, identifierNode.getSymbol().getLine());
            if (!isTemporary(identifier))
                throw new RuntimeException("visitAssignment temporary expected.");
            pushDuplicate(mv);
            storeTemporary(mv, indexOfTemporary(identifier));
            return null;
        }

        public Void visitMessage(@NotNull SmalltalkParser.MessageContext ctx) {
            log("visitMessage");
            SmalltalkParser.UnaryMessageContext unaryMessage = ctx.unaryMessage();
            if (unaryMessage != null)
                return unaryMessage.accept(currentVisitor());
            SmalltalkParser.KeywordMessageContext keywordMessage = ctx.keywordMessage();
            if (keywordMessage != null)
                return keywordMessage.accept(currentVisitor());
            SmalltalkParser.BinaryMessageContext binaryMessage = ctx.binaryMessage();
            if (binaryMessage != null)
                return binaryMessage.accept(currentVisitor());
            throw new RuntimeException("visitMessage no alternative found.");
        }

        public Void visitUnaryMessage(@NotNull SmalltalkParser.UnaryMessageContext ctx) {
            log("visitUnaryMessage");
            SmalltalkParser.UnarySelectorContext unarySelector = ctx.unarySelector();
            if (unarySelector != null)
                unarySelector.accept(currentVisitor());
            return null;
        }

        public Void visitBinaryMessage(@NotNull SmalltalkParser.BinaryMessageContext ctx) {
            log("visitBinaryMessage " + ctx.BINARY_SELECTOR().getSymbol().getText());
            TerminalNode binarySelector = ctx.BINARY_SELECTOR();
            SmalltalkParser.UnarySendContext unarySend = ctx.unarySend();
            if (unarySend != null)
                unarySend.accept(currentVisitor());
            SmalltalkParser.OperandContext operand = ctx.operand();
            if (operand != null)
                operand.accept(currentVisitor());
            visitLine(mv, binarySelector.getSymbol().getLine());
            invokePerform(mv, binarySelector.getSymbol().getText(), 1, sendToSuper);
            sendToSuper = false;
            return null;
        }

        public Void visitKeywordMessage(@NotNull SmalltalkParser.KeywordMessageContext ctx) {
            log("visitKeywordMessage");
            initializeKeyword();
            initializeTryCatch();
            for (SmalltalkParser.KeywordPairContext keywordPair : ctx.keywordPair())
                keywordPair.accept(currentVisitor());
            visitLine(mv, ctx.keywordPair().get(0).KEYWORD().getSymbol().getLine());
            String keyword = removeKeyword();
            setupTryBlock();
            invokePerform(mv, keyword, countOf(keyword, ':'), sendToSuper);
            setupCatchBlock();
            sendToSuper = false;
            return null;
        }

        private void initializeTryCatch() {
            tryCatchRecords = new ArrayList<>();
            tryStartLabel = new Label();
            tryEndLabel = new Label();
        }

        private void setupTryBlock() {
            if (tryCatchRecords.isEmpty())
                return;
            log("setupTryBlock");
            for (BlockAnswerRecord record : tryCatchRecords)
                mv.visitTryCatchBlock(tryStartLabel, tryEndLabel, record.handlerLabel, record.exceptionName);
            mv.visitLabel(tryStartLabel);
        }

        private void setupCatchBlock() {
            if (tryCatchRecords.isEmpty())
                return;
            log("setupCatchBlock");
            for (BlockAnswerRecord record : tryCatchRecords) {
                mv.visitJumpInsn(GOTO, tryEndLabel);
                mv.visitLabel(record.handlerLabel);
                mv.visitMethodInsn(INVOKEVIRTUAL, record.exceptionName, "answer", "()Lst/redline/core/PrimObject;", false);
                mv.visitInsn(ARETURN);
            }
            mv.visitLabel(tryEndLabel);
        }

        public Void visitKeywordPair(@NotNull SmalltalkParser.KeywordPairContext ctx) {
            log("visitKeywordPair " + ctx.KEYWORD().getSymbol().getText());
            TerminalNode keyword = ctx.KEYWORD();
            String part = keyword.getSymbol().getText();
            visitLine(mv, keyword.getSymbol().getLine());
            addToKeyword(part);
            SmalltalkParser.BinarySendContext binarySend = ctx.binarySend();
            if (binarySend != null)
                return binarySend.accept(currentVisitor());
            throw new RuntimeException("visitKeywordPair binary send expected.");
        }

        public Void visitOperand(@NotNull SmalltalkParser.OperandContext ctx) {
            log("visitOperand");
            SmalltalkParser.LiteralContext literal = ctx.literal();
            if (literal != null)
                return literal.accept(currentVisitor());
            SmalltalkParser.ReferenceContext reference = ctx.reference();
            if (reference != null)
                return reference.accept(currentVisitor());
            SmalltalkParser.SubexpressionContext subexpression = ctx.subexpression();
            if (subexpression != null)
                return subexpression.accept(currentVisitor());
            throw new RuntimeException("visitOperand no alternative found.");
        }

        public Void visitLiteral(@NotNull SmalltalkParser.LiteralContext ctx) {
            log("visitLiteral");
            SmalltalkParser.ParsetimeLiteralContext parsetimeLiteral = ctx.parsetimeLiteral();
            if (parsetimeLiteral != null)
                return parsetimeLiteral.accept(currentVisitor());
            SmalltalkParser.RuntimeLiteralContext runtimeLiteral = ctx.runtimeLiteral();
            if (runtimeLiteral != null)
                return runtimeLiteral.accept(currentVisitor());
            throw new RuntimeException("visitLiteral no alternative found.");
        }

        public Void visitRuntimeLiteral(@NotNull SmalltalkParser.RuntimeLiteralContext ctx) {
            log("visitRuntimeLiteral");
            SmalltalkParser.BlockContext block = ctx.block();
            if (block != null)
                return block.accept(currentVisitor());
            SmalltalkParser.DynamicDictionaryContext dynamicDictionary = ctx.dynamicDictionary();
            if (dynamicDictionary != null)
                return dynamicDictionary.accept(currentVisitor());
            SmalltalkParser.DynamicArrayContext dynamicArray = ctx.dynamicArray();
            if (dynamicArray != null)
                return dynamicArray.accept(currentVisitor());
            throw new RuntimeException("visitRuntimeLiteral no alternative found.");
        }

        public Void visitParsetimeLiteral(@NotNull SmalltalkParser.ParsetimeLiteralContext ctx) {
            log("visitParsetimeLiteral");
            SmalltalkParser.PseudoVariableContext pseudoVariable = ctx.pseudoVariable();
            if (pseudoVariable != null)
                return pseudoVariable.accept(currentVisitor());
            SmalltalkParser.NumberContext number = ctx.number();
            if (number != null)
                return number.accept(currentVisitor());
            SmalltalkParser.CharConstantContext charConstant = ctx.charConstant();
            if (charConstant != null)
                return charConstant.accept(currentVisitor());
            SmalltalkParser.LiteralArrayContext literalArray = ctx.literalArray();
            if (literalArray != null)
                return literalArray.accept(currentVisitor());
            SmalltalkParser.StringContext string = ctx.string();
            if (string != null)
                return string.accept(currentVisitor());
            SmalltalkParser.SymbolContext symbol = ctx.symbol();
            if (symbol != null)
                return symbol.accept(currentVisitor());
            throw new RuntimeException("visitParsetimeLiteral no alternative found.");
        }

        public Void visitPseudoVariable(@NotNull SmalltalkParser.PseudoVariableContext ctx) {
            log("visitPseudoVariable " + ctx.RESERVED_WORD().getSymbol().getText());
            TerminalNode pseudoVariable = ctx.RESERVED_WORD();
            String name = pseudoVariable.getSymbol().getText();
            if ("self".equals(name))
                pushReceiver(mv);
            else if ("nil".equals(name))
                pushNil(mv);
            else if ("true".equals(name))
                pushTrue(mv);
            else if ("false".equals(name))
                pushFalse(mv);
            else if ("super".equals(name))
                pushSuper(mv, pseudoVariable.getSymbol().getLine());
            else
                throw new RuntimeException("visitPseudoVariable unknown variable: " + name);
            return null;
        }

        public Void visitString(@NotNull SmalltalkParser.StringContext ctx) {
            log("visitString " + ctx.STRING().getSymbol().getText());
            TerminalNode node = ctx.STRING();
            String value = node.getSymbol().getText();
            value = value.substring(1, value.length() - 1);
            pushNewObject(mv, "smalltalkString", value, node.getSymbol().getLine());
            return null;
        }

        public Void visitSymbol(@NotNull SmalltalkParser.SymbolContext ctx) {
            log("visitSymbol #" + nodeFor(ctx).getText());
            BasicNode node = nodeFor(ctx);
            String symbol = node.getText();
            if (haveKeyword())
                addArgumentToKeyword(symbol);
            pushNewObject(mv, "smalltalkSymbol", symbol, node.getLine());
            return null;
        }

        private BasicNode nodeFor(SmalltalkParser.SymbolContext ctx) {
            SmalltalkParser.BareSymbolContext bareSymbolContext = ctx.bareSymbol();
            TerminalNode node = bareSymbolContext.IDENTIFIER();
            if (node != null)
                return new ExtendedTerminalNode(node, 0);
            node = bareSymbolContext.BINARY_SELECTOR();
            if (node != null)
                return new ExtendedTerminalNode(node, 0);
            List<TerminalNode> keywords = bareSymbolContext.KEYWORD();
            if (keywords != null && !keywords.isEmpty())
                return nodeFor(keywords);
            List<TerminalNode> pipe = bareSymbolContext.PIPE();
            if (pipe != null && !pipe.isEmpty())
                return nodeFor(pipe);
            throw new RuntimeException("Node cannot be determined from context.");
        }

        private BasicNode nodeFor(List<TerminalNode> nodes) {
            int line = nodes.get(0).getSymbol().getLine();
            StringBuilder text = new StringBuilder();
            for (TerminalNode n : nodes)
                text.append(n.getSymbol().getText());
            return new BasicNode(line, text.toString(), 0);
        }

        public Void visitReference(@NotNull SmalltalkParser.ReferenceContext ctx) {
            log("visitReference " + ctx.variable().IDENTIFIER().getSymbol().getText());
            TerminalNode identifier = ctx.variable().IDENTIFIER();
            String name = identifier.getSymbol().getText();
            visitLine(mv, identifier.getSymbol().getLine());
            if (isTemporary(name))
                pushTemporary(mv, indexOfTemporary(name));
            else if (isArgument(name))
                pushArgument(mv, indexOfArgument(name));
            else if ("JVM".equals(name))
                referencedJVM = true;
            else
                pushReference(mv, name);
            return null;
        }

        public Void visitBlock(@NotNull SmalltalkParser.BlockContext ctx) {
            log("visitBlock " + peekKeyword() + " " + blockNumber);
            KeywordRecord keywordRecord = peekKeyword();
            String name = makeBlockMethodName(keywordRecord);
            boolean methodBlock = keywordRecord.keyword.toString().endsWith("withMethod:");
            BlockGeneratorVisitor blockGeneratorVisitor = new BlockGeneratorVisitor(cw, name, blockNumber);
            pushCurrentVisitor(blockGeneratorVisitor);
            blockGeneratorVisitor.handleBlock(ctx);
            blockNumber = blockGeneratorVisitor.blockNumber;
            removeJVMGeneratorVisitor();
            popCurrentVisitor();
            int line = ctx.BLOCK_START().getSymbol().getLine();
            if (methodBlock)
                pushNewMethod(mv, fullClassName(), name, LAMBDA_BLOCK_SIG, line);
            else {
                String blockAnswerClassName = makeBlockAnswerClassName(name);
                pushNewBlock(mv, fullClassName(), name, LAMBDA_BLOCK_SIG, line, blockGeneratorVisitor.isAnswerBlock(), blockAnswerClassName);
                if (blockGeneratorVisitor.isAnswerBlock()) {
                    loadBlockAnswerClass(blockAnswerClassName);
                    tryCatchRecords.add(new BlockAnswerRecord(blockAnswerClassName));
                }
            }
            return null;
        }

        private void loadBlockAnswerClass(String blockAnswerClassName) {
            log("loadBlockAnswerClass: " + blockAnswerClassName);
            byte[] classBytes = createBlockAnswerClass(blockAnswerClassName);
            SmalltalkClassLoader classLoader = classLoader();
            classLoader.defineClass(classBytes);
        }

        private SmalltalkClassLoader classLoader() {
            return (SmalltalkClassLoader) Thread.currentThread().getContextClassLoader();
        }

        private byte[] createBlockAnswerClass(String blockAnswerClassName) {
            String className = blockAnswerClassName.replaceAll("\\.", "/");
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            MethodVisitor mv;
            cw.visit(BYTECODE_VERSION, ACC_PUBLIC + ACC_SUPER, className, null, "st/redline/core/PrimBlockAnswer", null);
            cw.visitSource(className() + sourceFileExtension(), null);
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Lst/redline/core/PrimObject;)V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, "st/redline/core/PrimBlockAnswer", "<init>", "(Lst/redline/core/PrimObject;)V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
            cw.visitEnd();
            return cw.toByteArray();
        }

        public Void visitSubexpression(@NotNull SmalltalkParser.SubexpressionContext ctx) {
            log("visitSubexpression");
            // 2 + (  (3 * 4) - 1  )
            // evaluated last (   (evaluated first)  evaluated second  )
            SmalltalkParser.ExpressionContext expression = ctx.expression();
            expression.accept(currentVisitor());
            return null;
        }

        public Void visitVariable(@NotNull SmalltalkParser.VariableContext ctx) {
            throw new RuntimeException("visitVariable should have been handed before now.");
        }

        private String makeBlockAnswerClassName(String blockName) {
            return packageName() + '.' + className() + "$A" + blockName;
        }

        private String makeBlockMethodName(KeywordRecord keywordRecord) {
            StringBuilder name = new StringBuilder();
            if (keywordRecord.firstArgument.length() == 0) {
                blockNumber++;
                name.append("B").append(blockNumber);
            } else
                name.append(keywordRecord.firstArgument.toString());
            return name.toString();
        }
    }


    private class BlockGeneratorVisitor extends ClassGeneratorVisitor {

        private final ClassWriter cw;
        private String blockName;
        private boolean returnRequired;

        public BlockGeneratorVisitor(ClassWriter cw, String name, int blockNumber) {
            super(cw);
            this.cw = cw;
            this.blockName = name;
            this.returnRequired = false;
            this.blockNumber = blockNumber;
        }

        public void handleBlock(@NotNull SmalltalkParser.BlockContext ctx) {
            log("handleBlock " + blockName + " " + blockNumber);
            openBlockLambdaMethod();
            SmalltalkParser.BlockParamListContext blockParamList = ctx.blockParamList();
            if (blockParamList != null)
                blockParamList.accept(currentVisitor());
            SmalltalkParser.SequenceContext blockSequence = ctx.sequence();
            if (blockSequence != null)
                blockSequence.accept(currentVisitor());
            returnRequired = returnRequired(blockSequence);
            closeBlockLambdaMethod(returnRequired);
        }

        public boolean isAnswerBlock() {
            return !returnRequired;
        }

        private boolean returnRequired(SmalltalkParser.SequenceContext blockSequence) {
            if (blockSequence == null)
                return true;
            SmalltalkParser.StatementsContext statements = blockSequence.statements();
            if (statements == null) {
                // We have no statements in block, so answer nil.
                pushNil(mv);
                return true;
            }
            List<ParseTree> list = statements.children;
            for (ParseTree parseTree : list) {
                if (parseTree instanceof SmalltalkParser.AnswerContext)
                    return false;
            }
            return true;
        }

        public Void visitBlockParamList(@NotNull SmalltalkParser.BlockParamListContext ctx) {
            log("visitBlockParamList");
            initializeArgumentsMap();
            int index = 0;
            int n = ctx.getChildCount();
            for(int i = 0; i < n; ++i) {
                ParseTree c = ctx.getChild(i);
                if (c instanceof TerminalNode)
                    addArgumentToMap(new ExtendedTerminalNode((TerminalNode) c, index++));
            }
            return null;
        }

        private void closeBlockLambdaMethod(boolean returnRequired) {
            log("closeBlockLambdaMethod: " + blockName + " " + returnRequired);
            if (returnRequired)
                mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void openBlockLambdaMethod() {
            log("openBlockLambdaMethod: " + blockName);
            mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC, blockName, LAMBDA_BLOCK_SIG, null, null);
            mv.visitCode();
        }
    }


    private class JVMGeneratorVisitor extends ClassGeneratorVisitor {

        private final MethodVisitor mv;
        private List<Object> arguments = new ArrayList<Object>();

        public JVMGeneratorVisitor(ClassWriter cw, MethodVisitor mv) {
            super(cw, mv);
            this.mv = mv;
        }

        public Void visitMessage(@NotNull SmalltalkParser.MessageContext ctx) {
            log("visitMessage");
            SmalltalkParser.KeywordMessageContext keywordMessage = ctx.keywordMessage();
            if (keywordMessage != null)
                return keywordMessage.accept(currentVisitor());
            throw new RuntimeException("visitMessage no alternative found.");
        }

        public Void visitKeywordMessage(@NotNull SmalltalkParser.KeywordMessageContext ctx) {
            log("visitKeywordMessage");
            initializeKeyword();
            for (SmalltalkParser.KeywordPairContext keywordPair : ctx.keywordPair())
                keywordPair.accept(currentVisitor());
            visitLine(mv, ctx.keywordPair().get(0).KEYWORD().getSymbol().getLine());
            String keyword = removeKeyword();
            JVMWriter jvmWriter = JVM_WRITERS.get(keyword);
            if (jvmWriter == null)
                throw new RuntimeException("JVM keyword not recognized.");
            jvmWriter.write(mv, arguments);
            return null;
        }

        protected void initializeKeyword() {
            super.initializeKeyword();
            arguments = new ArrayList<>();
        }

        public Void visitKeywordPair(@NotNull SmalltalkParser.KeywordPairContext ctx) {
            log("visitKeywordPair " + ctx.KEYWORD().getSymbol().getText());
            TerminalNode keyword = ctx.KEYWORD();
            String part = keyword.getSymbol().getText();
            visitLine(mv, keyword.getSymbol().getLine());
            addToKeyword(part);
            SmalltalkParser.BinarySendContext binarySend = ctx.binarySend();
            arguments.add(argumentFrom(binarySend));
            return null;
        }

        private Object argumentFrom(SmalltalkParser.BinarySendContext binarySend) {
            SmalltalkParser.UnarySendContext unarySend = binarySend.unarySend();
            if (unarySend != null) {
                SmalltalkParser.OperandContext operand = unarySend.operand();
                if (operand != null) {
                    SmalltalkParser.LiteralContext literal = operand.literal();
                    if (literal != null) {
                        SmalltalkParser.ParsetimeLiteralContext parsetimeLiteral = literal.parsetimeLiteral();
                        if (parsetimeLiteral != null) {
                            SmalltalkParser.StringContext string = parsetimeLiteral.string();
                            if (string != null) {
                                String raw = string.STRING().getSymbol().getText();
                                return raw.substring(1, raw.length() - 1);
                            }
                            SmalltalkParser.NumberContext number = parsetimeLiteral.number();
                            if (number != null) {
                                return number.getText();
                            }
                            SmalltalkParser.SymbolContext symbol = parsetimeLiteral.symbol();
//                            if (symbol != null) {
//                                SmalltalkParser.BareSymbolContext bareSymbol = symbol.bareSymbol();
//                                List<TerminalNode> keyword = bareSymbol.KEYWORD();
//                                if (keyword != null) {
//                                    System.out.println("here");
//                                }
//                            }
                            throw new RuntimeException("Unhandled JVM keyword argument.");
                        }
                    }
                }
            }
            throw new RuntimeException("JVM keyword argument expected.");
        }
    }


    private class BasicNode {

        private final int index;
        private final int line;
        private final String text;

        public BasicNode(int line, String text, int index) {
            this.line = line;
            this.text = text;
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public int getLine() {
            return line;
        }

        public String getText() {
            return text;
        }
    }

    private class ExtendedTerminalNode extends BasicNode {

        public ExtendedTerminalNode(TerminalNode node, int index) {
            super(node.getSymbol().getLine(), node.getSymbol().getText(), index);
        }
    }


    private class BlockAnswerRecord {

        public final String exceptionName;
        public Label handlerLabel;

        public BlockAnswerRecord(String name) {
            this.exceptionName = name.replaceAll("\\.", "/");
            this.handlerLabel = new Label();
        }
    }

    private class KeywordRecord {

        public StringBuilder keyword = new StringBuilder();
        public StringBuilder firstArgument = new StringBuilder();

        public String toString() {
            return keyword + " - " + firstArgument;
        }
    }


    private interface JVMWriter {

        void write(MethodVisitor mv, List<Object> arguments);
    }

    private static final Map<String, JVMWriter> JVM_WRITERS = new HashMap<String, JVMWriter>();
    static {
        JVM_WRITERS.put("aload:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitVarInsn(ALOAD, Integer.valueOf(String.valueOf(arguments.get(0))));
            }
        });
        JVM_WRITERS.put("invokeVirtual:method:matching:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitMethodInsn(INVOKEVIRTUAL, String.valueOf(arguments.get(0)), String.valueOf(arguments.get(1)), String.valueOf(arguments.get(2)), false);
            }
        });
        JVM_WRITERS.put("invokeStatic:method:matching:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitMethodInsn(INVOKESTATIC, String.valueOf(arguments.get(0)), String.valueOf(arguments.get(1)), String.valueOf(arguments.get(2)), false);
            }
        });
        JVM_WRITERS.put("getStatic:named:as:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitFieldInsn(GETSTATIC, String.valueOf(arguments.get(0)), String.valueOf(arguments.get(1)), String.valueOf(arguments.get(2)));
            }
        });
        // ## Redline Additions - these are not true JVM instructions but helpers.
        JVM_WRITERS.put("argLoad:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitVarInsn(ALOAD, 2);  // Load Context
                pushNumber(mv, Integer.valueOf(String.valueOf(arguments.get(0))));  // Load index of argument we want to get.
                mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimContext", "argumentAt", "(I)Lst/redline/core/PrimObject;", false);
            }
        });
        JVM_WRITERS.put("primitive:", new JVMWriter() {
            public void write(MethodVisitor mv, List<Object> arguments) {
                mv.visitVarInsn(ALOAD, 1);  // Load Receiver
                mv.visitVarInsn(ALOAD, 2);  // Load Context
                String methodName = "primitive" + String.valueOf(arguments.get(0));
                mv.visitMethodInsn(INVOKEVIRTUAL, "st/redline/core/PrimObject", methodName, "(Lst/redline/core/PrimContext;)Lst/redline/core/PrimObject;", false);
            }
        });
    }

    static {
        OPCODES.put("V1_1", 196653);
        OPCODES.put("V1_2", 46);
        OPCODES.put("V1_3", 47);
        OPCODES.put("V1_4", 48);
        OPCODES.put("V1_5", 49);
        OPCODES.put("V1_6", 50);
        OPCODES.put("V1_7", 51);
        OPCODES.put("V1_7", 51);
        OPCODES.put("V1_8", 52);
        OPCODES.put("ACC_PUBLIC", 1);
        OPCODES.put("ACC_PRIVATE", 2);
        OPCODES.put("ACC_PROTECTED", 4);
        OPCODES.put("ACC_STATIC", 8);
        OPCODES.put("ACC_FINAL", 16);
        OPCODES.put("ACC_SUPER", 32);
        OPCODES.put("ACC_SYNCHRONIZED", 32);
        OPCODES.put("ACC_VOLATILE", 64);
        OPCODES.put("ACC_BRIDGE", 64);
        OPCODES.put("ACC_VARARGS", 128);
        OPCODES.put("ACC_TRANSIENT", 128);
        OPCODES.put("ACC_NATIVE", 256);
        OPCODES.put("ACC_INTERFACE", 512);
        OPCODES.put("ACC_ABSTRACT", 1024);
        OPCODES.put("ACC_STRICT", 2048);
        OPCODES.put("ACC_SYNTHETIC", 4096);
        OPCODES.put("ACC_ANNOTATION", 8192);
        OPCODES.put("ACC_ENUM", 16384);
        OPCODES.put("ACC_DEPRECATED", 131072);
        OPCODES.put("T_BOOLEAN", 4);
        OPCODES.put("T_CHAR", 5);
        OPCODES.put("T_FLOAT", 6);
        OPCODES.put("T_DOUBLE", 7);
        OPCODES.put("T_BYTE", 8);
        OPCODES.put("T_SHORT", 9);
        OPCODES.put("T_INT", 10);
        OPCODES.put("T_LONG", 11);
        OPCODES.put("F_NEW", -1);
        OPCODES.put("F_FULL", 0);
        OPCODES.put("F_APPEND", 1);
        OPCODES.put("F_CHOP", 2);
        OPCODES.put("F_SAME", 3);
        OPCODES.put("F_SAME1", 4);
        OPCODES.put("TOP", TOP);
        OPCODES.put("INTEGER", INTEGER);
        OPCODES.put("FLOAT", FLOAT);
        OPCODES.put("DOUBLE", DOUBLE);
        OPCODES.put("LONG", LONG);
        OPCODES.put("NULL", NULL);
        OPCODES.put("UNINITIALIZED_THIS", UNINITIALIZED_THIS);
        OPCODES.put("NOP", 0);
        OPCODES.put("ACONST_NULL", 1);
        OPCODES.put("ICONST_M1", 2);
        OPCODES.put("ICONST_0", 3);
        OPCODES.put("ICONST_1", 4);
        OPCODES.put("ICONST_2", 5);
        OPCODES.put("ICONST_3", 6);
        OPCODES.put("ICONST_4", 7);
        OPCODES.put("ICONST_5", 8);
        OPCODES.put("LCONST_0", 9);
        OPCODES.put("LCONST_1", 10);
        OPCODES.put("FCONST_0", 11);
        OPCODES.put("FCONST_1", 12);
        OPCODES.put("FCONST_2", 13);
        OPCODES.put("DCONST_0", 14);
        OPCODES.put("DCONST_1", 15);
        OPCODES.put("BIPUSH", 16);
        OPCODES.put("SIPUSH", 17);
        OPCODES.put("LDC", 18);
        OPCODES.put("ILOAD", 21);
        OPCODES.put("LLOAD", 22);
        OPCODES.put("FLOAD", 23);
        OPCODES.put("DLOAD", 24);
        OPCODES.put("ALOAD", 25);
        OPCODES.put("IALOAD", 46);
        OPCODES.put("LALOAD", 47);
        OPCODES.put("FALOAD", 48);
        OPCODES.put("DALOAD", 49);
        OPCODES.put("AALOAD", 50);
        OPCODES.put("BALOAD", 51);
        OPCODES.put("CALOAD", 52);
        OPCODES.put("SALOAD", 53);
        OPCODES.put("ISTORE", 54);
        OPCODES.put("LSTORE", 55);
        OPCODES.put("FSTORE", 56);
        OPCODES.put("DSTORE", 57);
        OPCODES.put("ASTORE", 58);
        OPCODES.put("IASTORE", 79);
        OPCODES.put("LASTORE", 80);
        OPCODES.put("FASTORE", 81);
        OPCODES.put("DASTORE", 82);
        OPCODES.put("AASTORE", 83);
        OPCODES.put("BASTORE", 84);
        OPCODES.put("CASTORE", 85);
        OPCODES.put("SASTORE", 86);
        OPCODES.put("POP", 87);
        OPCODES.put("POP2", 88);
        OPCODES.put("DUP", 89);
        OPCODES.put("DUP_X1", 90);
        OPCODES.put("DUP_X2", 91);
        OPCODES.put("DUP2", 92);
        OPCODES.put("DUP2_X1", 93);
        OPCODES.put("DUP2_X2", 94);
        OPCODES.put("SWAP", 95);
        OPCODES.put("IADD", 96);
        OPCODES.put("LADD", 97);
        OPCODES.put("FADD", 98);
        OPCODES.put("DADD", 99);
        OPCODES.put("ISUB", 100);
        OPCODES.put("LSUB", 101);
        OPCODES.put("FSUB", 102);
        OPCODES.put("DSUB", 103);
        OPCODES.put("IMUL", 104);
        OPCODES.put("LMUL", 105);
        OPCODES.put("FMUL", 106);
        OPCODES.put("DMUL", 107);
        OPCODES.put("IDIV", 108);
        OPCODES.put("LDIV", 109);
        OPCODES.put("FDIV", 110);
        OPCODES.put("DDIV", 111);
        OPCODES.put("IREM", 112);
        OPCODES.put("LREM", 113);
        OPCODES.put("FREM", 114);
        OPCODES.put("DREM", 115);
        OPCODES.put("INEG", 116);
        OPCODES.put("LNEG", 117);
        OPCODES.put("FNEG", 118);
        OPCODES.put("DNEG", 119);
        OPCODES.put("ISHL", 120);
        OPCODES.put("LSHL", 121);
        OPCODES.put("ISHR", 122);
        OPCODES.put("LSHR", 123);
        OPCODES.put("IUSHR", 124);
        OPCODES.put("LUSHR", 125);
        OPCODES.put("IAND", 126);
        OPCODES.put("LAND", 127);
        OPCODES.put("IOR", 128);
        OPCODES.put("LOR", 129);
        OPCODES.put("IXOR", 130);
        OPCODES.put("LXOR", 131);
        OPCODES.put("IINC", 132);
        OPCODES.put("I2L", 133);
        OPCODES.put("I2F", 134);
        OPCODES.put("I2D", 135);
        OPCODES.put("L2I", 136);
        OPCODES.put("L2F", 137);
        OPCODES.put("L2D", 138);
        OPCODES.put("F2I", 139);
        OPCODES.put("F2L", 140);
        OPCODES.put("F2D", 141);
        OPCODES.put("D2I", 142);
        OPCODES.put("D2L", 143);
        OPCODES.put("D2F", 144);
        OPCODES.put("I2B", 145);
        OPCODES.put("I2C", 146);
        OPCODES.put("I2S", 147);
        OPCODES.put("LCMP", 148);
        OPCODES.put("FCMPL", 149);
        OPCODES.put("FCMPG", 150);
        OPCODES.put("DCMPL", 151);
        OPCODES.put("DCMPG", 152);
        OPCODES.put("IFEQ", 153);
        OPCODES.put("IFNE", 154);
        OPCODES.put("IFLT", 155);
        OPCODES.put("IFGE", 156);
        OPCODES.put("IFGT", 157);
        OPCODES.put("IFLE", 158);
        OPCODES.put("IF_ICMPEQ", 159);
        OPCODES.put("IF_ICMPNE", 160);
        OPCODES.put("IF_ICMPLT", 161);
        OPCODES.put("IF_ICMPGE", 162);
        OPCODES.put("IF_ICMPGT", 163);
        OPCODES.put("IF_ICMPLE", 164);
        OPCODES.put("IF_ACMPEQ", 165);
        OPCODES.put("IF_ACMPNE", 166);
        OPCODES.put("GOTO", 167);
        OPCODES.put("JSR", 168);
        OPCODES.put("RET", 169);
        OPCODES.put("TABLESWITCH", 170);
        OPCODES.put("LOOKUPSWITCH", 171);
        OPCODES.put("IRETURN", 172);
        OPCODES.put("LRETURN", 173);
        OPCODES.put("FRETURN", 174);
        OPCODES.put("DRETURN", 175);
        OPCODES.put("ARETURN", 176);
        OPCODES.put("RETURN", 177);
        OPCODES.put("GETSTATIC", 178);
        OPCODES.put("PUTSTATIC", 179);
        OPCODES.put("GETFIELD", 180);
        OPCODES.put("PUTFIELD", 181);
        OPCODES.put("INVOKEVIRTUAL", 182);
        OPCODES.put("INVOKESPECIAL", 183);
        OPCODES.put("INVOKESTATIC", 184);
        OPCODES.put("INVOKEINTERFACE", 185);
        OPCODES.put("INVOKEDYNAMIC", 186);
        OPCODES.put("NEW", 187);
        OPCODES.put("NEWARRAY", 188);
        OPCODES.put("ANEWARRAY", 189);
        OPCODES.put("ARRAYLENGTH", 190);
        OPCODES.put("ATHROW", 191);
        OPCODES.put("CHECKCAST", 192);
        OPCODES.put("INSTANCEOF", 193);
        OPCODES.put("MONITORENTER", 194);
        OPCODES.put("MONITOREXIT", 195);
        OPCODES.put("MULTIANEWARRAY", 197);
        OPCODES.put("IFNULL", 198);
        OPCODES.put("IFNONNULL", 199);
    }
}
