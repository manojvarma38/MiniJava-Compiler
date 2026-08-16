package visitor;

import syntaxtree.*;

public class LambdaPass extends GJDepthFirst<Void, Void> {

    private IRCodeEmitter irEmitter;

    public LambdaPass(IRCodeEmitter irEmitter) {
        this.irEmitter = irEmitter;
    }

    // second pass entry point
    public void emit() {
        irEmitter.emitLambdaFunctions();
    }
}
